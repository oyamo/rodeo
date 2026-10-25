package dev.rodeo.controller;

import dev.rodeo.service.WebhookResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class WebhookResponses {
    private WebhookResponses() {
    }

    static ResponseEntity<Void> from(WebhookResult result) {
        return switch (result) {
            case ACCEPTED, IGNORED -> ResponseEntity.accepted().build();
            case PING -> ResponseEntity.ok().build();
            case INVALID_SIGNATURE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        };
    }
}
