#!/usr/bin/env bash
# Dump uniforms (JSON), shader sources and LUTs for the current code: dump.sh <tag> <sizes WxH,WxH,...> [flash 0|1|both] [profiles]
# Output: tools/render/out/<tag>/. Extra RENDER_* env vars (see RenderDumpTool.kt) are passed through.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TAG="${1:?tag}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export RENDER_DUMP_DIR="$ROOT/tools/render/out/$TAG"
export RENDER_SIZES="${2:?sizes}"
export RENDER_FLASH="${3:-both}"
export RENDER_PROFILES="${4:-all}"
export RENDER_SAMPLES_DIR="$ROOT/tools/render/samples"   # film flash: set RENDER_RELIGHT_IMAGES=astronaut_dim,chelsea_dim,... to dump relight uniforms + illum maps
cd "$ROOT"
./gradlew --offline :profiles:testDebugUnitTest --tests '*RenderDumpTool*' --rerun -q 2>&1 | grep -v "^Picked up" || true
ls "$RENDER_DUMP_DIR" | head -3 >/dev/null
