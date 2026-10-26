package dev.rodeo.exec;

import dev.rodeo.config.GitCredentials;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GitAccess {

    private static final String CREDENTIAL_USER = "rodeo";
    private static final String SSH_OPTIONS = " -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new";
    private static final String PATH_SEPARATOR = "/";

    private GitAccess() {
    }

    static String cloneUrl(GitCredentials credentials, String httpsUrl) {
        if (credentials.sshKey() == null) {
            return httpsUrl;
        }
        return sshUrl(httpsUrl);
    }

    static String sshUrl(String httpsUrl) {
        URI uri = URI.create(httpsUrl);
        return "git@" + uri.getHost() + ":" + withoutLeadingSlash(uri.getPath());
    }

    static String credentialLine(String httpsUrl, String token) {
        return "https://" + CREDENTIAL_USER + ":" + percentEncode(token) + "@" + URI.create(httpsUrl).getHost();
    }

    static List<String> options(GitCredentials credentials, Path credentialsFile) {
        List<String> options = new ArrayList<>();
        if (credentials.sshKey() != null) {
            options.addAll(List.of("-c", "core.sshCommand=ssh -i " + credentials.sshKey() + SSH_OPTIONS));
        } else if (credentialsFile != null) {
            options.addAll(List.of("-c", "credential.helper=store --file=" + credentialsFile));
        }
        return options;
    }

    private static String withoutLeadingSlash(String path) {
        if (path.startsWith(PATH_SEPARATOR)) {
            return path.substring(1);
        }
        return path;
    }

    private static String percentEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
