package dev.rodeo.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rodeo.config.GitLabProperties;
import org.junit.jupiter.api.Test;

class GitLabTokenVerifierTest {
    private static final String TOKEN = "test-token";

    @Test
    void validTokenIsAccepted() {
        GitLabTokenVerifier verifier = new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null));

        assertThat(verifier.isValid(TOKEN)).isTrue();
    }

    @Test
    void invalidTokenIsRejected() {
        GitLabTokenVerifier verifier = new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null));

        assertThat(verifier.isValid("wrong-token")).isFalse();
    }

    @Test
    void nullTokenIsRejected() {
        GitLabTokenVerifier verifier = new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null));

        assertThat(verifier.isValid(null)).isFalse();
    }

    @Test
    void emptyTokenIsRejected() {
        GitLabTokenVerifier verifier = new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null));

        assertThat(verifier.isValid("")).isFalse();
    }

    @Test
    void tokenWithExtraCharacterIsRejected() {
        GitLabTokenVerifier verifier = new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null));

        assertThat(verifier.isValid(TOKEN + "x")).isFalse();
    }

    @Test
    void tokenWithMissingCharacterIsRejected() {
        GitLabTokenVerifier verifier = new GitLabTokenVerifier(new GitLabProperties(TOKEN, null, null));

        assertThat(verifier.isValid(TOKEN.substring(0, TOKEN.length() - 1))).isFalse();
    }
}
