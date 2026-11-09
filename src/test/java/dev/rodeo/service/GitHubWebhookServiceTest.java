package dev.rodeo.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rodeo.config.GitHubProperties;
import dev.rodeo.model.PushEvent;
import dev.rodeo.security.GitHubSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GitHubWebhookServiceTest {
    private static final String SECRET = "test-secret";
    private static final String PUSH_PAYLOAD = """
            {
              "ref": "refs/heads/main",
              "before": "0000000000000000000000000000000000000000",
              "after": "1111111111111111111111111111111111111111",
              "created": false,
              "deleted": false,
              "forced": false,
              "repository": {
                "id": 42,
                "full_name": "octo/hello",
                "html_url": "https://github.com/octo/hello",
                "default_branch": "main"
              },
              "pusher": { "name": "octocat", "email": "octocat@example.com" },
              "commits": [
                {
                  "id": "1111111111111111111111111111111111111111",
                  "message": "Fix bug",
                  "author": { "name": "Octo Cat", "email": "octo@example.com" }
                }
              ]
            }
            """;

    @Test
    void invalidSignatureRejectedWithoutProcessing() {
        List<PushEvent> receivedEvents = new ArrayList<>();
        GitHubWebhookService service = new GitHubWebhookService(
                new GitHubSignatureVerifier(new GitHubProperties(SECRET, null, null, null)),
                JsonMapper.builder().build(),
                List.of(receivedEvents::add));

        WebhookResult result = service.process(
                PUSH_PAYLOAD.getBytes(StandardCharsets.UTF_8),
                "sha256=invalid",
                "push",
                "delivery-1");

        assertThat(result).isEqualTo(WebhookResult.INVALID_SIGNATURE);
        assertThat(receivedEvents).isEmpty();
    }

    @Test
    void pingEventIsProcessedWithoutHandlers() {
        List<PushEvent> receivedEvents = new ArrayList<>();
        GitHubWebhookService service = new GitHubWebhookService(
                new GitHubSignatureVerifier(new GitHubProperties(SECRET, null, null, null)),
                JsonMapper.builder().build(),
                List.of(receivedEvents::add));
        String ping = "{\"zen\":\"Keep it awesome\"}";

        WebhookResult result = service.process(
                ping.getBytes(StandardCharsets.UTF_8),
                sign(ping),
                "ping",
                "delivery-1");

        assertThat(result).isEqualTo(WebhookResult.PING);
        assertThat(receivedEvents).isEmpty();
    }

    @Test
    void nonPushEventIsIgnored() {
        List<PushEvent> receivedEvents = new ArrayList<>();
        GitHubWebhookService service = new GitHubWebhookService(
                new GitHubSignatureVerifier(new GitHubProperties(SECRET, null, null, null)),
                JsonMapper.builder().build(),
                List.of(receivedEvents::add));
        String issue = "{\"action\":\"opened\"}";

        WebhookResult result = service.process(
                issue.getBytes(StandardCharsets.UTF_8),
                sign(issue),
                "issues",
                "delivery-1");

        assertThat(result).isEqualTo(WebhookResult.IGNORED);
        assertThat(receivedEvents).isEmpty();
    }

    @Test
    void validPushEventIsProcessedByHandlers() {
        List<PushEvent> receivedEvents = new ArrayList<>();
        GitHubWebhookService service = new GitHubWebhookService(
                new GitHubSignatureVerifier(new GitHubProperties(SECRET, null, null, null)),
                JsonMapper.builder().build(),
                List.of(receivedEvents::add));

        WebhookResult result = service.process(
                PUSH_PAYLOAD.getBytes(StandardCharsets.UTF_8),
                sign(PUSH_PAYLOAD),
                "push",
                "delivery-1");

        assertThat(result).isEqualTo(WebhookResult.ACCEPTED);
        assertThat(receivedEvents).hasSize(1);
        assertThat(receivedEvents.getFirst().repository()).isEqualTo("octo/hello");
        assertThat(receivedEvents.getFirst().branch()).isEqualTo("main");
    }

    @Test
    void multipleHandlersAreAllExecuted() {
        List<PushEvent> handler1Events = new ArrayList<>();
        List<PushEvent> handler2Events = new ArrayList<>();
        GitHubWebhookService service = new GitHubWebhookService(
                new GitHubSignatureVerifier(new GitHubProperties(SECRET, null, null, null)),
                JsonMapper.builder().build(),
                List.of(handler1Events::add, handler2Events::add));

        service.process(
                PUSH_PAYLOAD.getBytes(StandardCharsets.UTF_8),
                sign(PUSH_PAYLOAD),
                "push",
                "delivery-1");

        assertThat(handler1Events).hasSize(1);
        assertThat(handler2Events).hasSize(1);
    }

    private static String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
