#!/usr/bin/env bash
# 在 Windows Git Bash 下安装 JDK17 + Android SDK + Gradle，供本地编译 APK。
# 带重试与断点续传，适配走代理的网络环境。
ROOT="/c/Users/admin/WorkBuddy/2026-09-02-14-54-14/KidsGuard"
ENV="$ROOT/.build_env"
TOOLS="$ENV/tools"
mkdir -p "$ENV" "$TOOLS"

# 带重试的下载（最多 4 次，-C - 续传）
dl() {
  local url="$1" out="$2" n=0
  until [ $n -ge 4 ]; do
    n=$((n+1))
    echo "    [try $n/4] curl $url"
    if curl -sL -C - --retry 3 --retry-delay 3 --max-time 600 -o "$out" "$url"; then
      local sz; sz=$(stat -c%s "$out" 2>/dev/null || echo 0)
      if [ "$sz" -gt 100000 ]; then echo "    ok size=$sz"; return 0; fi
    fi
    echo "    ... 失败，3 秒后重试"
    sleep 3
  done
  echo "!!! 下载失败: $url"; return 1
}

echo "[1/6] 下载 JDK17 (Azul Zulu，规避 GitHub 二进制 CDN 被代理拦截) ..."
dl "https://cdn.azul.com/zulu/bin/zulu17.68.203-ca-jdk17.0.20.1-win_x64.zip" "$TOOLS/jdk.zip" || exit 1
rm -rf "$ENV/jdk"; mkdir -p "$ENV/jdk"
unzip -q -o "$TOOLS/jdk.zip" -d "$ENV/jdk"
JH=$(find "$ENV/jdk" -maxdepth 3 -name java -path '*/bin/java' | head -1)
JH=$(dirname "$(dirname "$JH")")
echo "    JAVA_HOME=$JH"

echo "[2/6] 下载 Gradle 8.2 ..."
dl "https://services.gradle.org/distributions/gradle-8.2-bin.zip" "$TOOLS/gradle.zip" || exit 1
rm -rf "$ENV/gradle"; mkdir -p "$ENV/gradle"
unzip -q -o "$TOOLS/gradle.zip" -d "$ENV/gradle"
GRADLE_BIN=$(find "$ENV/gradle" -maxdepth 2 -name gradle -path '*/bin/gradle' | head -1)
echo "    gradle=$GRADLE_BIN"

echo "[3/6] 下载 Android cmdline-tools ..."
dl "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" "$TOOLS/cmdline-tools.zip" || exit 1
SDK_DIR="$ENV/sdk"
rm -rf "$SDK_DIR/cmdline-tools"; mkdir -p "$SDK_DIR/cmdline-tools"
unzip -q -o "$TOOLS/cmdline-tools.zip" -d "$SDK_DIR/cmdline-tools"
if [ -d "$SDK_DIR/cmdline-tools/cmdline-tools" ]; then
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
fi
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
echo "    sdkmanager=$SDKMANAGER"

echo "[4/6] 接受许可并安装 SDK 组件 ..."
export JAVA_HOME="$JH"
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" --sdk_root="$SDK_DIR" "platform-tools" "build-tools;34.0.0" "platforms;android-34"

echo "[5/6] 写入环境脚本 ..."
cat > "$ENV/env.sh" <<EOF
export JAVA_HOME="$JH"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="\$JAVA_HOME/bin:\$SDK_DIR/platform-tools:\$PATH"
export GRADLE="$GRADLE_BIN"
EOF

echo "[6/6] 完成。"
ls -la "$SDK_DIR"
echo "JAVA_HOME=$JH"
"$JH/bin/java" -version
