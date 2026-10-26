# Install on Linux and macOS

This page installs rodeo as a service on Linux with systemd, using the installer script. The installer creates the `rodeo` user and directories, installs the jar and launcher, writes the service definition, and starts the service.

## Prerequisites

Install Java 21 and Git.

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
    ```

    Java 21 is installed under `$(brew --prefix openjdk@21)`. The installer manages systemd services, so on macOS run rodeo directly instead, as shown in [Run](#run-directly).

## Install from a package

Each release publishes `.deb` and `.rpm` packages. Download the one for your distribution from the release page, then install it:

```bash
sudo apt install ./rodeo_<version>_amd64.deb     # Debian and Ubuntu
sudo dnf install ./rodeo-<version>.x86_64.rpm    # Fedora and RHEL
```

The package creates the `rodeo` user, installs the systemd service, and starts it. Set the webhook secrets in `/etc/rodeo/config.toml`, then restart the service:

```bash
sudo systemctl restart rodeo
```

Removing the package stops the service and keeps `/etc/rodeo`.

## Build the jar

```bash
git clone https://github.com/oyamo/rodeo.git
cd rodeo
mvn -B package
```

## Install

From the repository root, run the installer as root:

```bash
sudo deploy/install.sh install --jar target/rodeo-0.1.0-SNAPSHOT.jar
```

The installer:

- creates the `rodeo` system user and `/var/lib/rodeo`
- copies the jar to `/opt/rodeo` and the launcher beside it
- creates `/etc/rodeo/config.toml` from the example, if it doesn't exist
- installs and starts the `rodeo` systemd service

Set the webhook secrets in `/etc/rodeo/config.toml`, then restart the service:

```bash
sudo systemctl restart rodeo
```

To install a prepared config file instead of the example, add `--config PATH`.

## Check it

```bash
sudo deploy/install.sh status
journalctl -u rodeo -f
```

## Upgrade

Build the new jar, then:

```bash
sudo deploy/install.sh upgrade --jar target/rodeo-0.1.0-SNAPSHOT.jar
```

The configuration and the service user are kept.

## Uninstall

```bash
sudo deploy/install.sh uninstall
```

This removes the service and the jar. It keeps `/etc/rodeo` and the `rodeo` user. Add `--purge` to remove them too.

## macOS

Each release publishes a `rodeo-<version>-macos.dmg`. It contains the jar, a launcher, an example configuration, a `launchd` service definition, and a short README.

1. Open the disk image and follow the README inside it.
2. Install Java 21 and Git first, for example with Homebrew: `brew install openjdk@21 git`.

## Run directly

To run rodeo without systemd, use the launcher with a config file:

```bash
java -jar target/rodeo-0.1.0-SNAPSHOT.jar --rodeo.config=/path/to/config.toml
```

## Next steps

- [Configuration](../configuration.md): the settings in the config file
- [Running rodeo](../running.md): connect repositories and check deliveries
