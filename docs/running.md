# Running rodeo

## Start

Set the variables described in [Configuration](configuration.md) first. Then start rodeo with the method that matches your installation.

### Docker

```bash
docker run -d --name rodeo \
  --restart unless-stopped \
  --env-file rodeo.env \
  -p 8080:8080 \
  rodeo:latest
```

Follow the log with `docker logs -f rodeo`. Stop the container with `docker stop rodeo`.

### Linux and macOS (systemd)

Create a dedicated service user, so rodeo does not run as your login account:

```bash
sudo useradd --system --create-home --home-dir /var/lib/rodeo --shell /usr/sbin/nologin rodeo
```

Create `/etc/systemd/system/rodeo.service`:

```ini
[Unit]
Description=rodeo pipeline runner
After=network-online.target
Wants=network-online.target

[Service]
User=rodeo
EnvironmentFile=/etc/rodeo/rodeo.env
ExecStart=/usr/bin/java -jar /opt/rodeo/rodeo.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Start the service and follow its log:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now rodeo
sudo systemctl status rodeo
journalctl -u rodeo -f
```

Stage commands run as the `rodeo` user. Install the tools they need system-wide, so they are on the default `PATH`.

On macOS, run the jar directly, or register it with `launchd` using the same variables.

### Windows

From the project folder, in a PowerShell window that has the variables set:

```powershell
java -jar target\rodeo-0.1.0-SNAPSHOT.jar
```

To run rodeo in the background at startup, register it as a Windows service with a wrapper such as [NSSM](https://nssm.cc/). Point the service at `java` with the arguments `-jar C:\rodeo\rodeo-0.1.0-SNAPSHOT.jar`. The service account must have the machine variables set and must reach your repositories.

### Verify it is running

```bash
curl -i -X POST http://localhost:8080/webhooks/github
```

Without a signature this returns `401`. Any HTTP response confirms that rodeo is listening.

## Expose it over HTTPS

GitHub and GitLab send webhooks to a public HTTPS URL. Put rodeo behind a reverse proxy that terminates TLS. With Caddy, this is enough:

```text
rodeo.example.com {
    reverse_proxy localhost:8080
}
```

Caddy obtains the certificate automatically. With nginx, use `proxy_pass http://127.0.0.1:8080;` inside a `location /` block and a certificate from your provider.

rodeo does not need to be reachable on any other path. Only `/webhooks/github` and `/webhooks/gitlab` accept requests. Everything else returns `403`.

## Connect a repository

Connect each repository to the matching endpoint. The payload URL is `https://<your-host>/webhooks/github` for GitHub and `https://<your-host>/webhooks/gitlab` for GitLab.

### GitHub

1. Open the repository, then go to **Settings → Webhooks → Add webhook**.
2. **Payload URL:** `https://<your-host>/webhooks/github`
3. **Content type:** `application/json`
4. **Secret:** the value of `GITHUB_WEBHOOK_SECRET`
5. **Which events:** **Just the push event**
6. Save.

To connect every repository in an organization, add the webhook at the organization level instead.

### GitLab

1. Open the project, then go to **Settings → Webhooks → Add new webhook**.
2. **URL:** `https://<your-host>/webhooks/gitlab`
3. **Secret token:** the value of `GITLAB_WEBHOOK_SECRET`
4. **Trigger:** **Push events**
5. Save, then select **Test → Push events** to send a test delivery.

## Check that deliveries arrive

Push a commit, or use **Recent Deliveries** (GitHub) or **Test** (GitLab). A correctly signed push returns `202 Accepted`.

rodeo logs each push as a run and prints a line for every stage it executes:

```text
14:02:11.482 INFO  run 3f9c1a2e received GITHUB push to octo/hello on main
14:02:11.520 INFO  run 3f9c1a2e no pipeline is triggered by branch main; nothing to run
```

The full format is described in [Logs](logs.md).

You can test without a provider by sending a signed request yourself:

```bash
BODY='{"ref":"refs/heads/main","after":"abc","repository":{"full_name":"octo/hello"},"commits":[]}'
SIG="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$GITHUB_WEBHOOK_SECRET" | sed 's/^.* //')"

curl -i -X POST https://rodeo.example.com/webhooks/github \
  -H 'Content-Type: application/json' \
  -H 'X-GitHub-Event: push' \
  -H "X-Hub-Signature-256: $SIG" \
  --data "$BODY"
```

## HTTP responses

| Request | Status | Meaning |
|---|---|---|
| Push with a valid signature or token | `202` | Accepted. Hooks and any `rodeo.toml` commands are triggered. |
| Other event, such as an issue or a tag push | `202` | Accepted and ignored. |
| GitHub `ping` with a valid signature | `200` | rodeo is reachable. |
| Missing or wrong signature or token | `401` | Rejected. Nothing ran. |
| Any other path | `403` | Not served. |

## Troubleshooting

| Symptom | Check |
|---|---|
| Every delivery returns `401` | The secret in rodeo and the secret in the provider are different. Re-enter the value in the provider. |
| rodeo does not start | One of the two secrets is not set. See [Configuration](configuration.md). |
| Deliveries return `202` but nothing runs | The repository has no `rodeo.toml`, or no pipeline lists the pushed branch in `on`. See [Logs](logs.md#run-lifecycle). |
| A run shows `aborted` | The `rodeo.toml` file is invalid. The message names the problem. See [Validation](rodeo-toml/validation.md). |
| Connection refused or timeout from the provider | The proxy is not reachable from the internet, or DNS does not point at the host. |
