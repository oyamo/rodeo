# rodeo.toml

`rodeo.toml` is the file that tells rodeo what to do when code is pushed. It lives at the root of a repository and is read from the commit that was pushed, so a change to the file takes effect on the next push.

This section documents the file in full. Start with the overview below, then read the page for each concept you need.

## The file in one minute

```toml
on = ["main"]

[pipeline-name]
stage-name = "a command"
other-stage = { run = "a command", needs = ["stage-name"] }
```

| Element | What it is | Page |
|---|---|---|
| `on` | Branches that trigger the pipelines | [Pipelines and stages](pipelines-and-stages.md#triggers) |
| `[pipeline-name]` | A pipeline: a group of stages that run together | [Pipelines and stages](pipelines-and-stages.md#pipelines) |
| `stage = "..."` | A stage that runs one command | [Pipelines and stages](pipelines-and-stages.md#stages) |
| `stage = { run = ... }` | A stage with options | [Reference](reference.md#stage-options) |
| `needs` | What a stage waits for | [Dependencies and conditions](dependencies-and-conditions.md#dependencies) |
| `when` | When a stage runs, based on outcomes | [Dependencies and conditions](dependencies-and-conditions.md#conditions) |
| `env`, `secrets` | Variables and server secrets for commands | [Environment and secrets](environment-and-secrets.md) |
| `dir`, `timeout`, `retries`, `optional`, `on_fail` | How a stage executes | [Execution controls](execution.md) |

## How a push is processed

1. rodeo receives a push and clones the pushed branch into a temporary directory.
2. It reads `rodeo.toml` from the clone. A missing file ends the run with nothing to do.
3. It selects the pipelines whose `on` list includes the branch.
4. It validates the whole file. Any error stops the run before a stage starts. See [Validation and errors](validation.md).
5. Stages run as their dependencies complete. Independent stages run in parallel, up to the server's limit.
6. The run finishes with a summary table. See [Logs](../logs.md).

## Requirements

Stage commands run on the server that runs rodeo. The server needs Git and `sh` on its `PATH`, plus every tool your stages call. In the Docker image, only Git and `sh` are included. See [Server requirements](#server-requirements) below.

## Server requirements

| Requirement | Why |
|---|---|
| Git | Cloning the repository |
| `sh` | Running stage commands. On Windows, use Git Bash. |
| Every tool your stages call | Such as `mvn`, `npm` or `docker`. They must be on the `PATH` of the rodeo process. |

In the Docker image, extend the base image to add tools. See [Docker](../installation/docker.md#add-tools-for-stage-commands).

## Security

Stage commands run on the machine that runs rodeo, with that machine's permissions. Anyone who can push to a branch listed in `on` controls what runs on the server. Enable a repository only if you trust its contributors with that access.

Three rules reduce the exposure:

- Secrets are never passed to a stage unless the stage asks for them with `secrets`, and the server allows that name. See [Environment and secrets](environment-and-secrets.md).
- Only `PATH`, `HOME`, and the variables the file sets reach a command. The server's own environment is not inherited.
- A file that names a secret the server does not allow is rejected before anything runs.

## Next steps

- [Pipelines and stages](pipelines-and-stages.md): the structure of the file
- [Examples](../examples.md): five complete files, from simple to complex
- [Reference](reference.md): every key in one table
