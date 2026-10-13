#!/usr/bin/env bash
# 本地与 CI 统一的验证入口（C-001/C-002 等命令的聚合门禁）
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> 单元测试"
./gradlew test

echo "==> Lint"
./gradlew lint

echo "==> 组装 debug APK"
./gradlew :app:assembleDebug

echo "==> 全部通过"
