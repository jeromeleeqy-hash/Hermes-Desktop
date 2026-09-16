#!/bin/bash
set -euo pipefail
if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "这个脚本用于 macOS。其他系统可使用 ./gradlew run 测试桌面代码。"
  exit 1
fi
hermes_java=""
hermes_detected="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
for hermes_candidate in "${HERMES_JAVA_HOME:-}" "${JAVA_HOME:-}" "$hermes_detected" \
  /Library/Java/JavaVirtualMachines/*/Contents/Home \
  "$HOME"/Library/Java/JavaVirtualMachines/*/Contents/Home \
  /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
  if [[ -n "$hermes_candidate" && -x "$hermes_candidate/bin/javac" ]]; then
    hermes_version="$("$hermes_candidate/bin/javac" -version 2>&1 || true)"
    case "$hermes_version" in
      "javac 21"|"javac 21."*|"javac 21-"*) hermes_java="$hermes_candidate"; break ;;
    esac
  fi
done
if [[ -z "$hermes_java" ]]; then
  echo "没有找到 JDK 21。已经手动安装 OpenJDK 的用户不需要安装 Homebrew。"
  echo "请确认安装的是 JDK 21（包含 javac），安装包选择与你的 Mac 芯片一致的 .pkg。"
  echo "下载：https://adoptium.net/temurin/releases/?os=mac&version=21"
  echo "已安装版本："
  /usr/libexec/java_home -V 2>&1 || true
  echo "解压安装的 JDK 可以通过 HERMES_JAVA_HOME 指向 Contents/Home。"
  exit 1
fi
hermes_arch="$("$hermes_java/bin/java" -XshowSettings:properties -version 2>&1 | /usr/bin/awk '/os.arch =/ {print $3}')"
if [[ "$hermes_arch" != aarch64 ]]; then
  echo "这份源码包面向 M 系列芯片，请使用 Apple Silicon / aarch64 版 JDK 21。"
  exit 1
fi
export JAVA_HOME="$hermes_java"
export PATH="$JAVA_HOME/bin:$PATH"
chmod +x ./gradlew
echo "使用 JDK：$JAVA_HOME"
