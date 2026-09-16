#!/bin/bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
source packaging/common.sh
bash packaging/prepare-native.sh
./gradlew run --console=plain
