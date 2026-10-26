package dev.rodeo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rodeo.config.GitHubProperties;
import dev.rodeo.model.PushEvent;
import dev.rodeo.security.GitHubSignatureVerifier;
import dev.rodeo.model.Provider;
import dev.rodeo.service.GitHubWebhookService;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class GitHubWebhookControllerTest {
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
                "default_branch": "main",
                "unknown_future_field": true
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

    private final List<PushEvent> receivedPushes = new ArrayList<>();
    private final GitHubSignatureVerifier verifier = new GitHubSignatureVerifier(new GitHubProperties(SECRET, null, null, null));
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new GitHubWebhookController(new GitHubWebhookService(
                    verifier,
                    JsonMapper.builder().build(),
                    List.of(receivedPushes::add))))
            .build();

    @Test
    void validSignedPushIsAcceptedAndHandlerRuns() throws Exception {
        mockMvc.perform(delivery("push", PUSH_PAYLOAD, sign(PUSH_PAYLOAD)))
                .andExpect(status().isAccepted());

        assertThat(receivedPushes).hasSize(1);
        PushEvent push = receivedPushes.getFirst();
        assertThat(push.provider()).isEqualTo(Provider.GITHUB);
        assertThat(push.branch()).isEqualTo("main");
        assertThat(push.repository()).isEqualTo("octo/hello");
        assertThat(push.pusher()).isEqualTo("octocat");
        assertThat(push.commitCount()).isEqualTo(1);
    }

    @Test
    void wrongSignatureIsRejectedAndHandlerDoesNotRun() throws Exception {
        mockMvc.perform(delivery("push", PUSH_PAYLOAD, sign("wrong-secret", PUSH_PAYLOAD)))
                .andExpect(status().isUnauthorized());

        assertThat(receivedPushes).isEmpty();
    }

    @Test
    void tamperedBodyIsRejected() throws Exception {
        String signature = sign(PUSH_PAYLOAD);
        String tampered = PUSH_PAYLOAD.replace("Fix bug", "Evil change");

        mockMvc.perform(delivery("push", tampered, signature))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingSignatureIsRejected() throws Exception {
        mockMvc.perform(delivery("push", PUSH_PAYLOAD, null))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pingIsAcknowledgedWithoutRunningHandlers() throws Exception {
        String ping = "{\"zen\":\"Keep it logically awesome.\"}";

        mockMvc.perform(delivery("ping", ping, sign(ping)))
                .andExpect(status().isOk());

        assertThat(receivedPushes).isEmpty();
    }

    @Test
    void otherEventsAreIgnored() throws Exception {
        String issue = "{\"action\":\"opened\"}";

        mockMvc.perform(delivery("issues", issue, sign(issue)))
                .andExpect(status().isAccepted());

        assertThat(receivedPushes).isEmpty();
    }

    @Test
    void verifierRejectsHeaderWithoutSha256Prefix() {
        byte[] body = PUSH_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        String digestOnly = sign(PUSH_PAYLOAD).substring("sha256=".length());

        assertThat(verifier.isValid(body, digestOnly)).isFalse();
        assertThat(verifier.isValid(body, sign(PUSH_PAYLOAD))).isTrue();
    }

    private static MockHttpServletRequestBuilder delivery(String event, String body, String signature) {
        var request = post("/webhooks/github")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", event)
                .header("X-GitHub-Delivery", "test-delivery-id")
                .content(body.getBytes(StandardCharsets.UTF_8));
        if (signature != null) {
            request.header("X-Hub-Signature-256", signature);
        }
        return request;
    }

    private static String sign(String body) {
        return sign(SECRET, body);
    }

    private static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
