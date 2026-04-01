#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_AVD="${EMULATOR_AVD:-tv_test}"
AVD_NAME="$DEFAULT_AVD"
GPU_MODE="${EMULATOR_GPU_MODE:-host}"
BOOT_TIMEOUT="${EMULATOR_BOOT_TIMEOUT:-180}"
WAIT_FOR_DEVICE=1
WIPE_DATA=0
RESET_ADB_KEYS=0

usage() {
  cat <<'EOF'
Usage:
  ./scripts/run-emulator.sh [options]

Options:
  --avd <name>           AVD name. Default: tv_test
  --gpu <mode>           Emulator GPU mode. Default: host
  --wipe-data            Wipe emulator userdata before boot
  --reset-adb-keys       Backup and recreate ~/.android/adbkey*
  --no-wait              Start emulator and exit without waiting for adb
  --boot-timeout <sec>   Seconds to wait for adb/device state. Default: 180
  -h, --help             Show this help
EOF
}

log() {
  printf '[emulator] %s\n' "$*"
}

fail() {
  printf '[emulator] %s\n' "$*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --avd)
      [[ $# -ge 2 ]] || fail "--avd requires a value"
      AVD_NAME="$2"
      shift 2
      ;;
    --gpu)
      [[ $# -ge 2 ]] || fail "--gpu requires a value"
      GPU_MODE="$2"
      shift 2
      ;;
    --wipe-data)
      WIPE_DATA=1
      shift
      ;;
    --reset-adb-keys)
      RESET_ADB_KEYS=1
      shift
      ;;
    --no-wait)
      WAIT_FOR_DEVICE=0
      shift
      ;;
    --boot-timeout)
      [[ $# -ge 2 ]] || fail "--boot-timeout requires a value"
      BOOT_TIMEOUT="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown option: $1"
      ;;
  esac
done

find_sdk_root() {
  local candidate
  local local_properties="$ROOT_DIR/local.properties"

  if [[ -f "$local_properties" ]]; then
    candidate="$(sed -n 's/^sdk\.dir=//p' "$local_properties" | tail -n1)"
    if [[ -n "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  fi

  for candidate in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "$HOME/Android/Sdk"; do
    if [[ -n "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

SDK_ROOT="$(find_sdk_root || true)"
[[ -n "$SDK_ROOT" ]] || fail "Android SDK not found. Set ANDROID_SDK_ROOT or fill local.properties."

ADB="$SDK_ROOT/platform-tools/adb"
EMULATOR="$SDK_ROOT/emulator/emulator"
AVD_DIR="$HOME/.android/avd/${AVD_NAME}.avd"
CONFIG_FILE="$AVD_DIR/config.ini"
STDOUT_LOG="/tmp/${AVD_NAME}_emulator.stdout.log"
LOGCAT_LOG="/tmp/${AVD_NAME}_emulator.logcat.txt"

[[ -x "$ADB" ]] || fail "adb not found: $ADB"
[[ -x "$EMULATOR" ]] || fail "emulator not found: $EMULATOR"
[[ -d "$AVD_DIR" ]] || fail "AVD not found: $AVD_DIR"
[[ -f "$CONFIG_FILE" ]] || fail "AVD config not found: $CONFIG_FILE"

upsert_config_value() {
  local file="$1"
  local key="$2"
  local value="$3"
  local escaped_key="${key//./\\.}"

  if awk -F= -v target="$key" '$1 == target { found = 1 } END { exit(found ? 0 : 1) }' "$file"; then
    sed -i "s|^${escaped_key}=.*|${key}=${value}|" "$file"
  else
    printf '%s=%s\n' "$key" "$value" >>"$file"
  fi
}

normalize_avd_config() {
  rm -f "$AVD_DIR/quickbootChoice.ini"

  upsert_config_value "$CONFIG_FILE" "avd.id" "$AVD_NAME"
  upsert_config_value "$CONFIG_FILE" "avd.name" "$AVD_NAME"
  upsert_config_value "$CONFIG_FILE" "disk.dataPartition.path" "$HOME/.android/avd/../avd/${AVD_NAME}.avd/userdata-qemu.img"
  upsert_config_value "$CONFIG_FILE" "fastboot.forceColdBoot" "yes"
  upsert_config_value "$CONFIG_FILE" "fastboot.forceFastBoot" "no"
  upsert_config_value "$CONFIG_FILE" "firstboot.bootFromDownloadableSnapshot" "no"
  upsert_config_value "$CONFIG_FILE" "firstboot.bootFromLocalSnapshot" "no"
  upsert_config_value "$CONFIG_FILE" "firstboot.saveToLocalSnapshot" "no"
  upsert_config_value "$CONFIG_FILE" "hw.gpu.enabled" "yes"
  upsert_config_value "$CONFIG_FILE" "hw.gpu.mode" "$GPU_MODE"
  upsert_config_value "$CONFIG_FILE" "hw.initialOrientation" "landscape"
  upsert_config_value "$CONFIG_FILE" "hw.keyboard" "yes"
  upsert_config_value "$CONFIG_FILE" "userdata.useQcow2" "yes"
  upsert_config_value "$CONFIG_FILE" "vm.heapSize" "512"
}

reset_adb_keys() {
  local backup_dir="$HOME/.android/adbkey-backups"
  local ts

  ts="$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$backup_dir"

  [[ -f "$HOME/.android/adbkey" ]] && cp -a "$HOME/.android/adbkey" "$backup_dir/adbkey.$ts"
  [[ -f "$HOME/.android/adbkey.pub" ]] && cp -a "$HOME/.android/adbkey.pub" "$backup_dir/adbkey.pub.$ts"

  rm -f "$HOME/.android/adbkey" "$HOME/.android/adbkey.pub"
  "$ADB" kill-server >/dev/null 2>&1 || true
  "$ADB" start-server >/dev/null

  log "adb keys recreated, backups stored in $backup_dir"
}

find_serial_for_avd() {
  local serial
  local name

  while read -r serial _; do
    [[ -n "$serial" ]] || continue
    name="$("$ADB" -s "$serial" emu avd name 2>/dev/null | sed -n '1p' | tr -d '\r' || true)"
    if [[ "$name" == "$AVD_NAME" ]]; then
      printf '%s\n' "$serial"
      return 0
    fi
  done < <("$ADB" devices | awk 'NR > 1 && $1 != "" { print $1, $2 }')

  return 1
}

device_ready() {
  local serial="$1"
  local state
  local boot_completed

  state="$("$ADB" -s "$serial" get-state 2>/dev/null || true)"
  [[ "$state" == "device" ]] || return 1

  boot_completed="$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [[ "$boot_completed" == "1" ]]
}

restart_adb_server() {
  "$ADB" kill-server >/dev/null 2>&1 || true
  "$ADB" start-server >/dev/null
}

wait_for_device() {
  local deadline=$((SECONDS + BOOT_TIMEOUT))
  local attempt=0
  local serial=""

  while (( SECONDS < deadline )); do
    serial="$(find_serial_for_avd || true)"

    if [[ -n "$serial" ]] && device_ready "$serial"; then
      "$ADB" -s "$serial" shell settings put system screen_off_timeout 2147483647 >/dev/null 2>&1 || true
      log "Emulator is ready: $serial"
      return 0
    fi

    if (( WAIT_FOR_DEVICE == 0 )); then
      return 0
    fi

    if (( attempt > 0 && attempt % 3 == 0 )); then
      "$ADB" reconnect offline >/dev/null 2>&1 || true
    fi

    if (( attempt > 0 && attempt % 6 == 0 )); then
      restart_adb_server
    fi

    sleep 5
    attempt=$((attempt + 1))
  done

  fail "Emulator did not become ready within ${BOOT_TIMEOUT}s. See $STDOUT_LOG and $LOGCAT_LOG."
}

normalize_avd_config
"$ADB" start-server >/dev/null

if (( RESET_ADB_KEYS == 1 )); then
  reset_adb_keys
fi

existing_serial="$(find_serial_for_avd || true)"
if [[ -n "$existing_serial" ]]; then
  log "AVD $AVD_NAME is already running on $existing_serial"
  wait_for_device
  exit 0
fi

emulator_args=(
  -avd "$AVD_NAME"
  -no-snapshot-load
  -no-boot-anim
  -gpu "$GPU_MODE"
  -logcat "*:I"
  -logcat-output "$LOGCAT_LOG"
)

if (( WIPE_DATA == 1 )); then
  emulator_args+=(-wipe-data)
fi

log "Starting emulator $AVD_NAME"
nohup "$EMULATOR" "${emulator_args[@]}" >"$STDOUT_LOG" 2>&1 &
emulator_pid=$!
disown "$emulator_pid" 2>/dev/null || true

log "Emulator pid: $emulator_pid"
log "stdout log: $STDOUT_LOG"
log "logcat log: $LOGCAT_LOG"

wait_for_device
