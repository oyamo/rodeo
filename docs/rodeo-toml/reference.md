# Reference

Every key rodeo reads from `rodeo.toml`, in one place. Each entry links to the page that explains it.

## Top-level keys

| Key | Type | Required | Default | Description |
|---|---|---|---|---|
| `on` | array of strings | no | none | Branches that trigger pipelines which do not set their own `on`. See [Triggers](pipelines-and-stages.md#triggers). |
| `<pipeline>` | table | yes, at least one | none | A pipeline. The table name is the pipeline name. See [Pipelines](pipelines-and-stages.md#pipelines). |

Legacy keys are accepted for compatibility. See [Legacy format](#legacy-format).

## Pipeline keys

Keys inside a pipeline table. Every other key is a stage.

| Key | Type | Required | Default | Description |
|---|---|---|---|---|
| `on` | array of strings | no | the top-level `on` | Branches that trigger this pipeline |
| `env` | table of strings | no | none | Variables for every stage in the pipeline. See [Environment](environment-and-secrets.md#pipeline-and-stage-variables). |
| `<stage>` | string, array, or table | yes, at least one | none | A stage. See [Stage forms](pipelines-and-stages.md#stages). |

## Stage forms

| Form | Example | Equivalent to |
|---|---|---|
| String | `build = "make"` | `build = { run = "make" }` |
| Array | `build = ["make", "make test"]` | `build = { run = ["make", "make test"] }` |
| Table | `build = { run = "make", timeout = "5m" }` | Full form |

## Stage options

Valid only inside a stage table. An unknown option is an error. See [Validation](validation.md#has-unknown-option).

| Option | Type | Default | Description |
|---|---|---|---|
| `run` | string or array of strings | required | Commands to run in order. Each runs with `sh -c`. |
| `label` | string, 1 to 60 characters | the stage name | Display name in logs and the summary. See [Labels](pipelines-and-stages.md#labels). |
| `needs` | array of strings | the stage above, or none for the first stage | Stages that must finish first. `needs = []` means no dependencies. See [Dependencies](dependencies-and-conditions.md#dependencies). |
| `when` | `"success"`, `"failure"` or `"always"` | `"success"` | When the stage runs. See [Conditions](dependencies-and-conditions.md#conditions). |
| `dir` | string | repository root | Working directory, relative to the repository. See [Working directory](execution.md#working-directory-dir). |
| `env` | table of strings | none | Variables for this stage, merged over the pipeline's `env`. See [Environment](environment-and-secrets.md#pipeline-and-stage-variables). |
| `secrets` | array of strings | none | Server secrets to expose. Each name must be in `RODEO_ALLOWED_SECRETS`. See [Secrets](environment-and-secrets.md#secrets). |
| `timeout` | string | `"10m"` | Limit for each command: `s`, `m` or `h`, such as `"30s"`. See [Timeouts](execution.md#timeouts-timeout). |
| `retries` | integer, 0 to 5 | `0` | Extra attempts after a failure. See [Retries](execution.md#retries-retries). |
| `optional` | boolean | `false` | A failure does not fail the run. See [Optional stages](execution.md#optional-stages-optional). |
| `on_fail` | string or array of strings | none | Commands run after the final failed attempt. See [Failure handling](execution.md#failure-handling-on_fail). |

## Stage IDs

A stage ID is `pipeline/stage`. In `needs`, write:

| Written as | Resolves to |
|---|---|
| `build` (inside `app`) | `app/build` |
| `backend.deploy` | `backend/deploy` |

Names may contain letters, digits, `-` and `_`. A dot is reserved for cross-pipeline references.

## Condition values

| Value | Runs when |
|---|---|
| `"success"` | Every dependency succeeded, or failed while marked `optional` |
| `"failure"` | At least one dependency failed |
| `"always"` | Every dependency has finished |

## Stage statuses

| Status | Meaning |
|---|---|
| `SUCCEEDED` | Every command exited 0 |
| `FAILED` | A command exited non-zero or timed out, after the final attempt |
| `SKIPPED` | The condition was not met, so the stage did not run |

## Environment variables seen by commands

| Variable | Set by | Value |
|---|---|---|
| `PATH`, `HOME` | rodeo, from its own environment | Passed through |
| Pipeline and stage `env` keys | the file | As written |
| Names in `secrets` | the server | Value from the server's environment |
| `RODEO_PIPELINE` | rodeo | Pipeline name |
| `RODEO_STAGE` | rodeo | Stage name |
| `RODEO_REPOSITORY` | rodeo | Repository full name |
| `RODEO_BRANCH` | rodeo | Pushed branch |
| `RODEO_COMMIT` | rodeo | Commit SHA at the branch head |

Later rows take precedence over earlier ones when names collide.

## Server settings that affect the file

| Setting | Effect on `rodeo.toml` |
|---|---|
| `RODEO_ALLOWED_SECRETS` | Which names stages may request in `secrets` |
| `RODEO_MAX_PARALLEL_STAGES` | How many stages run at once |

See [Configuration](../configuration.md).

## Limits

| Limit | Value |
|---|---|
| Clone | 5 minutes |
| Timeout per command | Set with `timeout`, default 10 minutes |
| Retries | 0 to 5 |
| Retry delay | 10 seconds |
| Label length | 1 to 60 characters |
| Parallel stages | `RODEO_MAX_PARALLEL_STAGES`, default 4 |

## Legacy format

The original format still works. It runs as one pipeline named `default` with one stage named `default`.

```toml
branches = ["main"]
commands = ["mvn -B test", "./deploy.sh"]
```

`branches` is an alias for the top-level `on`. Its equivalent in the current format is:

```toml
on = ["main"]

[default]
default = ["mvn -B test", "./deploy.sh"]
```

New files should use the pipeline format.

## Complete example

Every key in one file. This is for reference, not a recommendation to use all of them together.

```toml
on = ["main"]

[backend]
env = { JAVA_OPTS = "-Xmx512m" }

build = { run = ["mvn -B -q package"], label = "Build API", dir = "services/api", timeout = "15m" }
test = { run = "mvn -B verify", label = "Test API", dir = "services/api", needs = ["build"] }
lint = { run = "make lint", label = "Lint", needs = [], optional = true }
deploy = { run = "./deploy.sh \"$RODEO_COMMIT\"", label = "Deploy API", needs = ["test"], secrets = ["DEPLOY_TOKEN"], env = { STAGE = "prod" }, retries = 2, timeout = "5m", on_fail = "./rollback.sh" }
notify = { run = "./notify.sh failed", label = "Alert", needs = ["deploy"], when = "failure" }
```
