package dev.rodeo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rodeo.service.WebhookResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class WebhookResponsesTest {

    @Test
    void acceptedResultReturns202() {
        ResponseEntity<Void> response = WebhookResponses.from(WebhookResult.ACCEPTED);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void ignoredResultReturns202() {
        ResponseEntity<Void> response = WebhookResponses.from(WebhookResult.IGNORED);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void pingResultReturns200() {
        ResponseEntity<Void> response = WebhookResponses.from(WebhookResult.PING);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void invalidSignatureResultReturns401() {
        ResponseEntity<Void> response = WebhookResponses.from(WebhookResult.INVALID_SIGNATURE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
