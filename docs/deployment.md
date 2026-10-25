# Deploy on push

This walkthrough deploys a Spring Boot application to a Linux server every time someone pushes to `main`. It uses rodeo and a `rodeo.toml` in the application's repository.

## How it fits together

```text
git push origin main
      │
      ▼
GitHub or GitLab  ──webhook──▶  rodeo (on your server)
                                  │
                                  ├─ clones main into a temp directory
                                  ├─ reads rodeo.toml
                                  └─ runs ./scripts/deploy.sh
                                        │
                                        ├─ builds the jar
                                        ├─ copies it to /opt/app
                                        └─ restarts the app service
```

## Prerequisites

- rodeo is installed and running, and reachable over HTTPS. See [Install](installation/docker.md) and [Running rodeo](running.md).
- The application's repository is public, because rodeo cannot clone private repositories yet.
- The server has the tools the deploy needs: here, Maven and Java 21.
- An application service named `app` already runs from `/opt/app/app.jar`, under systemd.

## 1. Add the rodeo.toml file

In the application's repository, create `rodeo.toml` at the root:

```toml
branches = ["main"]

commands = [
  "./scripts/deploy.sh",
]
```

Pushes to any other branch are ignored.

## 2. Add the deploy script

Create `scripts/deploy.sh` and make it executable (`chmod +x scripts/deploy.sh`):

```bash
#!/bin/sh
set -eu

mvn -B -q package -DskipTests

if [ -f /opt/app/app.jar ]; then
    cp /opt/app/app.jar /opt/app/app.jar.previous
fi
install -m 644 target/app-*.jar /opt/app/app.jar
sudo systemctl restart app
```

`set -eu` stops the script at the first error, so a failed build never replaces the running jar. The previous jar is kept as `app.jar.previous` for rollback.

## 3. Let rodeo restart the service

The `rodeo` user needs permission to restart only this service. Create `/etc/sudoers.d/rodeo-app`:

```text
rodeo ALL=(root) NOPASSWD: /bin/systemctl restart app
```

Check the file before you save it:

```bash
sudo visudo -cf /etc/sudoers.d/rodeo-app
```

The script must also be able to write `/opt/app/app.jar`, so give the `rodeo` user write access to that directory:

```bash
sudo chown rodeo /opt/app
```

## 4. Connect the repository

Add the repository's webhook as described in [Running rodeo](running.md#connect-a-repository). For GitHub, the payload URL is `https://<your-host>/webhooks/github`, and the event is **Just the push event**.

## 5. Deploy

Push a commit to `main`:

```bash
git commit --allow-empty -m "Trigger deploy"
git push origin main
```

Watch the run on the server:

```bash
journalctl -u rodeo -f
```

You should see lines like these, in order:

```text
GITHUB push to acme/app on branch main by alice: 1 commit(s), head 9f8e7d6
Running './scripts/deploy.sh' in /tmp/rodeo-1234567890
Finished commands for acme/app on branch main
```

Confirm the application restarted:

```bash
systemctl status app
```

## Rolling back

Restore the jar the previous deploy saved, and restart the service:

```bash
sudo install -m 644 /opt/app/app.jar.previous /opt/app/app.jar
sudo systemctl restart app
```

The restore keeps only one previous version. Rolling back twice in a row requires a deploy of an older commit.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| The push shows `202` but no run appears in the log | The branch is not listed in `rodeo.toml`, or the file is missing. See [rodeo.toml logs](rodeo-toml.md#logs). |
| `git clone failed` | The repository is private, or the branch name is wrong. |
| `Command exited with 1` | The build failed. Read the output above that line. |
| `sudo: a password is required` | The sudoers rule is missing or has a different path to `systemctl`. Check `which systemctl`. |
| The run takes longer than 10 minutes and is killed | The build or deploy step is too slow for one command. Split the work into smaller commands, or run long jobs outside rodeo. |

## Limits of this setup

Deploy commands run directly on the server, outside any container. Anyone with push access to `main` can change what runs. Restrict who can push to `main`, and review changes to `rodeo.toml` and `scripts/` the way you review production code.
