package dev.rodeo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rodeo.config.GitLabProperties;
import dev.rodeo.model.Provider;
import dev.rodeo.model.PushEvent;
import dev.rodeo.security.GitLabTokenVerifier;
import dev.rodeo.service.GitLabWebhookService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class GitLabWebhookControllerTest {
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
                "default_branch": "main",
                "unknown_future_field": true
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

    private final List<PushEvent> receivedPushes = new ArrayList<>();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new GitLabWebhookController(new GitLabWebhookService(
                    new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null)),
                    JsonMapper.builder().build(),
                    List.of(receivedPushes::add))))
            .build();

    @Test
    void validTokenPushIsAcceptedAndHandlerRunsWithNormalizedEvent() throws Exception {
        mockMvc.perform(delivery("Push Hook", PUSH_PAYLOAD, TOKEN))
                .andExpect(status().isAccepted());

        assertThat(receivedPushes).hasSize(1);
        PushEvent push = receivedPushes.getFirst();
        assertThat(push.provider()).isEqualTo(Provider.GITLAB);
        assertThat(push.repository()).isEqualTo("group/project");
        assertThat(push.branch()).isEqualTo("main");
        assertThat(push.pusher()).isEqualTo("octocat");
        assertThat(push.commitCount()).isEqualTo(1);
    }

    @Test
    void wrongTokenIsRejectedAndHandlerDoesNotRun() throws Exception {
        mockMvc.perform(delivery("Push Hook", PUSH_PAYLOAD, "wrong-token"))
                .andExpect(status().isUnauthorized());

        assertThat(receivedPushes).isEmpty();
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(delivery("Push Hook", PUSH_PAYLOAD, null))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tagPushesAreIgnored() throws Exception {
        mockMvc.perform(delivery("Tag Push Hook", PUSH_PAYLOAD, TOKEN))
                .andExpect(status().isAccepted());

        assertThat(receivedPushes).isEmpty();
    }

    @Test
    void verifierAcceptsOnlyExactToken() {
        GitLabTokenVerifier verifier = new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null));

        assertThat(verifier.isValid(TOKEN)).isTrue();
        assertThat(verifier.isValid(TOKEN + "x")).isFalse();
        assertThat(verifier.isValid(null)).isFalse();
    }

    private static MockHttpServletRequestBuilder delivery(String event, String body, String token) {
        var request = post("/webhooks/gitlab")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Gitlab-Event", event)
                .content(body.getBytes(StandardCharsets.UTF_8));
        if (token != null) {
            request.header("X-Gitlab-Token", token);
        }
        return request;
    }
}
