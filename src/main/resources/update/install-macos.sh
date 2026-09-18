#!/bin/bash
set -eu
# All paths are positional arguments; release metadata is never executed as code.
pid="$1"; package="$2"; app="$3"; expected="$4"; expected_hash="$5"; result="$6"; ready="$7"; armed="$8"
base="$(dirname "$app")"
mount="$(mktemp -d)"
stage="$(mktemp -d "$base/.Hermes-update.XXXXXX")"
backup="$stage/previous.app"
new="$stage/Hermes.app"
moved=0
finish() {
    status=$?
    trap - EXIT
    /usr/bin/hdiutil detach "$mount" -quiet >/dev/null 2>&1 || true
    if [ "$status" -ne 0 ]; then
        if [ "$moved" = 1 ] && [ -d "$backup" ]; then
            [ ! -e "$app" ] || mv "$app" "$stage/failed.app"
            mv "$backup" "$app"
            /usr/bin/open "$app" || true
        fi
        printf '更新失败，旧版已保留。请在帮助页面重试或手动安装。\n' > "$result"
    fi
    # Retain backup/diagnostics on failure, remove only successful staging.
    if [ "$status" = 0 ]; then rm -rf "$stage"; fi
    rmdir "$mount" >/dev/null 2>&1 || true
    exit "$status"
}
trap finish EXIT
[ -w "$base" ] && [ -w "$app" ]
[ "$(/usr/bin/shasum -a 256 "$package" | /usr/bin/awk '{print $1}')" = "$expected_hash" ]
/usr/bin/hdiutil attach "$package" -readonly -nobrowse -mountpoint "$mount" -quiet
[ -d "$mount/Hermes.app" ]
[ "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$mount/Hermes.app/Contents/Info.plist")" = 'com.qingyu.hermes.desktop' ]
[ "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$mount/Hermes.app/Contents/Info.plist")" = "$expected" ]
/usr/bin/codesign --verify --deep --strict "$mount/Hermes.app"
/usr/bin/ditto "$mount/Hermes.app" "$new"
/usr/bin/codesign --verify --deep --strict "$new"
/usr/bin/hdiutil detach "$mount" -quiet
touch "$ready"
# Never replace a still-running application; timeout preserves old installation.
for i in $(seq 1 120); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
done
if kill -0 "$pid" 2>/dev/null; then exit 1; fi
[ -f "$armed" ]
mv "$app" "$backup"; moved=1
mv "$new" "$app"
/usr/bin/open "$app"
printf '已更新到 %s\n' "$expected" > "$result"
