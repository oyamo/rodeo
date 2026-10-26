# Install on Linux and macOS

Packages are available for Debian, Ubuntu, Fedora, RHEL, and macOS. Download the one for your system from the [releases page](https://github.com/oyamo/rodeo/releases/latest).

## Before you install

rodeo needs Java 21 and Git on the machine. The packages don't install them for you.

=== "Debian and Ubuntu"

    ```bash
    sudo apt-get update
    sudo apt-get install -y openjdk-21-jre-headless git
    ```

=== "Fedora and RHEL"

    ```bash
    sudo dnf install -y java-21-openjdk-headless git
    ```

=== "macOS"

    ```bash
    brew install openjdk@21 git
    ```

## Debian and Ubuntu

```bash
sudo apt install ./rodeo_<version>_amd64.deb
```

## Fedora and RHEL

```bash
sudo dnf install ./rodeo-<version>.x86_64.rpm
```

The package creates the `rodeo` user, installs the systemd service, and starts it. The service runs with the example configuration, which has placeholder secrets, so webhooks are rejected until you set real ones.

1. Edit `/etc/rodeo/config.toml` and set the webhook secrets. See [Configuration](../configuration.md).
2. Restart the service:

    ```bash
    sudo systemctl restart rodeo
    ```

3. Check that it's running:

    ```bash
    systemctl status rodeo
    journalctl -u rodeo -f
    ```

To upgrade, install the new package the same way. Your configuration is kept.

To remove rodeo, uninstall the package with `sudo apt remove rodeo` or `sudo dnf remove rodeo`. The configuration in `/etc/rodeo` is kept.

## macOS

Open `rodeo-<version>-macos.dmg` from the release page. It contains the jar, a launcher, an example configuration, a `launchd` service definition, and a README. Follow the README to copy the files, set the secrets, and start rodeo.

## Next steps

- [Configuration](../configuration.md): the settings in the config file
- [Running rodeo](../running.md): connect repositories and check deliveries
