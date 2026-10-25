# Install with Docker

Each push to `main` publishes an image to GitHub Container Registry. You can run it without building anything.

## Pull the image

```bash
docker pull ghcr.io/oyamo/rodeo:latest
```

Released versions are tagged with their version number, for example `ghcr.io/oyamo/rodeo:1.2.0`. Each commit is also tagged with its SHA.

The package must be public for anyone to pull it without logging in. Packages published by a workflow start as private. To make it public once, open **GitHub → Packages → rodeo → Package settings → Change visibility → Public**.

## Run the image

Mount your configuration file where rodeo expects it, and expose the port:

```bash
docker run -d --name rodeo \
  --restart unless-stopped \
  -v /etc/rodeo/config.toml:/etc/rodeo/config.toml:ro \
  -p 8080:8080 \
  ghcr.io/oyamo/rodeo:latest
```

See [Configuration](../configuration.md) for the file's contents, and [Running rodeo](../running.md) for the rest of the setup.

## Build from source

The rest of this page builds the same image from the repository.

### Build the image

From the project root:

```bash
docker build -t rodeo:latest .
```

The image is based on Eclipse Temurin 21 and includes Git. It runs as a non-root user named `rodeo`.

## Verify the image

Check the Java runtime:

```bash
docker run --rm --entrypoint java rodeo:latest -version
```

Check Git:

```bash
docker run --rm --entrypoint git rodeo:latest --version
```

Both commands should print a version.

## Add tools for stage commands

The image contains only Git and `sh`. A stage that calls another tool, such as Maven, Node, or Docker, needs that tool in the image. Extend the base image:

```dockerfile
FROM rodeo:latest
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
USER rodeo
```

Build the extended image under a new tag, and use that tag when you run the container.

## Next steps

- [Configuration](../configuration.md): the configuration file rodeo reads at startup
- [Running rodeo](../running.md): start the container and connect repositories
