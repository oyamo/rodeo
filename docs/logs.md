# Logs

rodeo writes its progress to standard output as it runs. Every line that belongs to a run carries the run's identifier, and every stage line also carries the stage name. Output from parallel stages stays readable, and a run can be followed or filtered by ID.

## Line format

Each line has a timestamp, a level, and a message.

```text
14:02:11.482 INFO  run 3f9c1a2e received GITHUB push to acme/app on main
```

| Part | Meaning |
|---|---|
| `14:02:11.482` | Time of day, to the millisecond |
| `INFO` | Level: `INFO`, `WARN` or `ERROR` |
| `run 3f9c1a2e` | The run identifier, an 8-character value unique to the push |
| `[3f9c1a2e frontend/build]` | Run identifier and stage ID, on stage lines |

Stage lines use the form `[run-id stage-id]`. Command output lines begin with `|`.

## Run lifecycle

A run moves through these messages in order.

| Message | Level | Meaning |
|---|---|---|
| `run <id> received <provider> push to <repo> on <branch>` | INFO | A verified push arrived and the run has started |
| `run <id> starting <n> stage(s)` | INFO | The file was parsed and the stages were selected |
| `run <id> no rodeo.toml in repository; nothing to run` | INFO | The repository has no `rodeo.toml` |
| `run <id> no pipeline is triggered by branch <branch>` | INFO | The file has no pipeline for this branch |
| `run <id> SUCCEEDED in <time>` | INFO | Every stage succeeded |
| `run <id> FAILED in <time>` | INFO | At least one stage failed without `optional` |
| `run <id> aborted for <repo> on <branch>: <reason>` | ERROR | The run stopped before any stage ran, such as a clone failure or an invalid `rodeo.toml`. See [Validation and errors](rodeo-toml/validation.md). |

## Stage lifecycle

Each stage moves through these messages.

| Message | Level | Meaning |
|---|---|---|
| `[id] STARTED  <label>` | INFO | The stage began running |
| `[id] step 2/3 $ <command>` | INFO | Command 2 of 3 is running |
| `[id] \| <output>` | INFO | A line of command output |
| `[id] RETRY 1 of 2 in 10.0s` | WARN | A failed stage is being retried |
| `[id] attempt 1 failed: <reason>` | WARN | An attempt failed and another will follow |
| `[id] running on_fail commands` | WARN | The stage failed and its `on_fail` commands are running |
| `[id] SUCCEEDED in <time>` | INFO | The stage succeeded |
| `[id] FAILED in <time>: <reason>` | ERROR | The stage failed after its last attempt |
| `[id] FAILED: <reason>` | ERROR | The stage failed before any command ran, for example because a secret was missing |

Reasons name the failing step, such as `step 2/3 exited with 1` or `step 1/1 timed out after 10.0s`.

Stages that did not run appear in the summary as `SKIPPED`. They produce no lifecycle messages.

## Run summary

At the end of a run, rodeo prints a table with one row per stage.

```text
14:02:19.113 INFO  run 3f9c1a2e SUCCEEDED in 7.6s
14:02:19.113 INFO  run 3f9c1a2e STAGE                      LABEL                    STATUS     TIME
14:02:19.114 INFO  run 3f9c1a2e backend/build              build                    SUCCEEDED  3.1s
14:02:19.114 INFO  run 3f9c1a2e backend/deploy             deploy                   SUCCEEDED  2.4s
14:02:19.114 INFO  run 3f9c1a2e frontend/build             Build assets             SUCCEEDED  4.0s
14:02:19.114 INFO  run 3f9c1a2e frontend/test              Unit tests               SUCCEEDED  1.8s
14:02:19.114 INFO  run 3f9c1a2e frontend/deploy            deploy                   SUCCEEDED  0.9s
```

The table shows each stage's status and time. A skipped stage shows `-` in the time column.

## Annotated example

A run in which `backend/deploy` fails. Its `on_fail` commands run, and the stage is marked failed.

```text
14:02:11.482 INFO  run 3f9c1a2e received GITHUB push to acme/app on main
14:02:12.011 INFO  run 3f9c1a2e starting 5 stage(s)
14:02:12.020 INFO  [3f9c1a2e backend/build] STARTED  build
14:02:12.021 INFO  [3f9c1a2e frontend/build] STARTED  Build assets
14:02:12.022 INFO  [3f9c1a2e backend/build] step 1/1 $ mvn -B -q package
14:02:12.023 INFO  [3f9c1a2e frontend/build] step 1/1 $ npm ci && npm run build
14:02:15.230 INFO  [3f9c1a2e backend/build] SUCCEEDED in 3.2s
14:02:15.231 INFO  [3f9c1a2e backend/deploy] STARTED  deploy
14:02:15.240 INFO  [3f9c1a2e backend/deploy] step 1/1 $ helm upgrade --install backend ./chart
14:02:15.512 INFO  [3f9c1a2e backend/deploy] | connecting to 10.0.4.12
14:02:17.903 INFO  [3f9c1a2e backend/deploy] | health check returned 503
14:02:17.904 WARN  [3f9c1a2e backend/deploy] running on_fail commands
14:02:18.402 INFO  [3f9c1a2e backend/deploy] | restored previous release
14:02:18.405 ERROR [3f9c1a2e backend/deploy] FAILED in 3.2s: step 1/1 exited with 1
```

## Filtering

Each run has a single identifier, so the lines for one run can be extracted with:

```bash
journalctl -u rodeo | grep 3f9c1a2e
```

To follow a single stage:

```bash
journalctl -u rodeo -f | grep '3f9c1a2e frontend/'
```

With Docker:

```bash
docker logs rodeo 2>&1 | grep 3f9c1a2e
```

## Levels

| Level | Used for |
|---|---|
| `INFO` | Normal progress: lifecycle messages, commands, and output |
| `WARN` | Something went wrong that rodeo is handling: retries, failed attempts, `on_fail` |
| `ERROR` | A stage or run failed, or rodeo could not complete an action |

Output from commands is logged at `INFO`, even when the command writes to standard error. A command that writes errors and still exits with 0 does not fail its stage.

## Notes

- Secrets are not logged by rodeo. Anything a command prints is logged, so do not echo secret values.
- Parallel stages write interleaved lines. Always filter by the stage ID when reading one stage's output.
- Repository clone output is written directly to standard output, without the run prefix.
