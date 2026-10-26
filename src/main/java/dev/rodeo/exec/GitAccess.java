package dev.rodeo.exec;

import dev.rodeo.config.GitCredentials;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GitAccess {

    private GitAccess() {
    }

    static String cloneUrl(GitCredentials credentials, String httpsUrl) {
        return credentials.sshKey() == null ? httpsUrl : sshUrl(httpsUrl);
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

    static List<String> options(GitCredentials credentials, Path credentialsFile) {
        List<String> options = new ArrayList<>();
        if (credentials.sshKey() != null) {
            options.add("-c");
            options.add("core.sshCommand=ssh -i " + credentials.sshKey()
                    + " -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new");
        } else if (credentialsFile != null) {
            options.add("-c");
            options.add("credential.helper=store --file=" + credentialsFile);
        }
        return options;
    }
}
