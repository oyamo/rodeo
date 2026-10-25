# Deploy on push

This guide deploys a Spring Boot application to a Linux server each time someone pushes to `main`. If the new version fails, the previous jar is restored.

## How it fits together

```text
git push origin main
      │
      ▼
GitHub or GitLab ──webhook──▶ rodeo
                                │
                                ├─ clones main into a temporary directory
                                ├─ builds and tests the jar on the server
                                ├─ installs the new jar and restarts the service
                                └─ on failure, restores the previous jar
```

## Prerequisites

- rodeo is installed and reachable over HTTPS. See [Install](installation/docker.md) and [Running rodeo](running.md).
- The repository is public. rodeo cannot clone private repositories yet.
- The server has Java 21 and Maven, and the `rodeo` user can run them.
- A service named `app` is installed with systemd and runs `/opt/app/app.jar`.

## 1. Write rodeo.toml

Create `rodeo.toml` at the root of the repository:

```toml
on = ["main"]

[app]
build = {
  run = "mvn -B -q package -DskipTests",
  label = "Build jar",
  host = true,
}
test = {
  run = "mvn -B test",
  label = "Unit tests",
  needs = [],
  host = true,
}
deploy = {
  run = [
    "cp -f /opt/app/app.jar /opt/app/app.jar.previous 2>/dev/null || true",
    "install -m 644 target/app-*.jar /opt/app/app.jar",
    "sudo systemctl restart app",
    "sleep 5",
    "curl -fsS http://localhost:8080/health",
  ],
  label = "Deploy to /opt/app",
  needs = ["build", "test"],
  host = true,
  timeout = "5m",
  on_fail = [
    "install -m 644 /opt/app/app.jar.previous /opt/app/app.jar",
    "sudo systemctl restart app",
  ],
}
```

What this file does:

- **`on = ["main"]`** runs the pipeline for pushes to `main` only.
- **`test` has `needs = []`**, so it starts together with `build`.
- **`deploy` waits for both**, so nothing ships without a green build and green tests.
- **`host = true`** runs each stage on the server, because the stages install a file and restart a systemd service there.
- **`on_fail`** restores the previous jar if any deploy command fails.

## 2. Allow the service restart

The `rodeo` user must restart only this service. Create `/etc/sudoers.d/rodeo-app`:

```text
rodeo ALL=(root) NOPASSWD: /bin/systemctl restart app
```

Validate the file before you rely on it:

```bash
sudo visudo -cf /etc/sudoers.d/rodeo-app
```

The `rodeo` user also needs write access to the application directory:

```bash
sudo chown rodeo /opt/app
```

## 3. Connect the repository

Add a webhook to the repository as described in [Running rodeo](running.md#connect-a-repository). For GitHub, choose **Just the push event**.

## 4. Deploy and watch

Push to `main`:

```bash
git commit --allow-empty -m "Trigger deploy"
git push origin main
```

Follow the run:

```bash
journalctl -u rodeo -f
```

A successful run prints lines like these:

```text
14:02:11.482 INFO  run 3f9c1a2e received GITHUB push to acme/app on main
14:02:11.520 INFO  run 3f9c1a2e starting 3 stage(s)
14:02:11.530 INFO  [3f9c1a2e app/build] STARTED  Build jar
14:02:11.531 INFO  [3f9c1a2e app/test] STARTED  Unit tests
14:02:52.104 INFO  [3f9c1a2e app/build] SUCCEEDED in 40.6s
14:03:20.877 INFO  [3f9c1a2e app/test] SUCCEEDED in 69.3s
14:03:20.880 INFO  [3f9c1a2e app/deploy] STARTED  Deploy to /opt/app
14:03:27.441 INFO  [3f9c1a2e app/deploy] step 1/5 $ cp -f /opt/app/app.jar /opt/app/app.jar.previous 2>/dev/null || true
14:03:33.102 INFO  run 3f9c1a2e SUCCEEDED in 2m 21s
```

The summary table follows. See [Logs](logs.md) for every message.

## Rollback

If a deploy command fails, `on_fail` restores the previous jar and restarts the service. The log shows:

```text
14:10:04.318 WARN  [9a1c7e42 app/deploy] running on_fail commands
14:10:09.011 ERROR run 9a1c7e42 FAILED in 2m 24s
```

The run ends with `FAILED`, and the summary marks `app/deploy` as failed.

To roll back by hand, restore the saved jar and restart the service:

```bash
sudo install -m 644 /opt/app/app.jar.previous /opt/app/app.jar
sudo systemctl restart app
```

The previous jar is kept for one deploy. A second rollback needs a deploy of an earlier commit.

## Troubleshooting

| Symptom | Where to look |
|---|---|
| The push returns `202`, but no run appears | Run `journalctl -u rodeo \| grep "no pipeline"`. The branch is probably missing from `on`. |
| `aborted` in the log | The `rodeo.toml` file is invalid. The message names the problem. See [Validation](rodeo-toml/validation.md). |
| `git clone` fails | The repository is private, or the branch does not exist. |
| A stage shows `FAILED` with `exited with` | Read the lines above that stage's `FAILED` line. |
| `sudo: a password is required` | The sudoers rule is missing or names a different path to `systemctl`. Check `which systemctl`. |
| A stage shows `FAILED` with `host execution is disabled` | Set `allow-host-execution = true` under `[rodeo.exec]` in `config.toml`. |

## Limits of this setup

Stages with `host = true` run directly on the server, outside any container. Anyone who can push to `main` controls what runs there. Protect `main` with required reviews, and review changes to `rodeo.toml` as you would production code.
