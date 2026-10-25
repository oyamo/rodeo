# Dependencies and conditions

This page explains how stages wait for each other, and how `when` decides whether a stage runs based on those outcomes.

## Dependencies

`needs` lists the stages that must finish before a stage starts.

| Form | Meaning |
|---|---|
| Omitted | The stage depends on the stage directly above it in the same pipeline. The first stage has no dependencies. |
| `needs = []` | The stage has no dependencies and starts as soon as the run begins. |
| `needs = ["build"]` | The stage depends on `build` in the same pipeline. |
| `needs = ["backend.deploy"]` | The stage depends on `deploy` in the `backend` pipeline. |

Within a pipeline, a name refers to a stage in that pipeline. Across pipelines, the dot form names a stage in another pipeline. A dependency may be on any stage in the file.

### Example

```toml
[backend]
build  = "mvn -B package"
test   = "mvn -B verify"
deploy = { run = "./deploy-api.sh", needs = ["build", "test"] }

[frontend]
build  = "npm run build"
deploy = { run = "./deploy-web.sh", needs = ["build", "backend.deploy"] }
```

`backend/build` and `frontend/build` start together. `backend/test` waits for `backend/build`. `backend/deploy` waits for both backend stages. `frontend/deploy` waits for `frontend/build` and for `backend/deploy`, so the web app is released only after the API is live.

### Rules

- A stage can list any number of dependencies.
- A dependency that names a stage that does not exist is rejected. See [Validation and errors](validation.md#unknown-dependency).
- Dependencies may not form a cycle. A cycle is rejected. See [Validation and errors](validation.md#dependency-cycle).
- A dependency on a stage in a pipeline that was not triggered by this push is treated as **skipped**, so the dependent stage is skipped too unless its condition is `"always"`.

## Conditions

`when` decides whether a stage runs, based on the outcome of its dependencies. The default is `"success"`.

| Value | The stage runs when |
|---|---|
| `"success"` | Every dependency succeeded. A dependency marked `optional` that failed counts as successful. |
| `"failure"` | At least one dependency failed. |
| `"always"` | Every dependency has finished, whatever its outcome. |

A stage that does not meet its condition is **skipped**. Skipped stages count as not succeeded, so dependents with the default condition are skipped too.

### Outcome table

The table shows what happens to a stage depending on the outcome of its dependency.

| Dependency outcome | `success` | `failure` | `always` |
|---|---|---|---|
| Succeeded | runs | skipped | runs |
| Failed | skipped | runs | runs |
| Failed, marked `optional` | runs | runs | runs |
| Skipped | skipped | skipped | runs |

## Common patterns

### Gate a deploy on tests

```toml
[app]
test   = "mvn -B test"
deploy = { run = "./deploy.sh", needs = ["test"] }
```

The deploy runs only when the tests pass.

### Roll back on failure

```toml
[app]
deploy   = { run = "./deploy.sh", needs = [] }
rollback = { run = "./rollback.sh", needs = ["deploy"], when = "failure" }
```

The rollback runs only when the deploy fails.

### Always clean up

```toml
[app]
build   = "make"
cleanup = { run = "make clean", needs = ["build"], when = "always" }
```

The cleanup runs after the build, whether it succeeded or failed.

### Alert on any failure

```toml
[alerts]
notify = { run = "./notify.sh", needs = ["eu.deploy", "us.deploy"], when = "failure", secrets = ["SLACK_TOKEN"] }
```

`notify` runs if either deploy fails. A deploy that was skipped does not count as a failure.

### Run a non-critical check

```toml
[app]
build = "make"
lint  = { run = "make lint", needs = ["build"], optional = true }
test  = { run = "make test", needs = ["build"] }
```

A failing `lint` is recorded in the summary but does not fail the run, and `test` still runs.

## Run outcome

The run succeeds when no stage failed, unless the failed stage is marked `optional`. A skipped stage does not fail the run.
