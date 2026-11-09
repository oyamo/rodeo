package dev.rodeo.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rodeo.config.GitLabProperties;
import dev.rodeo.model.PushEvent;
import dev.rodeo.security.GitLabTokenVerifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GitLabWebhookServiceTest {
    private static final String TOKEN = "test-token";
    private static final String PUSH_PAYLOAD = """
            {
              "object_kind": "push",
              "event_name": "push",
              "before": "0000000000000000000000000000000000000000",
              "after": "2222222222222222222222222222222222222222",
              "ref": "refs/heads/main",
              "user_username": "octocat",
              "user_email": "octocat@example.com",
              "project": {
                "id": 7,
                "path_with_namespace": "group/project",
                "default_branch": "main"
              },
              "commits": [
                {
                  "id": "2222222222222222222222222222222222222222",
                  "message": "Fix bug",
                  "author": { "name": "Octo Cat", "email": "octo@example.com" }
                }
              ],
              "total_commits_count": 1
            }
            """;

    @Test
    void invalidTokenRejectedWithoutProcessing() {
        List<PushEvent> receivedEvents = new ArrayList<>();
        GitLabWebhookService service = new GitLabWebhookService(
                new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null)),
                JsonMapper.builder().build(),
                List.of(receivedEvents::add));

        WebhookResult result = service.process(
                PUSH_PAYLOAD.getBytes(StandardCharsets.UTF_8),
                "wrong-token",
                "Push Hook");

        assertThat(result).isEqualTo(WebhookResult.INVALID_SIGNATURE);
        assertThat(receivedEvents).isEmpty();
    }

    @Test
    void nonPushHookEventIsIgnored() {
        List<PushEvent> receivedEvents = new ArrayList<>();
        GitLabWebhookService service = new GitLabWebhookService(
                new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null)),
                JsonMapper.builder().build(),
                List.of(receivedEvents::add));

        WebhookResult result = service.process(
                PUSH_PAYLOAD.getBytes(StandardCharsets.UTF_8),
                TOKEN,
                "Tag Push Hook");

        assertThat(result).isEqualTo(WebhookResult.IGNORED);
        assertThat(receivedEvents).isEmpty();
    }

    @Test
    void validPushHookEventIsProcessedByHandlers() {
        List<PushEvent> receivedEvents = new ArrayList<>();
        GitLabWebhookService service = new GitLabWebhookService(
                new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null)),
                JsonMapper.builder().build(),
                List.of(receivedEvents::add));

        WebhookResult result = service.process(
                PUSH_PAYLOAD.getBytes(StandardCharsets.UTF_8),
                TOKEN,
                "Push Hook");

        assertThat(result).isEqualTo(WebhookResult.ACCEPTED);
        assertThat(receivedEvents).hasSize(1);
        assertThat(receivedEvents.getFirst().repository()).isEqualTo("group/project");
        assertThat(receivedEvents.getFirst().branch()).isEqualTo("main");
    }

    @Test
    void multipleHandlersAreAllExecuted() {
        List<PushEvent> handler1Events = new ArrayList<>();
        List<PushEvent> handler2Events = new ArrayList<>();
        GitLabWebhookService service = new GitLabWebhookService(
                new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null)),
                JsonMapper.builder().build(),
                List.of(handler1Events::add, handler2Events::add));

        service.process(
                PUSH_PAYLOAD.getBytes(StandardCharsets.UTF_8),
                TOKEN,
                "Push Hook");

        assertThat(handler1Events).hasSize(1);
        assertThat(handler2Events).hasSize(1);
    }

    @Test
    void nullTokenIsRejected() {
        List<PushEvent> receivedEvents = new ArrayList<>();
        GitLabWebhookService service = new GitLabWebhookService(
                new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null)),
                JsonMapper.builder().build(),
                List.of(receivedEvents::add));

        WebhookResult result = service.process(
                PUSH_PAYLOAD.getBytes(StandardCharsets.UTF_8),
                null,
                "Push Hook");

        assertThat(result).isEqualTo(WebhookResult.INVALID_SIGNATURE);
        assertThat(receivedEvents).isEmpty();
    }
}
