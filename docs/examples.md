# Examples

Five `rodeo.toml` files, ordered from simplest to most complex. Each one is a complete file you can copy into a repository and adapt.

| # | Scenario | Features shown |
|---|---|---|
| 1 | [Run tests on every push](#1-run-tests-on-every-push) | A single stage written as a string |
| 2 | [Build and deploy a service](#2-build-and-deploy-a-service) | Sequential stages, labels, timeouts, rollback |
| 3 | [Staging and production from branches](#3-staging-and-production-from-branches) | Pipelines with their own branch triggers, environment variables |
| 4 | [Frontend and backend in a monorepo](#4-frontend-and-backend-in-a-monorepo) | Concurrent pipelines, `dir`, cross-pipeline dependencies, secrets, failure notification |
| 5 | [Multi-region rollout with gates](#5-multi-region-rollout-with-gates) | Shared build, staged regions, retries, `when`, cleanup |

Before you use an example, check that the server allows every secret it names. Secrets must be listed in `RODEO_ALLOWED_SECRETS`. The examples in this page use `DEPLOY_TOKEN`, `REGISTRY_PASSWORD`, and `SLACK_TOKEN`.

```text
RODEO_ALLOWED_SECRETS=DEPLOY_TOKEN,REGISTRY_PASSWORD,SLACK_TOKEN
```

The scripts referenced in the examples are assumed to exist in the repository, under `scripts/`.

## 1. Run tests on every push

The smallest useful file. Every push to `main` runs the Maven test suite.

```toml
on = ["main"]

[ci]
test = "mvn -B test"
```

**What happens:** rodeo clones `main`, runs `mvn -B test` in the repository root, and logs the result. A failing test fails the run.

**When to use it:** a quick safety net on a branch, before you add deployment.

## 2. Build and deploy a service

A Spring Boot service is built, tested, and deployed. If the deploy fails, a rollback script runs.

```toml
on = ["main"]

[app]
build  = { run = "mvn -B -q package -DskipTests", label = "Build jar" }
test   = { run = "mvn -B test", label = "Unit tests", needs = [] }
deploy = { run = "./scripts/deploy.sh", label = "Deploy to production", needs = ["build", "test"], timeout = "5m", retries = 1, on_fail = "./scripts/rollback.sh" }
```

**What happens:**

1. `build` and `test` start together. `needs = []` on `test` removes the default dependency on `build`.
2. `deploy` waits for both. A failed build or test stops the deploy.
3. If the deploy fails, it is retried once. If the retry fails too, `rollback.sh` runs.

**Log lines to expect:**

```text
14:02:11.530 INFO  [7c1d0e44 app/build] STARTED  Build jar
14:02:11.531 INFO  [7c1d0e44 app/test] STARTED  Unit tests
14:03:20.877 INFO  [7c1d0e44 app/deploy] STARTED  Deploy to production
```

**When to use it:** a single service with one environment.

## 3. Staging and production from branches

Two pipelines in one file, each triggered by a different branch. A push to `develop` deploys to staging. A push to `main` deploys to production. The two pipelines never run for the same push.

```toml
[staging]
on = ["develop"]
deploy = { run = "./scripts/deploy.sh", label = "Deploy staging", env = { ENVIRONMENT = "staging" } }
smoke  = { run = "./scripts/smoke-test.sh https://staging.example.com", label = "Smoke test", retries = 2 }

[production]
on = ["main"]
build  = { run = "./scripts/build.sh", label = "Build release" }
deploy = { run = "./scripts/deploy.sh", label = "Deploy production", needs = ["build"], env = { ENVIRONMENT = "production" }, secrets = ["DEPLOY_TOKEN"], on_fail = "./scripts/rollback.sh" }
smoke  = { run = "./scripts/smoke-test.sh https://example.com", label = "Smoke test", retries = 3 }
```

**What happens:**

- A push to `develop` runs only `staging`.
- A push to `main` runs only `production`.
- Inside `production`, `deploy` waits for `build`, and `smoke` waits for `deploy`, the default.

**Things to note:**

- The top-level `on` is absent, so each pipeline sets its own branches.
- `ENVIRONMENT` is set per pipeline, so the same deploy script behaves differently in each environment.
- `DEPLOY_TOKEN` is passed only to the production deploy. Staging does not receive it.

## 4. Frontend and backend in a monorepo

A repository holds an API in `services/api` and a web app in `apps/web`. Both are built, tested, and deployed. The web app deploys only after the API is live, because it calls the API. If the web deploy fails, the team is notified.

```toml
on = ["main"]

[backend]
build  = { run = "mvn -B -q package", dir = "services/api", label = "Build API" }
test   = { run = "mvn -B verify", dir = "services/api", label = "Test API" }
deploy = { run = "./scripts/deploy.sh api", label = "Deploy API", needs = ["build", "test"], secrets = ["DEPLOY_TOKEN"], retries = 2, timeout = "5m", on_fail = "./scripts/rollback.sh api" }

[frontend]
build  = { run = ["npm ci", "npm run build"], dir = "apps/web", label = "Build web app" }
test   = { run = "npm test", dir = "apps/web", label = "Test web app" }
deploy = { run = "./scripts/deploy.sh web", dir = "apps/web", label = "Deploy web app", needs = ["test", "backend.deploy"] }
notify = { run = "./scripts/notify.sh frontend-failed", label = "Alert the team", needs = ["deploy"], secrets = ["SLACK_TOKEN"], when = "failure" }
```

**What happens:**

1. `backend/build` and `frontend/build` start at the same time.
2. Each pipeline runs its tests after its build, and the backend deploys after its tests.
3. `frontend/deploy` waits for `frontend/test` and for `backend/deploy`. The frontend goes live only after the API is live.
4. `frontend/notify` runs only when `frontend/deploy` fails. Otherwise it is skipped.

**Log lines to expect:**

```text
14:02:12.020 INFO  [4b8a2f10 backend/build] STARTED  Build API
14:02:12.021 INFO  [4b8a2f10 frontend/build] STARTED  Build web app
...
14:04:01.310 INFO  [4b8a2f10 frontend/deploy] STARTED  Deploy web app
```

**Things to note:**

- `dir` runs each command in the right subdirectory, so the two projects can share the repository without interfering.
- `backend.deploy` in the `needs` list is the cross-pipeline dependency. The name uses a dot.
- The notification uses `when = "failure"`. Its `needs` list names the stage that must have failed.

## 5. Multi-region rollout with gates

A service is built once, scanned, and rolled out to two regions in sequence. The US region deploys only after the EU region passes its smoke test. Any failure notifies the team, and the build artifacts are cleaned up in every case.

```toml
on = ["main"]

[build]
image   = { run = "./scripts/build-image.sh", label = "Build and push image", secrets = ["REGISTRY_PASSWORD"], timeout = "20m" }
scan    = { run = "trivy image --exit-code 1 registry.example.com/app:$RODEO_COMMIT", label = "Scan image", needs = ["image"] }
cleanup = { run = "docker image prune -f", label = "Remove local images", needs = ["image"], when = "always" }

[eu]
deploy = { run = "./scripts/rollout.sh eu-west", label = "Roll out to EU", needs = ["build.scan"], secrets = ["DEPLOY_TOKEN"], retries = 2, timeout = "10m", on_fail = "./scripts/rollback.sh eu-west" }
smoke  = { run = "./scripts/smoke-test.sh https://eu.example.com", label = "Smoke test EU", retries = 3 }

[us]
deploy = { run = "./scripts/rollout.sh us-east", label = "Roll out to US", needs = ["build.scan", "eu.smoke"], secrets = ["DEPLOY_TOKEN"], retries = 2, timeout = "10m", on_fail = "./scripts/rollback.sh us-east" }
smoke  = { run = "./scripts/smoke-test.sh https://us.example.com", label = "Smoke test US", retries = 3 }

[alerts]
notify = { run = "./scripts/notify.sh release-failed", label = "Alert the release channel", needs = ["eu.deploy", "eu.smoke", "us.deploy", "us.smoke"], secrets = ["SLACK_TOKEN"], when = "failure" }
```

**What happens:**

1. `build/image` builds and pushes the container. `build/scan` checks it for vulnerabilities and fails the run if it finds any.
2. `build/cleanup` runs whether or not the image step succeeded, so local images never accumulate.
3. `eu/deploy` starts after the scan passes. `eu/smoke` checks the EU region.
4. `us/deploy` waits for both the scan and the EU smoke test. A bad EU release never reaches the US.
5. `alerts/notify` runs only if one of the deploy or smoke stages failed.

**Things to note:**

- Every stage that deploys has its own `on_fail` rollback, scoped to its region.
- `trivy` must be installed on the server. Any scanner with a non-zero exit code on findings works the same way.
- The `alerts` pipeline has no `on`, so it inherits `on = ["main"]` from the top of the file.

**Log lines to expect when the EU smoke test fails:**

```text
14:20:45.002 INFO  [e91d3b77 eu/smoke] STARTED  Smoke test EU
14:21:30.418 ERROR [e91d3b77 eu/smoke] FAILED in 45.4s: step 1/1 exited with 22
14:21:30.421 INFO  [e91d3b77 alerts/notify] STARTED  Alert the release channel
```

`us/deploy` never starts, because `eu/smoke` did not succeed. The summary table marks it `SKIPPED`, and `alerts/notify` runs because one of its dependencies failed.

## Choosing an example

| Your situation | Start from |
|---|---|
| Testing a repository before anything else | Example 1 |
| One service, one environment | Example 2 |
| Separate staging and production branches | Example 3 |
| Several projects in one repository | Example 4 |
| Several regions or a formal release process | Example 5 |

For every field, see the [rodeo.toml reference](rodeo-toml/index.md). For the log lines these examples produce, see [Logs](logs.md).
