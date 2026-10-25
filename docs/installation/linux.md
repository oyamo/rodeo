# Install on Linux and macOS

These steps install rodeo directly on the host, without Docker.

## 1. Install the prerequisites

=== "Debian and Ubuntu"

    ```bash
    sudo apt-get update
    sudo apt-get install -y openjdk-21-jdk-headless maven git
    ```

=== "Fedora and RHEL"

    ```bash
    sudo dnf install -y java-21-openjdk-devel maven git
    ```

=== "Arch Linux"

    ```bash
    sudo pacman -S --needed jdk21-openjdk maven git
    ```

=== "macOS"

    ```bash
    brew install openjdk@21 maven git
    export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
    ```

    Add the two `export` lines to your shell profile to keep them.

Confirm the versions:

```bash
java -version    # 21 or newer
mvn -version
git --version
```

## 2. Build the jar

From the project root:

```bash
mvn -B package
```

The jar is written to `target/rodeo-0.1.0-SNAPSHOT.jar`. Copy it to a location for the service:

```bash
sudo mkdir -p /opt/rodeo
sudo cp target/rodeo-0.1.0-SNAPSHOT.jar /opt/rodeo/rodeo.jar
```

## 3. Create a service user

Run rodeo as its own account, not as your login user:

```bash
sudo useradd --system --create-home --home-dir /var/lib/rodeo --shell /usr/sbin/nologin rodeo
```

## 4. Store the secrets

```bash
sudo mkdir -p /etc/rodeo
sudo tee /etc/rodeo/rodeo.env >/dev/null <<'EOF'
GITHUB_WEBHOOK_SECRET=replace-with-a-long-random-string
GITLAB_WEBHOOK_SECRET=replace-with-another-long-random-string
PORT=8080
EOF
sudo chown root:rodeo /etc/rodeo/rodeo.env
sudo chmod 640 /etc/rodeo/rodeo.env
```

## 5. Run as a service (systemd)

Create `/etc/systemd/system/rodeo.service`:

```ini
[Unit]
Description=rodeo webhook service
After=network-online.target
Wants=network-online.target

[Service]
User=rodeo
EnvironmentFile=/etc/rodeo/rodeo.env
ExecStart=/usr/bin/java -jar /opt/rodeo/rodeo.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

On macOS, run the jar directly, or write a `launchd` property list that sets the same variables.

Start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now rodeo
sudo systemctl status rodeo
journalctl -u rodeo -f
```

## Commands in `rodeo.toml`

Commands run as the `rodeo` user, with `PATH` and `HOME` set from that user's environment. Install every tool your commands need system-wide, for example `sudo apt-get install maven`, so it is on the default `PATH`.

## Next steps

Continue with [Configuration](../configuration.md) and [Running rodeo](../running.md).
