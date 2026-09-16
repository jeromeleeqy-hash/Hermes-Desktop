#!/bin/bash
set -Eeuo pipefail
cd -- "$(dirname -- "$0")"
if [[ "$(uname -s)" != Darwin ]]; then
  echo "请在 M 系列芯片的 Mac 上运行此文件。"
  exit 1
fi
if [[ "$(/usr/sbin/sysctl -n hw.optional.arm64 2>/dev/null || true)" != 1 ]]; then
  echo "此包适用于 Apple Silicon（M 系列芯片），不适用于 Intel Mac。"
  exit 1
fi
mkdir -p build/logs output
hermes_log="$PWD/build/logs/build-$(date +%Y%m%d-%H%M%S).log"
exec > >(/usr/bin/tee -a "$hermes_log") 2>&1
hermes_work=""
hermes_cleanup() {
  if [[ -n "$hermes_work" && -d "$hermes_work" ]]; then /bin/rm -rf -- "$hermes_work"; fi
}
hermes_failed() {
  local code=$?
  echo "生成未完成，日志：$hermes_log"
  echo "把这份日志发回来即可继续排查。"
  exit "$code"
}
trap hermes_failed ERR
trap hermes_cleanup EXIT

echo "Hermes macOS 1.8.4 · Apple Silicon"
echo "1/6 校验完整文件"
/usr/bin/shasum -a 256 -c PAYLOAD-SHA256.txt > "$PWD/build/logs/payload-check.log"
hermes_work="$(/usr/bin/mktemp -d "$PWD/build/session.XXXXXX")"
mkdir -p "$hermes_work/toolchain"

echo "2/6 准备随包提供的 Java 环境"
/usr/bin/tar -xzf toolchain/jdk-macos-arm64.tar.gz -C "$hermes_work/toolchain"
hermes_jdk="$hermes_work/toolchain/jdk-21.0.12.1+1/Contents/Home"
[[ -x "$hermes_jdk/bin/jpackage" && -d "$hermes_jdk/jmods" ]]
# The official archive was checked above. Only this temporary copy is changed.
/usr/bin/xattr -dr com.apple.quarantine "$hermes_work/toolchain" 2>/dev/null || true
"$hermes_jdk/bin/java" -XshowSettings:properties -version > "$hermes_work/java-info.txt" 2>&1
/usr/bin/grep -Eq 'os.arch = aarch64' "$hermes_work/java-info.txt"
"$hermes_jdk/bin/jlink" --module-path "$hermes_jdk/jmods" --add-modules ALL-MODULE-PATH --bind-services --strip-debug --no-header-files --no-man-pages --compress=2 --output "$hermes_work/runtime"

echo "3/6 生成 Hermes.app"
"$hermes_jdk/bin/jpackage" --type app-image --name Hermes --app-version 1.8.4 \
  --vendor Jerome --description "Hermes desktop companion" \
  --dest "$hermes_work/apps" --input "$PWD/app" \
  --main-jar hermes-desktop.jar --main-class com.qingyu.hermescompanion.desktop.MainKt \
  --runtime-image "$hermes_work/runtime" --icon "$PWD/packaging/Hermes.icns" \
  --mac-package-identifier com.qingyu.hermes.desktop --mac-package-name Hermes \
  --mac-app-category public.app-category.productivity \
  --java-options '-Dfile.encoding=UTF-8' --java-options '-Xmx1536m' \
  --java-options '-Dapple.laf.useScreenMenuBar=true' \
  --java-options '-Dapple.awt.enableTemplateImages=true' \
  --java-options '-Dcompose.application.resources.dir=$APPDIR/resources'
hermes_app="$hermes_work/apps/Hermes.app"
hermes_plist="$hermes_app/Contents/Info.plist"
hermes_set_string() {
  /usr/libexec/PlistBuddy -c "Set :$1 $2" "$hermes_plist" 2>/dev/null || /usr/libexec/PlistBuddy -c "Add :$1 string $2" "$hermes_plist"
}
hermes_set_string LSMinimumSystemVersion 13.0
hermes_set_string HermesBuildRevision desktop-ui-3
hermes_set_string NSMicrophoneUsageDescription 'Hermes 使用麦克风进行语音输入和对话。'
hermes_set_string NSSpeechRecognitionUsageDescription 'Hermes 将录音识别为对话文字。'
/usr/bin/plutil -lint "$hermes_plist"

# Apple command-line tools are optional; server transcription works without them.
if /usr/bin/xcrun --find swiftc >/dev/null 2>&1; then
  mkdir -p "$hermes_app/Contents/app/resources"
  if /usr/bin/xcrun swiftc -swift-version 5 -parse-as-library -O \
      -target arm64-apple-macos13.0 "$PWD/packaging/NativeSpeech.swift" \
      -o "$hermes_app/Contents/app/resources/HermesSpeech" -framework Speech -framework AppKit \
      -Xlinker -sectcreate -Xlinker __TEXT -Xlinker __info_plist -Xlinker "$PWD/packaging/SpeechInfo.plist"; then
    /usr/bin/codesign --force --sign - --identifier com.qingyu.hermes.desktop.speech "$hermes_app/Contents/app/resources/HermesSpeech"
  else
    /bin/rm -f "$hermes_app/Contents/app/resources/HermesSpeech"
    echo "系统识别组件未生成，语音输入仍可使用服务器识别。"
  fi
fi

echo "4/6 本机签名与应用校验"
/usr/bin/xattr -dr com.apple.quarantine "$hermes_app" 2>/dev/null || true
/usr/bin/codesign --force --deep --sign - "$hermes_app"
/usr/bin/codesign --verify --deep --strict --verbose=2 "$hermes_app"
[[ "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$hermes_plist")" == com.qingyu.hermes.desktop ]]
[[ "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$hermes_plist")" == 1.8.4 ]]
/usr/bin/file "$hermes_app/Contents/MacOS/Hermes" | /usr/bin/grep -q arm64
/usr/bin/cmp -s app/hermes-desktop.jar "$hermes_app/Contents/app/hermes-desktop.jar"
hermes_runtime="$hermes_app/Contents/runtime/Contents/Home"
if [[ ! -x "$hermes_runtime/bin/java" ]]; then hermes_runtime="$hermes_app/Contents/runtime"; fi
"$hermes_runtime/bin/java" --list-modules > "$hermes_work/modules.txt"
/usr/bin/grep -q '^jdk.unsupported.desktop@' "$hermes_work/modules.txt"
/usr/bin/grep -q '^java.desktop@' "$hermes_work/modules.txt"

echo "5/6 生成 DMG"
hermes_result="$PWD/output/Hermes-1.8.4-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$hermes_result" "$hermes_work/dmg"
/usr/bin/ditto "$hermes_app" "$hermes_result/Hermes.app"
/usr/bin/ditto "$hermes_app" "$hermes_work/dmg/Hermes.app"
/bin/ln -s /Applications "$hermes_work/dmg/Applications"
/usr/bin/hdiutil create -volname 'Hermes 1.8.4' -srcfolder "$hermes_work/dmg" -format UDZO "$hermes_result/Hermes-macOS-arm64-1.8.4.dmg"
/usr/bin/hdiutil verify "$hermes_result/Hermes-macOS-arm64-1.8.4.dmg"
/usr/bin/shasum -a 256 "$hermes_result/Hermes-macOS-arm64-1.8.4.dmg" > "$hermes_result/SHA256.txt"
/bin/cp "$hermes_work/modules.txt" "$hermes_result/runtime-modules.txt"

echo "6/6 完成"
echo "应用和安装镜像已生成：$hermes_result"
echo "打开 DMG，把 Hermes 拖入 Applications 即可。"
echo "本次仅在本机签名；没有进行 Apple 开发者签名或公证。"
if [[ "${CI:-}" != true ]]; then /usr/bin/open "$hermes_result"; fi
