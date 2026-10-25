# Examples

Five real `rodeo.toml` files, from the simplest to the most involved. Each one is a complete file you can copy into a repository.

| # | Scenario | Features shown |
|---|---|---|
| 1 | [Run a library's tests](#1-run-a-librarys-tests) | A single command in a container |
| 2 | [Deploy a Spring Boot service to a server](#2-deploy-a-spring-boot-service-to-a-server) | Host stages, rollback with `on_fail` |
| 3 | [Staging and production from branches](#3-staging-and-production-from-branches) | Pipelines with their own branches, `teardown` |
| 4 | [Frontend and backend in one repository](#4-frontend-and-backend-in-one-repository) | Concurrent pipelines, cross-pipeline dependency, secrets, container images |
| 5 | [Multi-region Kubernetes rollout](#5-multi-region-kubernetes-rollout) | Image pipeline, staged regions, alerts on failure |

The server's configuration must define every secret an example names under `[rodeo.secrets]`. Stages that run on the server need `allow-host-execution = true`. Stages with no image use the server's `default-image`.

## 1. Run a library's tests

Every push to `main` runs the Maven test suite in a Java 21 container.

```toml
on = ["main"]

[library]
image = "maven:3.9-eclipse-temurin-21"

test = "mvn -B test"
```

## 2. Deploy a Spring Boot service to a server

The service runs as a systemd unit on the host. A push to `main` builds and tests it, installs the new jar, and restarts the unit. If a deploy command fails, the previous jar is restored.

```toml
on = ["main"]

[orders-api]
build = {
  run = "mvn -B -q package -DskipTests",
  host = true,
}
test = {
  run = "mvn -B test",
  needs = [],
  host = true,
}
deploy = {
  run = [
    "cp -f /opt/orders-api/app.jar /opt/orders-api/app.jar.previous 2>/dev/null || true",
    "install -m 644 target/orders-api-*.jar /opt/orders-api/app.jar",
    "sudo systemctl restart orders-api",
    "sleep 5",
    "curl -fsS http://localhost:8080/health",
  ],
  needs = ["build", "test"],
  host = true,
  timeout = "5m",
  on_fail = [
    "install -m 644 /opt/orders-api/app.jar.previous /opt/orders-api/app.jar",
    "sudo systemctl restart orders-api",
  ],
}
```

**Server requirements:** a `sudo` rule that lets the `rodeo` user restart `orders-api`, and `allow-host-execution = true`.

## 3. Staging and production from branches

Two pipelines in one file, each started by a different branch. Both run Docker Compose on the same host, with a separate Compose file for each environment.

```toml
[staging]
on = ["develop"]

deploy = {
  teardown = "docker compose -f compose.staging.yml down",
  run = "docker compose -f compose.staging.yml up -d --build",
  host = true,
}
smoke = {
  run = "curl -fsS https://staging.acme.dev/health",
  needs = ["deploy"],
  network = true,
  retries = 2,
}

[production]
on = ["main"]

build = {
  run = "docker compose -f compose.prod.yml build",
  host = true,
}
deploy = {
  teardown = "docker compose -f compose.prod.yml down",
  run = "docker compose -f compose.prod.yml up -d",
  needs = ["build"],
  host = true,
}
smoke = {
  run = "curl -fsS https://acme.dev/health",
  needs = ["deploy"],
  network = true,
  retries = 3,
}
```

**Things to note:**

- A push to `develop` runs only `staging`. A push to `main` runs only `production`.
- `teardown` stops the running Compose project before the new one starts. Its failure is ignored, so the first deploy works when nothing is running.

## 4. Frontend and backend in one repository

A React app in `web/` is copied into the nginx folder on the host. A Spring Boot API in `api/` runs on the host with Docker Compose. The web deploy waits for the API, because the app calls it.

```toml
on = ["main"]

[web]
build = {
  run = "npm ci && npm run build",
  dir = "web",
}
deploy = {
  run = "cp -r dist/. /usr/share/nginx/html/",
  dir = "web",
  needs = ["build", "api.deploy"],
  host = true,
}

[api]
build = {
  run = [
    "mvn -B -q clean",
    "mvn -B -DskipTests package",
  ],
  image = "maven:3.9-eclipse-temurin-21",
  dir = "api",
}
test = {
  run = "mvn -B verify",
  image = "maven:3.9-eclipse-temurin-21",
  dir = "api",
}
deploy = {
  teardown = "docker compose -f api/compose.yml down",
  run = "docker compose -f api/compose.yml up -d --build",
  needs = ["build", "test"],
  host = true,
}
```

**Things to note:**

- `dir` runs each stage in its own folder, so the two projects share the repository without interfering.
- `api.deploy` in `needs` is a dependency on a stage in another pipeline.
- The web deploy copies the built files into nginx's document root on the host, so it runs with `host = true`.

## 5. Multi-region Kubernetes rollout

An API image is built on the host, scanned, and then rolled out to two regions in sequence. The US region starts only after the EU smoke test passes. If anything fails, a Slack alert is sent.

```toml
on = ["main"]

[build-image]
build = {
  run = "docker build -t registry.acme.io/api:latest .",
  host = true,
}
push = {
  run = "docker push registry.acme.io/api:latest",
  needs = ["build"],
  host = true,
}
scan = {
  run = "trivy image --exit-code 1 registry.acme.io/api:latest",
  needs = ["push"],
  host = true,
}

[eu-west]
deploy = {
  run = "kubectl --context eu-west set image deployment/api api=registry.acme.io/api:latest",
  needs = ["build-image.scan"],
  host = true,
}
smoke = {
  run = "curl -fsS https://eu.api.acme.io/health",
  needs = ["deploy"],
  network = true,
  retries = 3,
}

[us-east]
deploy = {
  run = "kubectl --context us-east set image deployment/api api=registry.acme.io/api:latest",
  needs = ["build-image.scan", "eu-west.smoke"],
  host = true,
}
smoke = {
  run = "curl -fsS https://us.api.acme.io/health",
  needs = ["deploy"],
  network = true,
  retries = 3,
}

[alerts]
notify = {
  run = "curl -fsS -X POST https://hooks.slack.com/services/T000/B000/REPLACE_ME",
  when = "failure",
  network = true,
}
```

**Server requirements:** Docker, `trivy`, and `kubectl`, with a kubeconfig that has the `eu-west` and `us-east` contexts. Stages that call them run on the host.

## Choosing an example

| Your situation | Start from |
|---|---|
| Testing a repository before anything else | Example 1 |
| One service on a server | Example 2 |
| Separate staging and production branches | Example 3 |
| Several projects in one repository | Example 4 |
| Several regions or a formal release process | Example 5 |

For every field, see the [rodeo.toml reference](rodeo-toml/reference.md).
