package dev.rodeo.exec;

import dev.rodeo.config.GitProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GitAccess {

    private GitAccess() {
    }

    static String cloneUrl(GitProperties properties, String httpsUrl) {
        return properties.sshKey() == null ? httpsUrl : sshUrl(httpsUrl);
    }

    static String sshUrl(String httpsUrl) {
        URI uri = URI.create(httpsUrl);
        String path = uri.getPath().startsWith("/") ? uri.getPath().substring(1) : uri.getPath();
        return "git@" + uri.getHost() + ":" + path;
    }

    static String credentialLine(String httpsUrl, String token) {
        URI uri = URI.create(httpsUrl);
        return "https://rodeo:" + URLEncoder.encode(token, StandardCharsets.UTF_8) + "@" + uri.getHost();
    }

    static List<String> options(GitProperties properties, Path credentialsFile) {
        List<String> options = new ArrayList<>();
        if (properties.sshKey() != null) {
            options.add("-c");
            options.add("core.sshCommand=ssh -i " + properties.sshKey()
                    + " -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new");
        } else if (credentialsFile != null) {
            options.add("-c");
            options.add("credential.helper=store --file=" + credentialsFile);
        }
        return options;
    }
}
