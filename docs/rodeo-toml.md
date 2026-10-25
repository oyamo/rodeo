# rodeo.toml

A repository adds a file named `rodeo.toml` at its root to tell rodeo which commands to run on each push. rodeo reads the file from the pushed branch, so changing the file takes effect on the next push to that branch.

## Format

```toml
branches = ["main"]

commands = [
  "mvn -B test",
  "./scripts/deploy.sh",
]
```

| Key | Type | Required | Description |
|---|---|---|---|
| `branches` | array of strings | Yes | Branches that trigger the commands. If the list is missing or empty, nothing runs. |
| `commands` | array of strings | Yes | Shell commands to run in order, from the root of the repository. |

The file must be valid TOML. A syntax error stops the run and is written to the log.

## What happens on a push

1. rodeo receives a verified push event.
2. Tag pushes are ignored.
3. rodeo clones the branch into a new temporary directory with `git clone --depth 1 --branch <branch>`.
4. If `rodeo.toml` is missing, or the branch is not listed in it, rodeo logs the reason and stops.
5. Each command runs in order with `sh -c`, in the clone's root directory.
6. The first command that fails stops the run. The remaining commands do not run.
7. The temporary directory is deleted, whatever the outcome.

Pushes are processed one at a time, in the order they arrive. A slow run delays the runs queued behind it.

## Limits

| Limit | Value |
|---|---|
| Clone time | 5 minutes |
| Time per command | 10 minutes. A command still running after that is killed. |
| Environment | Only `PATH` and `HOME` are passed through. Other variables, including the webhook secrets, are not visible to commands. |
| Repositories | Public repositories. A private repository cannot be cloned, because rodeo sends no credentials. |
| Output | Written to rodeo's standard output. Redirect it to a file if you need to keep it. |

## Logs

rodeo logs each step. The messages you will see most often:

| Message | Meaning |
|---|---|
| `Running '<command>' in <directory>` | A command is starting. |
| `No rodeo.toml in <repo>; nothing to run` | The clone has no `rodeo.toml` at its root. |
| `Branch <name> is not listed in rodeo.toml for <repo>` | The push was to a branch that is not in `branches`. |
| `Command exited with <code>: <command>` | A command failed, and the remaining commands were skipped. |
| `Command timed out after ...` | A command was killed for running too long. |
| `Commands failed for <repo> on branch <name>` | The clone or the file failed. The log entry includes the cause. |

## Security

Commands run on the machine that runs rodeo, with that machine's permissions. Anyone who can push to a listed branch controls what runs on the server. Only enable repositories whose contributors you trust with that access.

Keep server secrets out of reach of the commands you run. The webhook secrets are not passed to commands, but anything else the service user can read is.

## Requirements on the server

- `git` on the `PATH`.
- `sh` on the `PATH`. On Windows, use Git Bash, see [Windows](installation/windows.md).
- Every tool your commands call, such as `mvn`, `node`, or `docker`, installed on the server and on the `PATH` of the rodeo service.

Docker users: the image contains only `git` and `sh`. Add the tools you need, see [Docker](installation/docker.md#commands-in-rodeotoml).

For an end-to-end example, see [Deploy on push](deployment.md).
