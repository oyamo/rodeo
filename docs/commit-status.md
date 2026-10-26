# Commit status reporting

rodeo can report the result of each pipeline to GitHub. The result appears as a status check on the pushed commit: a pending indicator while the pipeline runs, then a tick for success or a cross for failure. The same status appears on pull requests that include the commit, and you can require it before a merge.

## What is reported

For each pipeline that runs for a push, rodeo posts two statuses to the pushed commit:

| When | Context | State | Description |
|---|---|---|---|
| The run starts | `rodeo/<pipeline>` | pending | `Run <id> started` |
| Every stage has finished | `rodeo/<pipeline>` | success | `All stages succeeded` |
| Every stage has finished | `rodeo/<pipeline>` | failure | `Failed: <label of the first failed stage>` |

The context is the pipeline name, so the file `[frontend]` and `[backend]` produce `rodeo/frontend` and `rodeo/backend`. GitHub shows each one as a separate check.

A pipeline fails if any of its stages failed and is not marked `optional`. Skipped stages do not cause a failure. The description names the first failed stage, using its label.

If the run cannot start, for example because `rodeo.toml` is invalid or the clone fails, rodeo posts a single failure under the context `rodeo/run`:

```text
rodeo/run  failure  Run 3f9c1a2e aborted: app/deploy needs unknown stage app/tset
```

Descriptions are limited to 140 characters.

Pushes to a branch that no pipeline triggers, and repositories without a `rodeo.toml`, produce no statuses.

## Setup

1. Create a fine-grained personal access token at **GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens**.
2. Under **Repository access**, select the repositories rodeo should report on.
3. Under **Permissions**, set **Commit statuses** to **Read and write**.
4. Set the token as `token` under `[github]` in the server's configuration file, and restart rodeo.

```text
[github]
token = "github_pat_..."
```

Without the token, rodeo runs as before and posts nothing. The startup log says so:

```text
GitHub commit statuses are disabled; set github.token to enable them
```

## Requiring a check before merge

Once statuses are posted, you can make them required:

1. Open **Settings → Branches → Branch protection rules** for the branch.
2. Enable **Require status checks to pass before merging**.
3. Search for `rodeo/` and select the pipeline checks you need.

A pull request then cannot merge until each selected pipeline has passed on its latest commit.

## Delivery and failures

rodeo tries each status update up to three times. It retries on network errors, HTTP 5xx and HTTP 429, with a two-second pause between attempts. It does not retry other client errors, such as HTTP 422, because retrying them cannot succeed. Failures are logged:

```text
WARN  status rodeo/app attempt 1 of 3 failed; retrying
ERROR status rodeo/app could not be set on abc1234 after 3 attempts
```

A status that could not be delivered is not sent again later.

## Limitations

- **GitHub only.** GitLab commit statuses are not reported yet.
- **No link to logs.** The status does not link to the run log, because rodeo does not publish logs.
- **Crash leaves pending.** If rodeo stops during a run, pipelines that were already marked pending stay pending. Push the commit again, or set the status by hand.
- **Personal token.** The token is tied to the account that created it. If that account leaves the project, create a new token and update `token`.
