#!/usr/bin/env bash
# tools/fetch_toolchain.sh
# 通过「分段 range 下载 + 校验 + 拼回」绕过代理对单文件大下载的掐断，
# 在本地组装 Android 编译工具链：JDK17 + Gradle8.2 + Android SDK(platform-34/build-tools/34.0.0/platform-tools)。
# 用法: bash tools/fetch_toolchain.sh
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BE="$ROOT/.build_env"
TOOLS="$BE/tools"
mkdir -p "$TOOLS"
LOG="$BE/fetch.log"
: > "$LOG"

PY="C:/Users/admin/.workbuddy/binaries/python/versions/3.13.12/python.exe"
CHUNK=$((5*1024*1024))   # 5MB/片，留余量避开代理可能的 6MB 封顶

log(){ echo "$*" | tee -a "$LOG"; }

# ---- 分段下载单个文件 URL -> OUTFILE ----
dl_chunked(){
  local URL="$1" OUTFILE="$2"
  local PARTS="$OUTFILE.parts"
  rm -rf "$PARTS"; mkdir -p "$PARTS"
  # 探测总大小：发 0-0 range，解析 Content-Range: bytes 0-0/TOTAL
  local HDR TOTAL
  HDR=$(curl -s -r 0-0 -D - -o /dev/null --max-time 60 "$URL" 2>/dev/null)
  TOTAL=$(printf '%s\n' "$HDR" | tr -d '\r' | grep -i '^Content-Range:' | sed -E 's/.*\/([0-9]+).*/\1/' | head -1)
  if [ -z "$TOTAL" ] || [ "$TOTAL" = "0" ]; then
    log "!! 无法探测大小(可能被代理拦截): $URL"; return 1
  fi
  log "[fetch] $(basename "$OUTFILE")  总大小=$TOTAL 字节 (约 $((TOTAL/1024/1024))MB)"
  local START=0 IDX=0
  while [ "$START" -lt "$TOTAL" ]; do
    local END=$((START+CHUNK-1))
    [ "$END" -ge "$TOTAL" ] && END=$((TOTAL-1))
    local PART="$PARTS/part_$IDX"
    local WANT=$((END-START+1)) GOT=0 OK=0 TRIES=0
    while [ "$TRIES" -lt 10 ]; do
      TRIES=$((TRIES+1))
      if [ -f "$PART" ]; then GOT=$(stat -c%s "$PART" 2>/dev/null || echo 0); [ "$GOT" -eq "$WANT" ] && { OK=1; break; }; fi
      rm -f "$PART"
      curl -s -r ${START}-${END} -o "$PART" --max-time 150 "$URL" 2>/dev/null
      GOT=$(stat -c%s "$PART" 2>/dev/null || echo 0)
      if [ "$GOT" -eq "$WANT" ]; then OK=1; break; fi
      log "  分片$IDX 重试$TRIES got=$GOT want=$WANT"
      sleep 1
    done
    if [ "$OK" -ne 1 ]; then log "!! 分片$IDX 下载失败，中止"; return 1; fi
    START=$((END+1)); IDX=$((IDX+1))
    log "  分片$IDX 完成 ($(basename "$OUTFILE"))"
  done
  cat "$PARTS"/part_* > "$OUTFILE"
  local FINAL; FINAL=$(stat -c%s "$OUTFILE" 2>/dev/null || echo 0)
  if [ "$FINAL" -ne "$TOTAL" ]; then log "!! 拼回大小不符 $FINAL != $TOTAL"; return 1; fi
  rm -rf "$PARTS"
  log "[ok] $OUTFILE ($FINAL 字节)"
}

# ---- 解压 zip -> DEST ----
extract_zip(){
  local ZIP="$1" DEST="$2"
  mkdir -p "$DEST"
  "$PY" - "$ZIP" "$DEST" <<'PY'
import sys, zipfile, os
z, d = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(z) as f:
    f.extractall(d)
print("extracted", os.path.basename(z), "->", d)
PY
}

# ================= 组件定义 =================
JDK_URL="https://cdn.azul.com/zulu/bin/zulu17.68.203-ca-jdk17.0.20.1-win_x64.zip"
GRADLE_URL="https://services.gradle.org/distributions/gradle-8.2-bin.zip"
PLATFORM_URL="https://dl.google.com/android/repository/platform-34_r02.zip"
BUILDTOOLS_URL="https://dl.google.com/android/repository/build-tools_r34.0.0-windows.zip"
PLATFORMS_TOOLS_URL="https://dl.google.com/android/repository/platform-tools-latest-windows.zip"

cd "$ROOT"

# 1) JDK
dl_chunked "$JDK_URL" "$TOOLS/jdk.zip" && {
  extract_zip "$TOOLS/jdk.zip" "$TOOLS/jdk_ext"
  # 找到 zulu 顶层目录，改名为 jdk
  ZD=$(find "$TOOLS/jdk_ext" -maxdepth 1 -type d -name 'zulu*' | head -1)
  [ -z "$ZD" ] && ZD=$(find "$TOOLS/jdk_ext" -maxdepth 1 -type d | tail -1)
  rm -rf "$TOOLS/jdk"; mv "$ZD" "$TOOLS/jdk"
  rm -rf "$TOOLS/jdk_ext"
  log "[jdk] JAVA_HOME=$TOOLS/jdk"
}

# 2) Gradle
dl_chunked "$GRADLE_URL" "$TOOLS/gradle.zip" && {
  extract_zip "$TOOLS/gradle.zip" "$TOOLS/gradle_ext"
  GD=$(find "$TOOLS/gradle_ext" -maxdepth 1 -type d -name 'gradle-*' | head -1)
  rm -rf "$TOOLS/gradle"; mv "$GD" "$TOOLS/gradle"
  rm -rf "$TOOLS/gradle_ext"
  log "[gradle] $TOOLS/gradle/bin/gradle"
}

# 3) Android SDK 目录
SDK="$BE/sdk"; mkdir -p "$SDK/platforms" "$SDK/build-tools" "$SDK/platform-tools"

# platform-34
dl_chunked "$PLATFORM_URL" "$TOOLS/platform.zip" && {
  extract_zip "$TOOLS/platform.zip" "$TOOLS/platform_ext"
  P34=$(find "$TOOLS/platform_ext" -maxdepth 1 -type d -name 'android-34' | head -1)
  rm -rf "$SDK/platforms/android-34"; mv "$P34" "$SDK/platforms/android-34"
  rm -rf "$TOOLS/platform_ext"
}

# build-tools 34.0.0
dl_chunked "$BUILDTOOLS_URL" "$TOOLS/bt.zip" && {
  extract_zip "$TOOLS/bt.zip" "$TOOLS/bt_ext"
  BT="$SDK/build-tools/34.0.0"
  rm -rf "$BT"
  # 压缩包内结构通常为 android-34/34.0.0/
  SRC=$(find "$TOOLS/bt_ext" -maxdepth 2 -type d -path '*34.0.0' | head -1)
  [ -z "$SRC" ] && SRC=$(find "$TOOLS/bt_ext" -maxdepth 1 -type d -name 'android-34' | head -1)
  mv "$SRC" "$BT"
  rm -rf "$TOOLS/bt_ext"
}

# platform-tools (adb，安装/调试用)
dl_chunked "$PLATFORMS_TOOLS_URL" "$TOOLS/pt.zip" && {
  extract_zip "$TOOLS/pt.zip" "$TOOLS/pt_ext"
  PT=$(find "$TOOLS/pt_ext" -maxdepth 1 -type d -name 'platform-tools' | head -1)
  rm -rf "$SDK/platform-tools"; mv "$PT" "$SDK/platform-tools"
  rm -rf "$TOOLS/pt_ext"
}

# ================= 写出 env.sh =================
{
  echo "export JAVA_HOME='$TOOLS/jdk'"
  echo "export ANDROID_HOME='$SDK'"
  echo "export GRADLE='$TOOLS/gradle/bin/gradle'"
  echo "export PATH=\"\$JAVA_HOME/bin:\$TOOLS/gradle/bin:\$SDK/platform-tools:\$PATH\""
} > "$BE/env.sh"
log "================ fetch 完成，已写出 $BE/env.sh ================"
log "下一步: bash tools/build_apk.sh"
