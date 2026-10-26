#!/bin/sh
set -e

systemctl disable --now rodeo >/dev/null 2>&1 || true
