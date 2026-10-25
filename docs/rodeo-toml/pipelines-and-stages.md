# Pipelines and stages

A `rodeo.toml` file is made of pipelines, and each pipeline is made of stages. This page covers how to declare both, and the three ways to write a stage.

## Triggers

The top-level key `on` lists the branches that trigger pipelines by default.

```toml
on = ["main", "release"]
```

- Matching is exact. `main` matches only a branch named `main`. Wildcards are not supported.
- Tag pushes are ignored.
- A pipeline can set its own `on` list. Its list replaces the top-level one for that pipeline only.

```toml
on = ["main"]

[docs]
on = ["gh-pages"]
publish = "make publish"
```

Here `docs` runs only for pushes to `gh-pages`, and every other pipeline in the file runs for pushes to `main`.

!!! warning "Use `on` inside a pipeline"
    The key inside a pipeline is `on`. The key `branches` is accepted only at the top level, as a legacy alias for `on`. Inside a pipeline, `branches` would be read as a stage.

## Pipelines

A pipeline is a top-level table. Its name becomes the first part of every stage ID, such as `frontend/build`.

```toml
[frontend]
build = "npm run build"

[backend]
build = "mvn -B package"
```

| Rule | Detail |
|---|---|
| Name | Letters, digits, `-` and `_` only. A dot is not allowed, because `.` separates pipeline from stage in references. |
| Uniqueness | Each pipeline name appears once in the file. |
| Contents | Every key other than the reserved `on` and `env` is a stage. |
| Empty pipelines | A pipeline with no stages is rejected. |

### Pipeline-level options

| Key | Type | Effect |
|---|---|---|
| `on` | list of strings | Branches that trigger this pipeline |
| `env` | table | Variables inherited by every stage in the pipeline. See [Environment and secrets](environment-and-secrets.md#pipeline-and-stage-variables). |

!!! warning "Keys inside a pipeline are stages"
    Any key that is not `on` or `env` is treated as a stage. A pipeline-level `label = "Frontend"` is read as a stage named `label` whose command is `Frontend`. Set labels on stages, not on pipelines.

## Stages

A stage is a key inside a pipeline. It takes one of three forms.

### Form 1: a command

```toml
[site]
build = "make"
```

The command runs through `sh -c`. Shell syntax such as `&&`, pipes and redirection works.

### Form 2: a list of commands

```toml
[site]
build = ["npm ci", "npm run build"]
```

The commands run in order. The first command that fails ends the stage, and the remaining commands do not run.

### Form 3: a table

```toml
[site]
deploy = { run = "./deploy.sh", label = "Publish to CDN", needs = ["build"], timeout = "15m" }
```

Use a table when a stage needs options. The full list of options is in the [reference](reference.md#stage-options). The required key is `run`, and its value can be a string or a list of strings.

## Stage order

Stages inside a pipeline run in declaration order unless `needs` says otherwise.

```toml
[site]
build  = "make"         # runs first
test   = "make test"    # waits for build, the stage above
deploy = "make deploy"  # waits for test, the stage above
```

To start a stage immediately, give it an empty `needs` list:

```toml
[site]
build = "make"
lint  = { run = "make lint", needs = [] }   # runs in parallel with build
```

The full rules are in [Dependencies and conditions](dependencies-and-conditions.md).

## Labels

A label is a display name used in the logs and the run summary. It does not change behavior.

```toml
[site]
deploy = { run = "./deploy.sh", label = "Publish to CDN" }
```

Without a label, the stage name is shown. A label can be 1 to 60 characters.

## Naming

| Element | Allowed characters | Notes |
|---|---|---|
| Pipeline | letters, digits, `-`, `_` | Cannot contain a dot |
| Stage | letters, digits, `-`, `_` | Cannot contain a dot |
| Label | any text | 1 to 60 characters |

Stage IDs are formed from the pipeline and stage names: `pipeline/stage`. The ID appears in the logs, in the summary, and in `needs` references from other pipelines.
