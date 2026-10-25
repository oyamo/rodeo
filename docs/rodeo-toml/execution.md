# Execution controls

These stage options control how a stage runs: where, for how long, how many attempts, and what happens after a failure.

## Working directory: `dir`

By default a stage runs in the repository root. `dir` changes that to a subdirectory.

```toml
[web]
build = { run = ["npm ci", "npm run build"], dir = "apps/web" }
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
migrate = { run = "./migrate.sh", timeout = "30m" }
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
deploy = { run = "./deploy.sh", retries = 2 }
```

With `retries = 2`, a stage can run up to three times. rodeo waits 10 seconds between attempts. Each attempt runs every command in the stage from the beginning.

Retry only steps that are safe to repeat. A script that is not idempotent should not be retried blindly.

## Optional stages: `optional`

`optional` is a boolean. The default is `false`.

```toml
[api]
lint = { run = "make lint", optional = true }
```

An optional stage that fails does not fail the run, and its dependents still run. The failure is still recorded in the logs and in the summary as `FAILED`.

## Failure handling: `on_fail`

`on_fail` lists commands to run after the final failed attempt. It accepts a string or a list of strings.

```toml
[api]
deploy = { run = "./deploy.sh", retries = 1, on_fail = "./rollback.sh" }
```

Use `on_fail` for cleanup or for undoing partial work. It does not change the stage's status. The stage is still `FAILED`, and the `on_fail` commands do not affect whether later stages run. To react to a failure with a separate stage, use `when = "failure"`. See [Dependencies and conditions](dependencies-and-conditions.md#roll-back-on-failure).

`on_fail` runs with the same environment, directory and timeout as the stage.

## Parallelism

Stages that do not depend on each other run in parallel. The server limits how many run at once with `RODEO_MAX_PARALLEL_STAGES`, which defaults to 4. Runs are processed one at a time, so the limit applies to the stages of the current run. See [Configuration](../configuration.md#pipeline-execution).

## Summary of execution options

| Option | Default | Scope |
|---|---|---|
| `dir` | repository root | One stage |
| `timeout` | `"10m"` | Each command in the stage |
| `retries` | `0` | The whole stage, up to 5 extra attempts |
| `optional` | `false` | The stage's effect on the run outcome |
| `on_fail` | none | Runs once after the last failed attempt |
