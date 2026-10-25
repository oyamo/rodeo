package dev.rodeo.controller;

import dev.rodeo.service.GitLabWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/gitlab")
public class GitLabWebhookController {
    private final GitLabWebhookService webhookService;

    public GitLabWebhookController(GitLabWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
            @RequestHeader("X-Gitlab-Event") String event,
            @RequestBody byte[] body) {
        return WebhookResponses.from(webhookService.process(body, token, event));
    }
}
