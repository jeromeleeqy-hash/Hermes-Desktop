#!/bin/bash
set -euo pipefail
cd -- "$(dirname -- "$0")"

# Keep this entry self-contained for both Preview 1 and Beta 1 projects.
echo "工程目录：$PWD"
hermes_missing=0
hermes_require_file() {
  if [[ ! -f "$1" ]]; then
    echo "缺少工程文件：$1"
    hermes_missing=1
  fi
}
for hermes_required in gradlew build.gradle.kts packaging/common.sh src/main/resources/icon.png; do
  hermes_require_file "$hermes_required"
done

# Preview 1 has no native speech files. A partial native component in Beta 1
# is an incomplete project and must still report its missing files.
hermes_native_count=0
for hermes_native_file in packaging/prepare-native.sh packaging/NativeSpeech.swift packaging/SpeechInfo.plist; do
  if [[ -f "$hermes_native_file" ]]; then
    hermes_native_count=$((hermes_native_count + 1))
  fi
done
if [[ "$hermes_native_count" -gt 0 ]]; then
  for hermes_native_file in packaging/prepare-native.sh packaging/NativeSpeech.swift packaging/SpeechInfo.plist; do
    hermes_require_file "$hermes_native_file"
  done
fi
if [[ "$hermes_missing" -ne 0 ]]; then
  echo "请检查上面列出的文件。若该目录已是 Hermes 工程，请重新解压完整源码包到新目录。"
  echo "若这里只有补丁文件，请把本脚本复制到含 build.gradle.kts 和 src 的工程目录。"
  exit 1
fi

hermes_version_file="src/main/kotlin/com/qingyu/hermescompanion/R.kt"
if [[ -f "$hermes_version_file" ]]; then
  hermes_source_version="$(sed -nE 's/.*VERSION_NAME[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$hermes_version_file")"
  if [[ -n "$hermes_source_version" ]]; then
    echo "源码版本：$hermes_source_version"
    case "$hermes_source_version" in
      *-preview.*) echo "当前是预览版。打包只会生成该版本；使用完整功能请解压 Beta 版的完整源码包。" ;;
    esac
  fi
fi

source packaging/common.sh
echo "此入口编译应用并生成 DMG，不编译或运行自动测试。"
echo "需要完整测试与打包时，请使用 Build-macOS.command。"

echo "1/3 准备系统语音组件"
if [[ "$hermes_native_count" -gt 0 ]]; then
  bash packaging/prepare-native.sh
else
  echo "当前工程没有系统语音辅助组件，继续按现有源码打包。"
fi

echo "2/3 准备应用图标"
mkdir -p packaging/Hermes.iconset
for size in 16 32 128 256 512; do
  /usr/bin/sips -z "$size" "$size" src/main/resources/icon.png --out "packaging/Hermes.iconset/icon_${size}x${size}.png" >/dev/null
  double=$((size * 2))
  /usr/bin/sips -z "$double" "$double" src/main/resources/icon.png --out "packaging/Hermes.iconset/icon_${size}x${size}@2x.png" >/dev/null
done
/usr/bin/iconutil -c icns packaging/Hermes.iconset -o packaging/Hermes.icns

echo "3/3 编译应用并生成 macOS 安装文件"
mkdir -p build/logs
hermes_package_log="build/logs/package-macos-$(date +%Y%m%d-%H%M%S).log"
if ./gradlew packageDmg --console=plain 2>&1 | tee "$hermes_package_log"; then
  echo "打包完成：build/compose/binaries/main/dmg"
  echo "本次未运行自动测试。"
  /usr/bin/open build/compose/binaries/main/dmg
else
  echo "打包失败，日志已保存在：$PWD/$hermes_package_log"
  exit 1
fi
