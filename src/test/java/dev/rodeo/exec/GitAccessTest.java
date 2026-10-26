package dev.rodeo.exec;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rodeo.config.GitHubProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitAccessTest {

    @Test
    void convertsGitHubHttpsToSshUrl() {
        assertThat(GitAccess.sshUrl("https://github.com/acme/orders.git")).isEqualTo("git@github.com:acme/orders.git");
    }

    @Test
    void convertsGitLabNestedGroupsToSshUrl() {
        assertThat(GitAccess.sshUrl("https://gitlab.com/acme/platform/billing.git"))
                .isEqualTo("git@gitlab.com:acme/platform/billing.git");
    }

    @Test
    void keepsHttpsUrlWhenNoSshKeyIsConfigured() {
        GitHubProperties properties = new GitHubProperties(null, null, null, null);

        assertThat(GitAccess.cloneUrl(properties, "https://github.com/acme/orders.git"))
                .isEqualTo("https://github.com/acme/orders.git");
    }

    @Test
    void usesSshUrlAndKeyWhenSshKeyIsConfigured() {
        GitHubProperties properties = new GitHubProperties(null, null, null, "/etc/rodeo/deploy_key");

        assertThat(GitAccess.cloneUrl(properties, "https://github.com/acme/orders.git"))
                .isEqualTo("git@github.com:acme/orders.git");
        assertThat(GitAccess.options(properties, null)).containsExactly(
                "-c",
                "core.sshCommand=ssh -i /etc/rodeo/deploy_key -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new");
    }

    @Test
    void writesTokenToCredentialStoreWithoutPuttingItInArguments() {
        GitHubProperties properties = new GitHubProperties(null, "ghp_secret123", null, null);
        Path file = Path.of("/tmp/rodeo-credentials-1");

        assertThat(GitAccess.credentialLine("https://github.com/acme/orders.git", "ghp_secret123"))
                .isEqualTo("https://rodeo:ghp_secret123@github.com");
        List<String> options = GitAccess.options(properties, file);
        assertThat(options).containsExactly("-c", "credential.helper=store --file=/tmp/rodeo-credentials-1");
        assertThat(String.join(" ", options)).doesNotContain("ghp_secret123");
    }

    @Test
    void noOptionsWhenNothingIsConfigured() {
        assertThat(GitAccess.options(new GitHubProperties(null, null, null, null), null)).isEmpty();
    }
}
