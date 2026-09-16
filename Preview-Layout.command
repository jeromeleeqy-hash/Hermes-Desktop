#!/bin/bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
source packaging/common.sh
./gradlew run --args="--demo" --console=plain
