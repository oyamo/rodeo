package dev.rodeo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "rodeo.git")
public record GitProperties(String token, String sshKey) {

    @ConstructorBinding
    public GitProperties {
        token = token == null || token.isBlank() ? null : token;
        sshKey = sshKey == null || sshKey.isBlank() ? null : sshKey;
    }

    public static GitProperties none() {
        return new GitProperties(null, null);
    }
}
