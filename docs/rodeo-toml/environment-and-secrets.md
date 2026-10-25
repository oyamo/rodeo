# Environment and secrets

Commands in a stage see a controlled environment. This page lists exactly what a command receives, in what order values are applied, and how server secrets reach a stage.

## What a command receives

A command does not inherit the server's environment. It receives only:

1. `PATH` and `HOME` from the rodeo process
2. The variables set with `env` at the pipeline level
3. The variables set with `env` at the stage level, which override pipeline values
4. The secrets the stage requests with `secrets`
5. The `RODEO_*` variables described below, which take precedence over everything above

If two sources set the same name, the later one wins.

## Pipeline and stage variables

Use `env` to set variables. It is a table of strings.

```toml
[api]
env = { JAVA_OPTS = "-Xmx512m", TZ = "UTC" }
build = "mvn -B package"
deploy = { run = "./deploy.sh", env = { STAGE = "production" } }
```

The `deploy` stage receives `JAVA_OPTS`, `TZ` and `STAGE`. The `build` stage receives `JAVA_OPTS` and `TZ`.

Values must be strings. Use names that are valid for your shell, such as `JAVA_OPTS`. A name that contains a character the shell does not accept will simply not be usable from scripts.

## rodeo variables

These variables are set for every command.

| Variable | Value |
|---|---|
| `RODEO_PIPELINE` | The pipeline name, such as `api` |
| `RODEO_STAGE` | The stage name, such as `deploy` |
| `RODEO_REPOSITORY` | The repository full name, such as `octo/hello` |
| `RODEO_BRANCH` | The branch that received the push |
| `RODEO_COMMIT` | The commit SHA at the head of the branch |

Use them in scripts to tag artifacts, record deployments, or name logs:

```toml
[api]
deploy = { run = "./deploy.sh \"$RODEO_COMMIT\"", label = "Deploy release" }
```

## Secrets

Secrets are values stored on the server. The repository only asks for them by name.

```toml
[api]
deploy = { run = "./deploy.sh", secrets = ["DEPLOY_TOKEN"] }
```

A secret reaches a stage only when all three conditions hold:

1. The stage lists the secret in `secrets`.
2. The server lists the name in `RODEO_ALLOWED_SECRETS`.
3. The rodeo process has a variable with that name set.

Each condition has a different failure:

| Failure | Where it appears | Effect |
|---|---|---|
| The name is not in `RODEO_ALLOWED_SECRETS` | When the file is parsed | The whole run is aborted before any stage starts. |
| The variable is not set on the server | When the stage starts | That stage fails with `secret X is not set on this server`. Other stages are unaffected. |

Secrets are given only to the stages that request them. A secret listed in one stage is not visible to another.

### Configuring the server allowlist

```text
RODEO_ALLOWED_SECRETS=DEPLOY_TOKEN,REGISTRY_PASSWORD
```

See [Configuration](../configuration.md#pipeline-execution) for how to set it.

## Logging and secrets

rodeo does not print the value of a secret. Command output is logged as-is, so a script that echoes a secret will expose it in the logs. Keep secrets out of command lines, because they can appear in logs and process listings. Pass them through the environment, as `secrets` does.

## Repository content

Environment variables do not change which files a stage can read. A stage that runs in the clone can read any file in the repository, including files that are committed to it. Keep credentials out of the repository.
