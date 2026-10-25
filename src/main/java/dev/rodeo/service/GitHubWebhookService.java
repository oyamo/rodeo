package dev.rodeo.service;

import dev.rodeo.model.GitHubPushPayload;
import dev.rodeo.model.PushEvent;
import dev.rodeo.security.GitHubSignatureVerifier;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class GitHubWebhookService {
    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);

    private final GitHubSignatureVerifier verifier;
    private final ObjectMapper objectMapper;
    private final List<PushEventHandler> pushHandlers;

    public GitHubWebhookService(GitHubSignatureVerifier verifier,
                                ObjectMapper objectMapper,
                                List<PushEventHandler> pushHandlers) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
        this.pushHandlers = pushHandlers;
    }

    public WebhookResult process(byte[] body, String signature, String event, String deliveryId) {
        if (!verifier.isValid(body, signature)) {
            log.warn("Rejected GitHub delivery {}: invalid or missing signature", deliveryId);
            return WebhookResult.INVALID_SIGNATURE;
        }

        if ("ping".equals(event)) {
            return WebhookResult.PING;
        }

        if (!"push".equals(event)) {
            log.debug("Ignoring GitHub {} event for delivery {}", event, deliveryId);
            return WebhookResult.IGNORED;
        }

        PushEvent push = objectMapper.readValue(body, GitHubPushPayload.class).toPushEvent();
        pushHandlers.forEach(handler -> handler.handle(push));
        return WebhookResult.ACCEPTED;
    }
}
