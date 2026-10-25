# Running rodeo

## Start

With rodeo installed and configured, start it using the method you chose:

| Method | Command |
|---|---|
| Docker | `docker start rodeo` |
| systemd | `sudo systemctl start rodeo` |
| Windows | `java -jar rodeo-0.1.0-SNAPSHOT.jar` |

When it is running, the log shows the port it listens on. Stop it with `docker stop rodeo`, `sudo systemctl stop rodeo`, or Ctrl+C in the terminal.

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

rodeo logs one line per accepted push:

```text
GITHUB push to octo/hello on branch main by octocat: 3 commit(s), head 1a2b3c4
```

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
| Deliveries return `202` but nothing runs | The repository has no `rodeo.toml`, or the branch is not listed in it. See [rodeo.toml](rodeo-toml.md#logs). |
| Connection refused or timeout from the provider | The proxy is not reachable from the internet, or DNS does not point at the host. |
