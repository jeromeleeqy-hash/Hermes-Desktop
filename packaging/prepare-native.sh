#!/bin/bash
set -euo pipefail
if [[ "$(uname -s)" != "Darwin" ]]; then exit 0; fi
if ! /usr/bin/xcrun --find swiftc >/dev/null 2>&1; then
  echo "已使用服务器语音模式。安装 Apple Command Line Tools 后可额外启用 macOS 系统识别。"
  exit 0
fi
mkdir -p packaging/native packaging/app-resources/macos
if [[ ! -x packaging/native/HermesSpeech || packaging/NativeSpeech.swift -nt packaging/native/HermesSpeech || packaging/SpeechInfo.plist -nt packaging/native/HermesSpeech ]]; then
  /usr/bin/xcrun swiftc -swift-version 5 -parse-as-library -O -target arm64-apple-macos13.0 packaging/NativeSpeech.swift \
    -o packaging/native/HermesSpeech -framework Speech -framework AppKit \
    -Xlinker -sectcreate -Xlinker __TEXT -Xlinker __info_plist -Xlinker packaging/SpeechInfo.plist
  /usr/bin/codesign --force --sign - --identifier com.qingyu.hermes.desktop.speech packaging/native/HermesSpeech
fi
cp packaging/native/HermesSpeech packaging/app-resources/macos/HermesSpeech
