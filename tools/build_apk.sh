#!/usr/bin/env bash
# tools/build_apk.sh  —— 用本地工具链编译 debug APK
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BE="$ROOT/.build_env"
if [ ! -f "$BE/env.sh" ]; then
  echo "!! 未找到 $BE/env.sh，请先运行 bash tools/fetch_toolchain.sh"; exit 1
fi
source "$BE/env.sh"

# 让 Gradle 走沙箱代理拉取 Maven 依赖
PROXY_HOST="127.0.0.1"; PROXY_PORT="65028"
export JAVA_OPTS="-Dhttp.proxyHost=$PROXY_HOST -Dhttp.proxyPort=$PROXY_PORT -Dhttps.proxyHost=$PROXY_HOST -Dhttps.proxyPort=$PROXY_PORT"
GRADLE_PROPS="$ROOT/gradle.properties"
if [ -f "$GRADLE_PROPS" ] && ! grep -q "systemProp.http.proxyHost" "$GRADLE_PROPS"; then
  cat >> "$GRADLE_PROPS" <<EOF

systemProp.http.proxyHost=$PROXY_HOST
systemProp.http.proxyPort=$PROXY_PORT
systemProp.https.proxyHost=$PROXY_HOST
systemProp.https.proxyPort=$PROXY_PORT
EOF
fi

cd "$ROOT"
echo "==== 开始 assembleDebug ===="
"$GRADLE" assembleDebug --no-daemon --stacktrace 2>&1 | tee "$BE/build.log"
echo "==== assembleDebug 结束 exit=${PIPESTATUS[0]} ===="
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo "APK_OK: $APK ($(stat -c%s "$APK") 字节)"
else
  echo "APK_MISSING: 编译未产出 apk，请查看 $BE/build.log"
fi
