# Running rodeo

## Start

Write the configuration file described in [Configuration](configuration.md) first. Then start rodeo with the method that matches your installation.

### Docker

```bash
docker run -d --name rodeo \
  --restart unless-stopped \
  -v /etc/rodeo/config.toml:/etc/rodeo/config.toml:ro \
  -p 8080:8080 \
  ghcr.io/oyamo/rodeo:latest
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
ExecStart=/usr/bin/java -jar /opt/rodeo/rodeo.jar --rodeo.config=/etc/rodeo/config.toml
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

Stages run in containers, so the `rodeo` user must be able to run Docker. Membership of the `docker` group is equivalent to root on the server. Prefer rootless Docker for the service account.

On macOS, run the jar directly, or register it with `launchd` with the same `--rodeo.config` argument.

### Windows

Start rodeo from PowerShell as shown in [Install on Windows](installation/windows.md#run), or register it as a Windows service as shown in [Run as a service](installation/windows.md#run-as-a-service).

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
4. **Secret:** the value of `secret` under `[github.webhook]` in the configuration file
5. **Which events:** **Just the push event**
6. Save.

To connect every repository in an organization, add the webhook at the organization level instead.

### GitLab

1. Open the project, then go to **Settings → Webhooks → Add new webhook**.
2. **URL:** `https://<your-host>/webhooks/gitlab`
3. **Secret token:** the value of `secret` under `[gitlab.webhook]` in the configuration file
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
SECRET='the-github-webhook-secret-from-config.toml'
SIG="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.* //')"

curl -i -X POST https://rodeo.example.com/webhooks/github \
  -H 'Content-Type: application/json' \
  -H 'X-GitHub-Event: push' \
  -H "X-Hub-Signature-256: $SIG" \
  --data "$BODY"
```

## HTTP responses

| Request | Status | Meaning |
|---|---|---|
| Push with a valid signature or token | `202` | Received. Hooks and any `rodeo.toml` run start in the background. The `202` does not report whether the run succeeded. See [Logs](logs.md). |
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
