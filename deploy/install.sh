#!/bin/sh
set -eu

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

  install     create the rodeo user and directories, install the jar,
              write the systemd unit, and start the service
  upgrade     replace the jar and restart the service
  uninstall   stop the service and remove the jar and unit
  status      show the service status

options:
  --jar PATH       the rodeo jar to install (install, upgrade)
  --config PATH    config file to install; defaults to a copy of config.example.toml
  --purge          with uninstall, also remove the config and the rodeo user
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

require_linux() {
    if [ -z "$PREFIX" ] && [ "$DRY_RUN" -eq 0 ] && [ "$(uname -s)" != "Linux" ]; then
        die "this script manages systemd services on Linux. On other systems run $HERE/rodeo --rodeo.config=PATH directly."
    fi
}

require_root() {
    if [ -z "$PREFIX" ] && [ "$DRY_RUN" -eq 0 ] && [ "$(id -u)" -ne 0 ]; then
        die "run as root, for example: sudo $0 $ACTION ..."
    fi
}

require_jar() {
    [ -n "$JAR" ] || die "--jar PATH is required"
    [ -f "$JAR" ] || die "no such jar: $JAR"
}

create_account() {
    if [ -z "$PREFIX" ] && ! id rodeo >/dev/null 2>&1; then
        run useradd --system --home-dir /var/lib/rodeo --shell /usr/sbin/nologin rodeo
    fi
}

install_files() {
    run mkdir -p "$PREFIX/opt/rodeo" "$PREFIX/etc/rodeo" "$PREFIX/var/lib/rodeo" "$PREFIX/etc/systemd/system"
    run install -m 0644 "$JAR" "$PREFIX/opt/rodeo/rodeo.jar"
    run install -m 0755 "$HERE/rodeo" "$PREFIX/opt/rodeo/rodeo"
    if [ ! -f "$PREFIX/etc/rodeo/config.toml" ]; then
        if [ -n "$CONFIG" ]; then
            run install -m 0640 "$CONFIG" "$PREFIX/etc/rodeo/config.toml"
        else
            run install -m 0640 "$HERE/config.example.toml" "$PREFIX/etc/rodeo/config.toml"
            echo "edit $PREFIX/etc/rodeo/config.toml and set the webhook secrets before starting rodeo"
        fi
    fi
    if [ -z "$PREFIX" ] && [ "$DRY_RUN" -eq 0 ]; then
        chown root:rodeo /etc/rodeo/config.toml
        chown rodeo /var/lib/rodeo
    fi
    run install -m 0644 "$HERE/rodeo.service" "$PREFIX/etc/systemd/system/rodeo.service"
}

systemctl_if_enabled() {
    if [ "$SYSTEMD" -eq 1 ]; then
        run systemctl "$@"
    fi
}

install_rodeo() {
    require_linux
    require_root
    require_jar
    create_account
    install_files
    systemctl_if_enabled daemon-reload
    systemctl_if_enabled enable --now rodeo
}

upgrade_rodeo() {
    require_linux
    require_root
    require_jar
    run install -m 0644 "$JAR" "$PREFIX/opt/rodeo/rodeo.jar"
    systemctl_if_enabled restart rodeo
}

uninstall_rodeo() {
    require_linux
    require_root
    systemctl_if_enabled disable --now rodeo
    run rm -f "$PREFIX/etc/systemd/system/rodeo.service"
    run rm -rf "$PREFIX/opt/rodeo"
    systemctl_if_enabled daemon-reload
    if [ "$PURGE" -eq 1 ]; then
        run rm -rf "$PREFIX/etc/rodeo" "$PREFIX/var/lib/rodeo"
        if [ -z "$PREFIX" ]; then
            run userdel rodeo
        fi
    else
        echo "kept $PREFIX/etc/rodeo and the rodeo user; pass --purge to remove them"
    fi
}

status_rodeo() {
    systemctl_if_enabled status rodeo
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
