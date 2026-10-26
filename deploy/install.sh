#!/bin/sh
set -eu

readonly SERVICE=rodeo
readonly SERVICE_USER=rodeo
readonly OPT_DIR=/opt/rodeo
readonly ETC_DIR=/etc/rodeo
readonly DATA_DIR=/var/lib/rodeo
readonly UNIT_DIR=/etc/systemd/system
readonly EXAMPLE_CONFIG_NAME=config.example.toml

HERE=$(cd "$(dirname "$0")" && pwd)
ACTION=""
JAR=""
CONFIG=""
PREFIX=""
DRY_RUN=0
SYSTEMD=1
PURGE=0

usage() {
    cat <<USAGE
usage: install.sh <install|upgrade|uninstall|status> [options]

  install     create the $SERVICE_USER user and directories, install the jar,
              write the systemd unit, and start the service
  upgrade     replace the jar and restart the service
  uninstall   stop the service and remove the jar and unit
  status      show the service status

options:
  --jar PATH       the rodeo jar to install (install, upgrade)
  --config PATH    config file to install; defaults to a copy of $EXAMPLE_CONFIG_NAME
  --purge          with uninstall, also remove the config and the $SERVICE_USER user
  --no-systemd     install files only; do not touch systemd
  --prefix DIR     install into DIR instead of the system (for testing)
  --dry-run        print the actions instead of running them
USAGE
}

die() {
    echo "install.sh: $*" >&2
    exit 1
}

run() {
    if [ "$DRY_RUN" -eq 1 ]; then
        echo "+ $*"
    else
        "$@"
    fi
}

# Where a system path lands. Empty PREFIX means the real system.
at() {
    printf '%s%s' "$PREFIX" "$1"
}

# True when the script changes the real system: not a test prefix, not a dry run.
touches_system() {
    [ -z "$PREFIX" ] && [ "$DRY_RUN" -eq 0 ]
}

systemctl_if_enabled() {
    if [ "$SYSTEMD" -eq 1 ]; then
        run systemctl "$@"
    fi
}

require_linux() {
    if [ -z "$PREFIX" ] && [ "$DRY_RUN" -eq 0 ] && [ "$(uname -s)" != "Linux" ]; then
        die "this script manages systemd services on Linux. On other systems run $HERE/$SERVICE --rodeo.config=PATH directly."
    fi
}

require_root() {
    if touches_system && [ "$(id -u)" -ne 0 ]; then
        die "run as root, for example: sudo $0 $ACTION ..."
    fi
}

require_jar() {
    [ -n "$JAR" ] || die "--jar PATH is required"
    [ -f "$JAR" ] || die "no such jar: $JAR"
}

create_account() {
    if [ -z "$PREFIX" ] && ! id "$SERVICE_USER" >/dev/null 2>&1; then
        run useradd --system --home-dir "$DATA_DIR" --shell /usr/sbin/nologin "$SERVICE_USER"
    fi
}

install_files() {
    run mkdir -p "$(at "$OPT_DIR")" "$(at "$ETC_DIR")" "$(at "$DATA_DIR")" "$(at "$UNIT_DIR")"
    run install -m 0644 "$JAR" "$(at "$OPT_DIR")/$SERVICE.jar"
    run install -m 0755 "$HERE/$SERVICE" "$(at "$OPT_DIR")/$SERVICE"
    install_config
    if touches_system; then
        chown "root:$SERVICE_USER" "$ETC_DIR/config.toml"
        chown "$SERVICE_USER" "$DATA_DIR"
    fi
    run install -m 0644 "$HERE/$SERVICE.service" "$(at "$UNIT_DIR")/$SERVICE.service"
}

install_config() {
    config="$(at "$ETC_DIR")/config.toml"
    if [ -f "$config" ]; then
        return
    fi
    if [ -n "$CONFIG" ]; then
        run install -m 0640 "$CONFIG" "$config"
    else
        run install -m 0640 "$HERE/$EXAMPLE_CONFIG_NAME" "$config"
        echo "edit $config and set the webhook secrets before starting $SERVICE"
    fi
}

install_rodeo() {
    require_linux
    require_root
    require_jar
    create_account
    install_files
    systemctl_if_enabled daemon-reload
    systemctl_if_enabled enable --now "$SERVICE"
}

upgrade_rodeo() {
    require_linux
    require_root
    require_jar
    run install -m 0644 "$JAR" "$(at "$OPT_DIR")/$SERVICE.jar"
    systemctl_if_enabled restart "$SERVICE"
}

uninstall_rodeo() {
    require_linux
    require_root
    systemctl_if_enabled disable --now "$SERVICE"
    run rm -f "$(at "$UNIT_DIR")/$SERVICE.service"
    run rm -rf "$(at "$OPT_DIR")"
    systemctl_if_enabled daemon-reload
    if [ "$PURGE" -eq 1 ]; then
        run rm -rf "$(at "$ETC_DIR")" "$(at "$DATA_DIR")"
        if [ -z "$PREFIX" ]; then
            run userdel "$SERVICE_USER"
        fi
    else
        echo "kept $(at "$ETC_DIR") and the $SERVICE_USER user; pass --purge to remove them"
    fi
}

status_rodeo() {
    systemctl_if_enabled status "$SERVICE"
}

[ $# -gt 0 ] || { usage; exit 1; }
ACTION="$1"
shift
while [ $# -gt 0 ]; do
    case "$1" in
        --jar) JAR="$2"; shift 2 ;;
        --config) CONFIG="$2"; shift 2 ;;
        --prefix) PREFIX="$2"; shift 2 ;;
        --purge) PURGE=1; shift ;;
        --no-systemd) SYSTEMD=0; shift ;;
        --dry-run) DRY_RUN=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "unknown option: $1" ;;
    esac
done

case "$ACTION" in
    install) install_rodeo ;;
    upgrade) upgrade_rodeo ;;
    uninstall) uninstall_rodeo ;;
    status) status_rodeo ;;
    *) usage; exit 1 ;;
esac
