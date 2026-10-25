# Validation and errors

rodeo validates the whole `rodeo.toml` before it starts any stage. A file with an error is rejected, and nothing runs. This page lists every error, its cause, and how to fix it.

When a file is rejected, the log contains an `aborted` line:

```text
14:02:11.640 ERROR run 3f9c1a2e aborted for acme/app on main: app/deploy needs unknown stage app/tset
```

The run ID tells you which push it came from. The message after the colon is the error described on this page.

Stage IDs in messages take the form `pipeline/stage`.

## Syntax errors

### invalid rodeo.toml

```text
invalid rodeo.toml: <TOML parser message>
```

The file is not valid TOML. The message points to the parser's problem, usually a missing quote, a bracket, or a duplicate key.

**Fix:** check the syntax with any TOML validator, or fix the line the message names.

## File structure errors

### unknown top-level key

```text
unknown top-level key 'color'; pipelines are tables such as [frontend]
```

A key outside the table form is not `on` or `branches`, and it is not a pipeline.

**Cause:** a setting written at the top level, such as `color = "blue"`, or a pipeline written as a value instead of a table.

**Fix:** move the setting into the pipeline that needs it, or write the pipeline as `[name]`.

### no pipelines

```text
rodeo.toml defines no pipelines
```

The file contains `on` but no pipeline tables.

**Fix:** add at least one pipeline table, such as `[app]`.

### pipeline has no stages

```text
pipeline app has no stages
```

A pipeline table contains only `on` or `env`.

**Fix:** add a stage. See [Pipelines and stages](pipelines-and-stages.md).

## Stage definition errors

### must be a command, a list of commands, or a table

```text
app/build must be a command, a list of commands, or a table
```

A stage is a number, a boolean, or some other value that rodeo does not accept.

**Fix:** write the stage as a string, a list of strings, or an inline table. See [the three forms](pipelines-and-stages.md#stages).

### has no run

```text
app/build has no run
```

A stage table has no `run` key.

**Fix:** add `run = "command"`.

### run must be a string or a list of strings

```text
app/build run must be a string or a list of strings
```

The value of `run` is a number, a table, or a boolean.

**Fix:** write `run` as a string or as an array of strings.

### has an empty command list

```text
app/build has an empty command list
```

`run` is an empty array, so there is nothing to execute.

**Fix:** add at least one command, or remove the stage.

### commands must be strings

```text
app/build commands must be strings
```

A list in `run` contains a value that is not a string.

**Fix:** quote each command.

### has unknown option

```text
app/deploy has unknown option 'timout'; valid options are dir, env, label, needs, on_fail, optional, retries, run, secrets, timeout, when
```

A stage table contains a key that rodeo does not recognize. The message lists the valid keys.

**Cause:** almost always a typo. Without this check, the typo would be ignored and the default value used.

**Fix:** correct the key name. The complete list is in the [reference](reference.md#stage-options).

### name contains invalid characters

```text
pipeline name 'front.end' may only contain letters, digits, '-' and '_'
stage in pipeline app name 'build.js' may only contain letters, digits, '-' and '_'
```

A pipeline or stage name contains a character outside letters, digits, `-` and `_`. A dot is reserved for cross-pipeline references.

**Fix:** rename the element. Use `front-end` and `build-js`, for example.

## Option value errors

### label must be 1 to 60 characters

```text
app/deploy label must be 1 to 60 characters
```

The label is empty or longer than 60 characters.

**Fix:** shorten the label, or remove it to use the stage name.

### timeout must look like 30s, 10m or 2h

```text
app/deploy timeout must look like 30s, 10m or 2h
```

The value is not a number followed by `s`, `m` or `h`.

**Cause:** common mistakes are `"5 minutes"`, `"300"` (no unit), or `"1.5h"` (decimals are not supported).

**Fix:** write whole numbers with a unit, such as `"5m"` or `"90s"`.

### retries must be an integer

```text
retries must be an integer
```

The value of `retries` is not an integer.

**Fix:** write `retries = 2`, without quotes.

### retries must be between 0 and 5

```text
app/deploy retries must be between 0 and 5
```

`retries` is negative or greater than 5.

**Fix:** use a value from 0 to 5.

### optional must be true or false

```text
optional must be true or false
```

`optional` is not a boolean.

**Fix:** write `optional = true` or `optional = false`, without quotes.

### when must be success, failure or always

```text
when must be success, failure or always, got 'on-failure'
```

`when` contains an unknown value.

**Fix:** use `"success"`, `"failure"` or `"always"`. See [Conditions](dependencies-and-conditions.md#conditions).

### dir must be a relative path inside the repository

```text
app/build dir must be a relative path inside the repository
```

`dir` is absolute, or it leaves the repository through `..`.

**Fix:** write a path relative to the repository root, such as `services/api`.

### env value must be a string

```text
env.JAVA_OPTS must be a string
```

A value in an `env` table is a number, a boolean, or an array.

**Fix:** quote the value. For example, `JAVA_OPTS = "-Xmx512m"`.

### must contain only strings

```text
needs must contain only strings
```

The list in `needs`, `secrets`, `on` or another list-valued key contains a non-string item.

**Fix:** quote each item.

### must be a string

```text
label must be a string
```

A key that expects text received a different type.

**Fix:** quote the value.

## Secrets

### requests secret, which this server does not allow

```text
app/deploy requests secret ROOT_PASSWORD, which this server does not allow
```

A stage asks for a secret that is not in the server's `RODEO_ALLOWED_SECRETS`. The whole file is rejected.

**Cause:** either the repository asks for a secret it should not have, or the server allowlist is missing a name the team needs.

**Fix:** if the request is correct, add the name to `RODEO_ALLOWED_SECRETS` and restart rodeo. If it is not, remove the name from the stage. See [Environment and secrets](environment-and-secrets.md#secrets).

## Dependency errors

### unknown dependency

```text
app/deploy needs unknown stage app/tset
```

A `needs` entry names a stage that does not exist. The stage ID is shown with its pipeline.

**Cause:** a typo, or a reference to a stage in a pipeline that was renamed or removed. Cross-pipeline references use a dot, such as `backend.deploy`.

**Fix:** correct the name. Check spelling and the dot form for cross-pipeline references.

### dependency cycle

```text
dependency cycle involving app/build, app/test
```

The dependencies form a loop. The stages listed are part of the loop.

**Fix:** remove one of the `needs` entries that closes the loop. A stage cannot wait for a stage that waits for it.

## Duplicate definitions

Stage names are unique within a pipeline, and pipeline names are unique within the file. TOML itself rejects a duplicate key or table, so a duplicate is reported as a TOML error.

## Behavior that is not an error

Some mistakes are valid TOML and valid rodeo syntax, so rodeo accepts them. Check for these yourself:

- **A pipeline-level `label`** becomes a stage named `label`. Set labels on stages.
- **A pipeline-level `branches`** becomes a stage named `branches`. Use `on` inside a pipeline.
- **A pipeline-level `run`** becomes a stage named `run`, whose command is the value. Put `run` inside a stage table.
- **A stage that depends on a pipeline not triggered by the push** is skipped. No error is reported, because the rule is intentional. See [Dependencies and conditions](dependencies-and-conditions.md#dependencies).
- **A branch name that matches no `on` entry** is not an error. The push is accepted, and the log says no pipeline is triggered.

## Checking a file before you push

Parse the file locally with a TOML validator before you push, and check the names and options against the [reference](reference.md). Run the commands by hand, from the repository root, to catch missing tools before rodeo does.
