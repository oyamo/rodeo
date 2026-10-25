package dev.rodeo.service;

import dev.rodeo.model.GitLabPushPayload;
import dev.rodeo.model.PushEvent;
import dev.rodeo.security.GitLabTokenVerifier;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class GitLabWebhookService {
    private static final Logger log = LoggerFactory.getLogger(GitLabWebhookService.class);

    private static final String PUSH_HOOK = "Push Hook";

    private final GitLabTokenVerifier verifier;
    private final ObjectMapper objectMapper;
    private final List<PushEventHandler> pushHandlers;

    public GitLabWebhookService(GitLabTokenVerifier verifier,
                                ObjectMapper objectMapper,
                                List<PushEventHandler> pushHandlers) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
        this.pushHandlers = pushHandlers;
    }

    public WebhookResult process(byte[] body, String token, String event) {
        if (!verifier.isValid(token)) {
            log.warn("Rejected GitLab {} delivery: invalid or missing token", event);
            return WebhookResult.INVALID_SIGNATURE;
        }

        if (!PUSH_HOOK.equals(event)) {
            log.debug("Ignoring GitLab {} event", event);
            return WebhookResult.IGNORED;
        }

        PushEvent push = objectMapper.readValue(body, GitLabPushPayload.class).toPushEvent();
        pushHandlers.forEach(handler -> handler.handle(push));
        return WebhookResult.ACCEPTED;
    }
}
