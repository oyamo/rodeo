#!/bin/sh
set -e

if ! id rodeo >/dev/null 2>&1; then
    useradd --system --home-dir /var/lib/rodeo --shell /usr/sbin/nologin rodeo
fi
mkdir -p /var/lib/rodeo
chown rodeo /var/lib/rodeo
chown root:rodeo /etc/rodeo/config.toml
chmod 0640 /etc/rodeo/config.toml
systemctl daemon-reload >/dev/null 2>&1 || true
echo "rodeo installed. Set the webhook secrets in /etc/rodeo/config.toml, then run: systemctl enable --now rodeo"
