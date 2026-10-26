# Install with Docker

rodeo is available as a container image on GitHub Container Registry. Pull it and run it; nothing needs to be built.

## Pull the image

```bash
docker pull ghcr.io/oyamo/rodeo:latest
```

`latest` is the newest build. To pin a version, use its tag, for example `ghcr.io/oyamo/rodeo:1.2.0`.

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

## Check the image

```bash
docker run --rm --entrypoint java ghcr.io/oyamo/rodeo:latest -version
docker run --rm --entrypoint git ghcr.io/oyamo/rodeo:latest --version
```

Both commands print a version.

## Add tools for stage commands

The image contains Java, Git, and `sh`. A stage that calls another tool, such as Maven, Node, or Docker, needs that tool in the image. Build your own image on top of it:

```dockerfile
FROM ghcr.io/oyamo/rodeo:latest
USER root
RUN apk add --no-cache maven
USER rodeo
```

Build the image under your own name, and run it in place of `ghcr.io/oyamo/rodeo:latest`:

```bash
docker build -t my-rodeo .
docker run -d --name rodeo -v /etc/rodeo/config.toml:/etc/rodeo/config.toml:ro -p 8080:8080 my-rodeo
```

## Next steps

- [Configuration](../configuration.md): the configuration file rodeo reads at startup
- [Running rodeo](../running.md): start the container and connect repositories
