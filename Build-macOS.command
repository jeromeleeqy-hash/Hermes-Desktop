#!/bin/bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
source packaging/common.sh
bash packaging/prepare-native.sh
echo "1/3 准备应用图标"
mkdir -p packaging/Hermes.iconset
for size in 16 32 128 256 512; do
  /usr/bin/sips -z "$size" "$size" src/main/resources/icon.png --out "packaging/Hermes.iconset/icon_${size}x${size}.png" >/dev/null
  double=$((size * 2))
  /usr/bin/sips -z "$double" "$double" src/main/resources/icon.png --out "packaging/Hermes.iconset/icon_${size}x${size}@2x.png" >/dev/null
done
/usr/bin/iconutil -c icns packaging/Hermes.iconset -o packaging/Hermes.icns
echo "2/3 编译并运行协议回归测试"
./gradlew test --console=plain
echo "3/3 打包 macOS 安装文件"
./gradlew packageDmg --console=plain
echo "完成：build/compose/binaries/main/dmg"
/usr/bin/open build/compose/binaries/main/dmg
