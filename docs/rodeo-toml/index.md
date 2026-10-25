# rodeo.toml

`rodeo.toml` is the file that tells rodeo what to do when code is pushed. It lives at the root of a repository and is read from the commit that was pushed, so a change to the file takes effect on the next push.

This section documents the file in full. Start with the overview below, then read the page for each concept you need.

## The file in one minute

```toml
on = ["main"]

[pipeline-name]
stage-name = "a command"
other-stage = {
  run = "a command",
  needs = ["stage-name"],
}
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

rodeo needs Git on the server to clone repositories. Stages run in containers, so the server needs a container runtime, such as Docker, and the images your stages use. A stage that runs on the server needs the tools it calls on the server's `PATH`. See [Execution controls](execution.md#where-a-stage-runs).

## Security

Every stage runs in a container by default, with no network, no access to the server's files, and fixed CPU and memory limits. A stage that runs on the server is possible only when the operator allows it. The complete list of guarantees is in [Execution controls](execution.md#what-a-container-gets).

Three things still matter:

- **Secrets** reach a stage only when the stage requests them and the server allows the name. See [Environment and secrets](environment-and-secrets.md).
- **Container access is powerful.** The service user must be able to run containers. Membership of the `docker` group is equivalent to root on the server. Use rootless Docker, or a dedicated runner host, to avoid that.
- **Anyone who can push to a branch in `on`** controls which commands run. Limit who can push, and review changes to `rodeo.toml` and the scripts it calls.

## Next steps

- [Pipelines and stages](pipelines-and-stages.md): the structure of the file
- [Examples](../examples.md): five complete files, from simple to complex
- [Reference](reference.md): every key in one table
