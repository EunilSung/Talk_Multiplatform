#!/usr/bin/env bash
# 실화면 흐름을 한 플랫폼에 돌린다: 기기를 고르고(꺼져 있으면 켜고) 앱을 설치한 뒤 Maestro 로 .maestro 의 흐름을 돌린다.
#
#   .maestro/run.sh android <apk 경로>
#   .maestro/run.sh ios
#
# 기기는 환경변수나 local.properties 로 고른다. 없으면 켜져 있는 에뮬레이터·iPhone 시뮬레이터 중 첫 번째를 쓴다.
# 실기기는 고르지 않는다.
#   TALK_ANDROID_AVD / e2e.androidAvd  에뮬레이터 이름. 꺼져 있으면 켠다
#   TALK_IOS_DEVICE  / e2e.iosDevice   시뮬레이터 UDID 또는 이름. 꺼져 있으면 켠다
# 앱은 TALK_SERVER_BASE_URL(기본 http://localhost:8090)의 서버에 붙게 빌드되고, 그 서버가 떠 있어야 한다.
# `ai` 이름표가 붙은 흐름은 TALK_E2E_AI=true 일 때만 돈다(AI 키가 있는 서버에서만 뜻이 있다).
# 결과는 build/maestro/<플랫폼>/report.xml(JUnit)과 같은 폴더의 스크린샷·로그로 남는다.
set -euo pipefail

PLATFORM=${1:?android 또는 ios}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SERVER_URL=${TALK_SERVER_BASE_URL:-http://localhost:8090}
SERVER_PORT=${SERVER_URL##*:}
OUT=$ROOT/build/maestro/$PLATFORM

fail() { echo "[e2e] $*" >&2; exit 1; }
log() { echo "[e2e] $*" >&2; }

local_property() {
    [ -f "$ROOT/local.properties" ] || return 0
    sed -n "s/^$1=//p" "$ROOT/local.properties" | tail -1 | tr -d '[:space:]'
}

MAESTRO=$(command -v maestro || echo "$HOME/.maestro/bin/maestro")
[ -x "$MAESTRO" ] || fail "Maestro 가 없다. 설치: curl -fsSL https://get.maestro.mobile.dev | bash"
curl -sf "$SERVER_URL/health" >/dev/null || fail "서버($SERVER_URL)에 닿지 않는다. ./gradlew :server:runLocal 로 띄운다"

run_flows() {
    rm -rf "$OUT"
    mkdir -p "$OUT"
    log "$PLATFORM($1) 에서 흐름을 돌린다"
    local tags=--exclude-tags=ai
    [ "${TALK_E2E_AI:-}" = true ] && tags=
    "$MAESTRO" --device "$1" test -e TALK_SERVER="$SERVER_URL" "$ROOT/.maestro" $tags --format junit --output "$OUT/report.xml" --test-output-dir "$OUT"
}

android_avd_of() { "$ADB" -s "$1" emu avd name 2>/dev/null | head -1 | tr -d '\r'; }

running_emulators() { "$ADB" devices | awk '/^emulator-[0-9]+\tdevice$/ {print $1}'; }

find_android_device() {
    local avd=$1 serial
    if [ -z "$avd" ]; then
        running_emulators | head -1
        return
    fi
    for serial in $(running_emulators); do
        [ "$(android_avd_of "$serial")" = "$avd" ] && { echo "$serial"; return; }
    done
}

boot_android() {
    local avd=$1 serial waited=0
    log "에뮬레이터 $avd 를 켠다"
    nohup "$SDK/emulator/emulator" -avd "$avd" -no-snapshot-save -no-boot-anim >/dev/null 2>&1 &
    until serial=$(find_android_device "$avd") && [ -n "$serial" ] &&
        [ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
        sleep 3
        waited=$((waited + 3))
        [ $waited -lt 240 ] || fail "에뮬레이터 $avd 가 4분 안에 켜지지 않았다"
    done
    echo "$serial"
}

run_android() {
    local apk=${1:?apk 경로}
    [ -f "$apk" ] || fail "apk 가 없다: $apk"
    SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(local_property sdk.dir)}}
    ADB=$SDK/platform-tools/adb
    [ -x "$ADB" ] || fail "adb 를 찾지 못했다. local.properties 의 sdk.dir 을 확인한다"

    local avd=${TALK_ANDROID_AVD:-$(local_property e2e.androidAvd)}
    local serial
    serial=$(find_android_device "$avd" || true)
    if [ -z "$serial" ]; then
        [ -n "$avd" ] || fail "켜져 있는 에뮬레이터가 없다. e2e.androidAvd 를 주면 켜서 쓴다"
        serial=$(boot_android "$avd")
    fi

    log "$serial 에 설치한다"
    "$ADB" -s "$serial" install -r "$apk" >/dev/null
    case "$SERVER_URL" in
        http://localhost:*) "$ADB" -s "$serial" reverse "tcp:$SERVER_PORT" "tcp:$SERVER_PORT" >/dev/null ;;
    esac
    run_flows "$serial"
}

find_ios_device() {
    python3 - "$1" <<'EOF'
import json, subprocess, sys
want = sys.argv[1]
data = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "available", "-j"]))
devices = [d for runtime, ds in data["devices"].items() if "iOS" in runtime for d in ds]
if want:
    matched = [d for d in devices if want in (d["udid"], d["name"])]
    matched.sort(key=lambda d: d["state"] != "Booted")
else:
    matched = [d for d in devices if d["state"] == "Booted" and d["name"].startswith("iPhone")]
print(matched[0]["udid"] if matched else "")
EOF
}

run_ios() {
    local want=${TALK_IOS_DEVICE:-$(local_property e2e.iosDevice)}
    local udid
    udid=$(find_ios_device "$want")
    [ -n "$udid" ] || fail "쓸 시뮬레이터가 없다. e2e.iosDevice 에 UDID 나 이름을 준다"
    xcrun simctl boot "$udid" 2>/dev/null || true
    xcrun simctl bootstatus "$udid" -b >/dev/null

    mkdir -p "$ROOT/build/maestro"
    local build_log=$ROOT/build/maestro/ios-build.log
    log "iOS 앱을 빌드한다 (로그: $build_log)"
    xcodebuild -project "$ROOT/iosApp/iosApp.xcodeproj" -scheme iosApp -configuration Debug \
        -sdk iphonesimulator -destination "id=$udid" -derivedDataPath "$ROOT/build/ios-derived" \
        TALK_SERVER_BASE_URL="$SERVER_URL" build >"$build_log" 2>&1 ||
        { tail -30 "$build_log" >&2; fail "iOS 빌드가 실패했다"; }

    log "$udid 에 설치한다"
    xcrun simctl install "$udid" "$ROOT/build/ios-derived/Build/Products/Debug-iphonesimulator/Talk.app"
    run_flows "$udid"
}

case "$PLATFORM" in
    android) run_android "${2:-}" ;;
    ios) run_ios ;;
    *) fail "android 또는 ios 를 준다" ;;
esac
