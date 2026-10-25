# Execution controls

These stage options control how a stage runs: where, for how long, how many attempts, and what happens after a failure.

## Where a stage runs

Every stage runs in one of two places.

| Place | Selected when | What it means |
|---|---|---|
| A container | The stage, its pipeline, or the top level sets `image` | Each command runs in a new container from that image, which is removed when the command finishes. |
| The server | No image is set, and the server allows host execution | Each command runs directly on the server, as the rodeo service user, with access to the host's files. |

If no image applies and the server does not allow host execution, the file is rejected. See [Validation and errors](validation.md#has-no-image-and-host-execution-is-disabled).

### Images

Set `image` at the top level, in a pipeline, or in a stage. The most specific value wins.

```toml
on = ["main"]
image = "maven:3.9-eclipse-temurin-21"

[api]
build = "mvn -B package"

[web]
image = "node:22"
build = "npm ci && npm run build"
```

`image` is optional. When no image is set in the stage, its pipeline, or the top level, the server's `default-image` is used. If the server sets neither, the stage has nothing to run in, and it is rejected unless host execution is allowed.

The image must provide `sh`, because commands run as `sh -c`. Every tool a command uses must be installed in the image.

### What a container gets

| Property | Value |
|---|---|
| Filesystem | Only the run's checkout, mounted at `/work`. Files outside it are not visible. |
| Working directory | `/work`, or `/work/<dir>` when `dir` is set |
| Network | None, unless the stage sets `network = true` |
| CPU and memory | Set by the server: `stage-cpus` and `stage-memory` under `[rodeo.exec]` |
| Process count | At most 512 |
| Linux capabilities | All dropped, and privilege escalation is blocked |
| User | The same user ID as the rodeo service, so files written to `/work` stay owned by it |
| Lifetime | One command. The container is removed afterward, and nothing carries over except files in `/work`. |
| Secrets | Passed by name. Their values are supplied through the Docker client's environment, not on the command line. |

### Network access

Containers have no network by default. A stage that downloads dependencies or calls a deployment API must ask for it:

```toml
[web]
image = "node:22"
build = {
  run = "npm ci",
  network = true,
}
```

`network` needs an image. A stage that runs on the server already has the server's network.

### Each command is a new container

A stage with several commands starts one container per command. State kept in `/work` persists between them. State kept anywhere else does not. Put dependencies and build output under the checkout, and write nothing you need to the image's own filesystem.

### Running on the server

A stage with no image runs on the server. This is powerful and is not isolated, so the server operator must allow it with `allow-host-execution = true` under `[rodeo.exec]`. Use it only for work that must touch the server itself, such as restarting a systemd service. See [Configuration](../configuration.md#rodeoexec).

A stage in a pipeline that sets an image can still opt out with `host = true`:

```toml
on = ["main"]
image = "alpine:3.20"

[app]
build  = "make"
deploy = {
  run = "systemctl restart app",
  host = true,
  needs = ["build"],
}
```

`build` runs in a container, and `deploy` runs on the server.

## Working directory: `dir`

By default a stage runs in the repository root. `dir` changes that to a subdirectory.

```toml
[web]
build = {
  run = ["npm ci", "npm run build"],
  dir = "apps/web",
}
```

| Rule | Detail |
|---|---|
| Relative | `dir` must be relative to the repository root. An absolute path is rejected. |
| Contained | `dir` may not leave the repository. `../other` is rejected. |
| Not validated on disk | rodeo checks the path syntax when it parses the file. If the directory does not exist, the stage fails when it starts. |

## Timeouts: `timeout`

`timeout` limits how long each command in the stage may run. The default is `"10m"`.

```toml
[api]
migrate = {
  run = "npx prisma migrate deploy",
  timeout = "30m",
}
```

| Format | Meaning |
|---|---|
| `"30s"` | 30 seconds |
| `"15m"` | 15 minutes |
| `"2h"` | 2 hours |

The timeout applies to each command separately. A command that runs past its timeout is stopped, and its step is reported as timed out. The stage then fails, unless it has retries left.

Cloning the repository has its own fixed limit of 5 minutes.

## Retries: `retries`

`retries` is the number of extra attempts after a failure. The default is `0`. The maximum is `5`.

```toml
[api]
deploy = {
  run = "helm upgrade --install app ./chart",
  retries = 2,
}
```

With `retries = 2`, a stage can run up to three times. rodeo waits 10 seconds between attempts. Each attempt runs every command in the stage from the beginning.

Retry only steps that are safe to repeat. A script that is not idempotent should not be retried blindly.

## Optional stages: `optional`

`optional` is a boolean. The default is `false`.

```toml
[api]
lint = {
  run = "make lint",
  optional = true,
}
```

An optional stage that fails does not fail the run, and its dependents still run. The failure is still recorded in the logs and in the summary as `FAILED`.

## Failure handling: `on_fail`

`on_fail` lists commands to run after the final failed attempt. It accepts a string or a list of strings.

```toml
[api]
deploy = {
  run = "helm upgrade --install app ./chart",
  retries = 1,
  on_fail = "helm rollback app",
}
```

Use `on_fail` for cleanup or for undoing partial work. It does not change the stage's status. The stage is still `FAILED`, and the `on_fail` commands do not affect whether later stages run. To react to a failure with a separate stage, use `when = "failure"`. See [Dependencies and conditions](dependencies-and-conditions.md#roll-back-on-failure).

`on_fail` runs with the same environment, directory and timeout as the stage.

## Parallelism

Stages that do not depend on each other run in parallel. The server limits how many run at once with `max-parallel-stages` under `[rodeo.exec]`, which defaults to 4. Runs are processed one at a time, so the limit applies to the stages of the current run. See [Configuration](../configuration.md#rodeoexec).

## Teardown: `teardown`

`teardown` lists commands that stop whatever the previous run started, such as a container or a service. rodeo runs them before the stage's commands, on every attempt.

```toml
[auth-service.deploy]
teardown = "docker compose down"
run = "docker compose up -d auth-service"
host = true
```

Teardown is optional, and it is expected to fail when nothing is running. A failed teardown command is logged and the stage continues. Only the stage's own `run` commands decide whether the stage succeeds.

## Environment file: `env_file`

`env_file` loads variables from a file in the repository. The file holds one `KEY=VALUE` pair per line. Blank lines and lines starting with `#` are ignored.

```text
# deploy/production.env
REGION=eu-west
REPLICAS=3
```

```toml
[frontend.deploy]
run = "rsync -az dist/ deploy@web.example.com:/srv/web/"
env_file = "deploy/production.env"
network = true
```

The stage's variables come from these sources, in increasing order of precedence:

1. `env_file`
2. The `env` table in the pipeline and the stage
3. The secrets the stage requests
4. The variables rodeo sets (`RODEO_*`)

Keep secrets out of the env file. Request them with `secrets`, which are defined on the server. The path must stay inside the repository. A missing file fails the stage.

## Summary of execution options

| Option | Default | Scope |
|---|---|---|
| `dir` | repository root | One stage |
| `timeout` | `"10m"` | Each command in the stage |
| `retries` | `0` | The whole stage, up to 5 extra attempts |
| `optional` | `false` | The stage's effect on the run outcome |
| `on_fail` | none | Runs once after the last failed attempt |
| `teardown` | none | Runs before each attempt. Failures are ignored. |
| `env_file` | none | Variables loaded from a repository file, lowest precedence |
