# Install on Linux and macOS

This page installs the prerequisites, builds the rodeo jar, and checks the installation. To run rodeo as a service, see [Running rodeo](../running.md).

## Prerequisites

Install Java 21, Maven, and Git.

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

    Java 21 is installed under `$(brew --prefix openjdk@21)`. Run the build with that JDK, and point `[rodeo.tools]` at Git with its full path if you need to.

## Build the jar

From the project root:

```bash
mvn -B package
```

The jar is written to `target/rodeo-0.1.0-SNAPSHOT.jar`.

## Install the jar

Copy the jar to a fixed location, so the service does not depend on the source tree:

```bash
sudo mkdir -p /opt/rodeo
sudo cp target/rodeo-0.1.0-SNAPSHOT.jar /opt/rodeo/rodeo.jar
```

## Verify the installation

```bash
java -version
mvn -version
git --version
```

Each command should print a version. Java must be version 21 or later.

## Next steps

- [Configuration](../configuration.md): the configuration file rodeo reads at startup
- [Running rodeo](../running.md): run rodeo as a service and connect repositories
