#!/usr/bin/env bash
# emu.sh — boot / install / launch / kill / status for Clocktower Grimoire playtest emulators.
#
#   ./emu.sh boot 3          # boot 3 headless emulators on 5554/5556/5558, install the APK on each
#   ./emu.sh install         # uninstall+reinstall the debug APK on every connected emulator
#   ./emu.sh launch emulator-5554
#   ./emu.sh status
#   ./emu.sh kill
#
# All instances of the AVD run with -read-only (the emulator refuses to run a second
# instance of the same AVD otherwise). -read-only means each instance gets a throwaway
# copy of userdata, so `boot` always (re)installs the APK.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

: "${ANDROID_SDK:=/opt/homebrew/share/android-commandlinetools}"
: "${EMU_AVD:=grimoire}"
: "${EMU_APK:=$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk}"
: "${EMU_PKG:=com.clocktower.grimoire}"
: "${EMU_ACTIVITY:=$EMU_PKG/.MainActivity}"
: "${EMU_OUT:=$REPO_ROOT/tools/emu/out}"
: "${EMU_BOOT_TIMEOUT:=240}"

ADB="$ANDROID_SDK/platform-tools/adb"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"
EMULATOR="$ANDROID_SDK/emulator/emulator"
LOGDIR="$EMU_OUT/logs"

die() { echo "emu.sh: $*" >&2; exit 1; }
log() { echo "[emu] $*"; }

[ -n "$ADB" ] && [ -x "$ADB" ] || die "adb not found (set ANDROID_SDK)"

port_of()   { echo "${1#emulator-}"; }
serial_of() { echo "emulator-$1"; }

# Serials of every attached emulator, sorted by port.
list_serials() {
  "$ADB" devices 2>/dev/null | awk '/^emulator-[0-9]+\t/ {print $1}' | sort -t- -k2 -n
}

is_online() {
  "$ADB" devices 2>/dev/null | grep -q "^$1[[:space:]]\+device$"
}

is_booted() {
  [ "$("$ADB" -s "$1" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" = "1" ]
}

# ---------------------------------------------------------------- boot

wait_for_boot() {
  local serial="$1" deadline=$(( $(date +%s) + EMU_BOOT_TIMEOUT ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if is_online "$serial" && is_booted "$serial"; then return 0; fi
    sleep 1
  done
  return 1
}

# Make the device deterministic for UI automation: gesture nav, no animations,
# no screen-off, no stray dialogs.
configure_device() {
  local serial="$1"
  "$ADB" -s "$serial" shell cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1
  "$ADB" -s "$serial" shell settings put global window_animation_scale 0 >/dev/null 2>&1
  "$ADB" -s "$serial" shell settings put global transition_animation_scale 0 >/dev/null 2>&1
  "$ADB" -s "$serial" shell settings put global animator_duration_scale 0 >/dev/null 2>&1
  "$ADB" -s "$serial" shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1
  "$ADB" -s "$serial" shell settings put global stay_on_while_plugged_in 3 >/dev/null 2>&1
  "$ADB" -s "$serial" shell svc power stayon true >/dev/null 2>&1
  "$ADB" -s "$serial" shell wm dismiss-keyguard >/dev/null 2>&1
}

boot_one() {
  local port="$1" serial; serial="$(serial_of "$port")"
  if is_online "$serial"; then
    log "$serial already attached"
    return 0
  fi
  [ -x "$EMULATOR" ] || die "emulator binary not found at $EMULATOR"
  mkdir -p "$LOGDIR"
  log "booting $serial (avd=$EMU_AVD, read-only, headless)"
  nohup "$EMULATOR" -avd "$EMU_AVD" \
    -no-window -no-audio -no-boot-anim -no-snapshot -read-only \
    -gpu swiftshader_indirect -port "$port" \
    >"$LOGDIR/$port.log" 2>&1 &
  disown 2>/dev/null || true
}

cmd_boot() {
  local n="${1:-1}"
  case "$n" in ''|*[!0-9]*) die "boot: N must be a number";; esac
  [ "$n" -ge 1 ] || die "boot: N must be >= 1"
  "$ADB" start-server >/dev/null 2>&1

  local k port serials=""
  for (( k=0; k<n; k++ )); do
    port=$(( 5554 + 2*k ))
    boot_one "$port"
    serials="$serials $(serial_of "$port")"
  done

  local serial failed=0
  for serial in $serials; do
    log "waiting for $serial to finish booting..."
    if wait_for_boot "$serial"; then
      configure_device "$serial"
      install_one "$serial"
    else
      echo "emu.sh: $serial failed to boot within ${EMU_BOOT_TIMEOUT}s; see $LOGDIR/$(port_of "$serial").log" >&2
      failed=1
    fi
  done

  echo
  echo "SERIALS:"
  for serial in $serials; do
    is_online "$serial" && is_booted "$serial" && echo "  $serial"
  done
  return $failed
}

# ---------------------------------------------------------------- install

install_one() {
  local serial="$1"
  [ -f "$EMU_APK" ] || die "APK not found: $EMU_APK (build it first)"
  # Debug and CI builds are signed with different keys, so a plain `install -r`
  # fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE. Always uninstall first.
  "$ADB" -s "$serial" uninstall "$EMU_PKG" >/dev/null 2>&1
  log "installing $(basename "$EMU_APK") on $serial"
  if ! "$ADB" -s "$serial" install -t "$EMU_APK"; then
    echo "emu.sh: install failed on $serial" >&2
    return 1
  fi
}

cmd_install() {
  local serials="$*"
  [ -n "$serials" ] || serials="$(list_serials)"
  [ -n "$serials" ] || die "install: no emulators attached"
  local serial rc=0
  for serial in $serials; do install_one "$serial" || rc=1; done
  return $rc
}

# ---------------------------------------------------------------- launch

cmd_launch() {
  local serial="" fresh=0 a
  for a in "$@"; do
    case "$a" in
      --fresh) fresh=1 ;;
      *) serial="$a" ;;
    esac
  done
  [ -n "$serial" ] || die "usage: emu.sh launch <serial> [--fresh]"
  if [ "$fresh" -eq 1 ]; then
    # The app persists the game in progress, so a plain launch resumes it.
    # --fresh wipes app data to guarantee the Home screen.
    log "clearing app data on $serial"
    "$ADB" -s "$serial" shell pm clear "$EMU_PKG" >/dev/null 2>&1
  fi
  "$ADB" -s "$serial" shell am force-stop "$EMU_PKG" >/dev/null 2>&1
  "$ADB" -s "$serial" shell am start -n "$EMU_ACTIVITY" >/dev/null 2>&1 \
    || die "launch: could not start $EMU_ACTIVITY on $serial"
  log "launched $EMU_ACTIVITY on $serial"
}

# ---------------------------------------------------------------- kill

cmd_kill() {
  local serial
  for serial in $(list_serials); do
    log "killing $serial"
    "$ADB" -s "$serial" emu kill >/dev/null 2>&1
  done
  local i
  for (( i=0; i<20; i++ )); do
    [ -z "$(list_serials)" ] && break
    sleep 1
  done
  # Backstop for instances that ignored `emu kill`.
  pkill -f "qemu-system-aarch64.*-avd $EMU_AVD" >/dev/null 2>&1
  log "all emulators stopped"
}

# ---------------------------------------------------------------- status

cmd_status() {
  local serials; serials="$(list_serials)"
  if [ -z "$serials" ]; then echo "no emulators attached"; return 0; fi
  printf '%-16s %-8s %-10s %s\n' SERIAL BOOTED "RSS(MB)" FOREGROUND
  local serial port rss fg
  for serial in $serials; do
    port="$(port_of "$serial")"
    rss="$(ps -axo rss=,command= 2>/dev/null \
            | awk -v p="-port $port" 'index($0,p){s+=$1} END{if(s)printf "%d", s/1024; else printf "?"}')"
    fg="$("$ADB" -s "$serial" shell dumpsys activity activities 2>/dev/null \
          | sed -n 's/.*topResumedActivity=ActivityRecord{[^ ]* [^ ]* \([^ ]*\).*/\1/p' \
          | head -1 | tr -d '\r' | cut -c1-48)"
    printf '%-16s %-8s %-10s %s\n' "$serial" \
      "$(is_booted "$serial" && echo yes || echo no)" "$rss" "${fg:-?}"
  done
}

# ---------------------------------------------------------------- main

case "${1:-}" in
  boot)    shift; cmd_boot "$@" ;;
  install) shift; cmd_install "$@" ;;
  launch)  shift; cmd_launch "$@" ;;
  kill)    shift; cmd_kill "$@" ;;
  status)  shift; cmd_status "$@" ;;
  *)
    cat >&2 <<EOF
usage: emu.sh <command>

  boot N              boot N headless emulators (ports 5554, 5556, ...), configure
                      them for automation, and install the debug APK on each
  install [serial...] uninstall + install the APK (default: every attached emulator)
  launch <serial> [--fresh]
                      force-stop and start $EMU_ACTIVITY;
                      --fresh also wipes app data (the app resumes a saved game)
  kill                stop every attached emulator
  status              serials, boot state, RSS in MB, foreground activity

env: ANDROID_SDK=$ANDROID_SDK
     EMU_AVD=$EMU_AVD
     EMU_APK=$EMU_APK
     EMU_OUT=$EMU_OUT
EOF
    exit 2 ;;
esac
