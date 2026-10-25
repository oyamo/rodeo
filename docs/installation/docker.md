# Install with Docker

This page builds the rodeo image and checks that it works. To run the container, see [Running rodeo](../running.md).

## Prerequisites

- Docker Engine or Docker Desktop
- The rodeo source code, with the `Dockerfile` at its root

## Build the image

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

- [Configuration](../configuration.md): the variables rodeo needs before it starts
- [Running rodeo](../running.md): start the container and connect repositories
