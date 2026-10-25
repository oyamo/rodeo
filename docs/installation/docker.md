# Install with Docker

The image includes the rodeo service, Java 21, and Git. Build it from the project root.

## Build the image

```bash
docker build -t rodeo:latest .
```

## Run the container

Pass the secrets with an environment file so they do not appear in your shell history:

```bash
cat > rodeo.env <<'EOF'
GITHUB_WEBHOOK_SECRET=replace-with-a-long-random-string
GITLAB_WEBHOOK_SECRET=replace-with-another-long-random-string
EOF
chmod 600 rodeo.env

docker run -d --name rodeo \
  --restart unless-stopped \
  --env-file rodeo.env \
  -p 8080:8080 \
  rodeo:latest
```

Check that it started:

```bash
docker logs -f rodeo
```

## Commands in `rodeo.toml`

The image contains only `git` and `sh`. A `rodeo.toml` command that needs another tool, such as Maven, Node, or Docker, fails unless that tool is in the image. Build your own image on top of `rodeo:latest` and install what your commands need:

```dockerfile
FROM rodeo:latest
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
USER rodeo
```

## Update

Rebuild the image and replace the container. The secrets stay in `rodeo.env`:

```bash
docker build -t rodeo:latest .
docker rm -f rodeo
docker run -d --name rodeo --restart unless-stopped --env-file rodeo.env -p 8080:8080 rodeo:latest
```

## Next steps

Continue with [Configuration](../configuration.md) and [Running rodeo](../running.md).
