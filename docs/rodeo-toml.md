# rodeo.toml reference

`rodeo.toml` defines what rodeo does when a branch receives a push. It lives at the root of the repository and is read from the pushed commit.

## Structure

A file has two kinds of top-level entries:

- **Branch triggers.** The key `on`, a list of branch names.
- **Pipelines.** Each top-level table is a pipeline. Each key inside a pipeline is a stage.

```toml
on = ["main"]

[pipeline-name]
stage-name = "command"
```

Pipelines run side by side. Stages inside a pipeline run in the order they are written, unless you declare otherwise.

## Example

A web application with a frontend and a backend that deploy concurrently. The frontend deploy waits for the backend to be live.

```toml
on = ["main"]

[frontend]
build  = { run = "npm ci && npm run build", label = "Build assets" }
test   = { run = "npm test", label = "Unit tests" }
deploy = { run = "./scripts/deploy-frontend.sh", needs = ["test", "backend.deploy"], env = { STAGE = "prod" } }

[backend]
build  = "mvn -B package"
deploy = { run = "./scripts/deploy-backend.sh", needs = ["build"], secrets = ["DEPLOY_TOKEN"] }
```

With this file, the run proceeds as follows:

1. `frontend/build` and `backend/build` start together.
2. `frontend/test` starts when `frontend/build` succeeds. `backend/deploy` starts when `backend/build` succeeds.
3. `frontend/deploy` starts when `frontend/test` and `backend/deploy` have both succeeded.

## Pipelines

A pipeline is a top-level table. Its name may contain letters, digits, `-` and `_`.

| Key | Type | Description |
|---|---|---|
| `on` | list of strings | Branches that trigger this pipeline. Overrides the top-level `on`. |
| `env` | table | Variables applied to every stage in the pipeline |

Any other key in a pipeline table is a stage. The keys `on` and `env` are reserved.

A pipeline runs only when the pushed branch is in its `on` list. Pipelines that do not match the branch are ignored for that push.

## Stages

A stage can take one of three forms.

**A command**

```toml
build = "make"
```

**Several commands, run in order**

```toml
build = ["npm ci", "npm run build"]
```

**A table, when you need options**

```toml
deploy = { run = "./deploy.sh", needs = ["build"], timeout = "15m" }
```

### Stage options

| Option | Type | Default | Description |
|---|---|---|---|
| `run` | string or list of strings | required | Commands to run. Each command runs through `sh -c` in the stage directory. |
| `label` | string | the stage name | Display name used in logs and the run summary. 1 to 60 characters. |
| `needs` | list of strings | the stage above, or none for the first stage | Stages that must finish before this one starts. See [Dependencies](#dependencies). |
| `when` | string | `"success"` | When the stage runs. See [Conditions](#conditions). |
| `dir` | string | repository root | Directory to run in, relative to the repository. Must not leave the repository. |
| `env` | table | none | Variables for this stage. Merged with the pipeline's `env`. |
| `secrets` | list of strings | none | Server secrets to expose to this stage. Each name must be on the server's allowlist. |
| `timeout` | string | `"10m"` | Time limit for each command. Accepts `s`, `m` or `h`, such as `"30s"`, `"15m"` or `"2h"`. |
| `retries` | integer | `0` | Additional attempts after a failure. Maximum 5. |
| `optional` | boolean | `false` | When `true`, a failure does not fail the run and does not block dependent stages. |
| `on_fail` | string or list of strings | none | Commands to run after the final failed attempt. Their result does not change the stage status. |

## Dependencies

`needs` lists the stages that must finish before a stage starts.

- **Default.** A stage without `needs` depends on the stage directly above it in the same pipeline. The first stage in a pipeline has no dependencies.
- **Start immediately.** `needs = []` makes a stage start as soon as the run begins, with no dependencies.
- **Within a pipeline.** Refer to a stage by name, for example `needs = ["build"]`.
- **Across pipelines.** Refer to a stage with a dot, for example `needs = ["backend.deploy"]`. This means the `deploy` stage in the `backend` pipeline.

Dependencies form a directed graph. rodeo rejects a file whose graph contains a cycle, and rejects a `needs` entry that names an unknown stage.

If a dependency belongs to a pipeline that was not triggered by the push, the dependent stage is skipped.

## Conditions

`when` controls whether a stage runs, based on the outcome of its dependencies.

| Value | Runs when |
|---|---|
| `"success"` | Every dependency succeeded. Optional dependencies that failed count as successful. This is the default. |
| `"failure"` | At least one dependency failed. Use it for rollback and alert stages. |
| `"always"` | The dependencies have finished, whatever their outcome. Use it for cleanup. |

A stage that does not meet its condition is skipped. Stages that depend on a skipped stage are skipped too, unless they use `"always"`.

## Labels

Labels make logs and the run summary readable. The label does not affect scheduling.

```toml
[frontend]
deploy = { run = "./deploy.sh", label = "Publish to CDN" }
```

## Environment

A command receives only `PATH` and `HOME`, the variables set in `env` at the pipeline and stage level, and the following values:

| Variable | Value |
|---|---|
| `RODEO_PIPELINE` | Name of the pipeline |
| `RODEO_STAGE` | Name of the stage |
| `RODEO_REPOSITORY` | Repository full name, such as `octo/hello` |
| `RODEO_BRANCH` | Branch that received the push |
| `RODEO_COMMIT` | Commit SHA at the head of the branch |

Server secrets are not visible to commands unless the stage lists them in `secrets`. A secret that the server does not allow is rejected when the file is parsed, before any stage starts. A secret that is on the allowlist but not set on the server fails only the stage that requests it.

## Working directory

Each stage runs in the repository root unless `dir` is set. Use `dir` for monorepos:

```toml
[frontend]
build = { run = "npm ci && npm run build", dir = "apps/web" }
```

## Retries and timeouts

`timeout` applies to each command separately. A command that exceeds it is stopped and counts as a failure.

With `retries = 2`, a failing stage runs up to three times in total. rodeo waits 10 seconds between attempts.

Once the final attempt fails, `on_fail` runs, and the stage is marked failed.

```toml
[backend]
deploy = { run = "./deploy.sh", retries = 2, timeout = "5m", on_fail = "./scripts/rollback.sh" }
```

## Optional stages

A stage marked `optional` can fail without failing the run. Its dependents still run.

```toml
[backend]
lint = { run = "mvn -B checkstyle:check", optional = true }
test = { run = "mvn -B test", needs = [] }
```

## Validation

rodeo checks the whole file before running anything. A file is rejected when:

- it is not valid TOML
- a top-level key is neither `on` nor a pipeline table
- a pipeline or stage name contains characters outside letters, digits, `-` and `_`
- a stage has no `run`, or its `run` is empty
- a `needs` entry names an unknown stage
- the dependencies contain a cycle
- a `dir` is absolute or leaves the repository
- a `secrets` entry is not on the server's allowlist
- a `timeout` is not in the form `30s`, `15m` or `2h`
- `retries` is outside 0 to 5
- `when` is not `success`, `failure` or `always`

A rejected file aborts the run before any stage starts. The log contains an `aborted` line that names the problem, for example `run 3f9c1a2e aborted: ... needs unknown stage frontend/tset`.

## Legacy format

A file with top-level `branches` and `commands` still works. It runs as one pipeline with one stage, and is equivalent to:

```toml
on = ["main"]

[default]
default = ["mvn -B test", "./scripts/deploy.sh"]
```

New files should use the pipeline format.

## Security

Commands run on the machine that runs rodeo, with that machine's permissions. Anyone who can push to a listed branch controls what runs on the server. Enable a repository only if you trust its contributors with that access.

Secrets are never written to the log. Command output is logged as-is, so avoid printing secret values in your scripts.

## Logs

Every run produces log lines that show its progress. See [Logs](logs.md) for the format and an annotated example.
