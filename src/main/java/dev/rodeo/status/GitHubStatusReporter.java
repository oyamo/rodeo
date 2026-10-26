package dev.rodeo.status;

import dev.rodeo.config.GitHubProperties;
import dev.rodeo.model.Provider;
import dev.rodeo.model.PushEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class GitHubStatusReporter implements StatusReporter {

    private static final Logger log = LoggerFactory.getLogger(GitHubStatusReporter.class);
    private static final int ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_DESCRIPTION = 140;
    private static final int SHORT_SHA_LENGTH = 7;
    private static final String TRAILING_SLASHES = "/+$";
    private static final String ELLIPSIS = "…";
    private static final String API_VERSION = "2022-11-28";
    private static final String USER_AGENT = "rodeo";
    private static final String MEDIA_TYPE = "application/vnd.github+json";
    private static final String JSON = "application/json";
    private static final String TOKEN_PREFIX = "Bearer ";
    private static final int HTTP_OK = 200;
    private static final int HTTP_MULTIPLE_CHOICES = 300;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR = 500;

    private final String token;
    private final String apiUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    public GitHubStatusReporter(GitHubProperties properties, ObjectMapper objectMapper) {
        this.token = properties.token();
        this.apiUrl = properties.apiUrl().replaceAll(TRAILING_SLASHES, "");
        this.objectMapper = objectMapper;
        if (token == null) {
            log.info("GitHub commit statuses are disabled; set github.token to enable them");
        }
    }

    @Override
    public void report(PushEvent event, String context, CommitState state, String description) {
        if (token == null || event.provider() != Provider.GITHUB) {
            return;
        }
        HttpRequest request = request(event, context, state, description);
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            if (send(request)) {
                log.info("status {} {} set on {} for {}", context, state.value(), shortSha(event), event.repository());
                return;
            }
            if (attempt < ATTEMPTS && !pause(context, attempt)) {
                return;
            }
        }
        log.error("status {} could not be set on {} after {} attempts", context, shortSha(event), ATTEMPTS);
    }

    private HttpRequest request(PushEvent event, String context, CommitState state, String description) {
        String body = objectMapper.writeValueAsString(Map.of(
                "state", state.value(),
                "context", context,
                "description", truncate(description)));
        return HttpRequest.newBuilder(URI.create(apiUrl + "/repos/" + event.repository() + "/statuses/" + event.after()))
                .header("Authorization", TOKEN_PREFIX + token)
                .header("Accept", MEDIA_TYPE)
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    /** Returns true when the update is finished, whether it succeeded or was rejected for good. */
    private boolean send(HttpRequest request) {
        try {
            int code = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
            if (code >= HTTP_OK && code < HTTP_MULTIPLE_CHOICES) {
                return true;
            }
            if (isRetryable(code)) {
                log.warn("GitHub returned HTTP {} for a status update", code);
                return false;
            }
            log.error("GitHub rejected a status update with HTTP {}", code);
            return true;
        } catch (IOException e) {
            log.warn("status update could not reach GitHub: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private static boolean isRetryable(int code) {
        return code >= HTTP_SERVER_ERROR || code == HTTP_TOO_MANY_REQUESTS;
    }

    private static boolean pause(String context, int attempt) {
        log.warn("status {} attempt {} of {} failed; retrying", context, attempt, ATTEMPTS);
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String truncate(String description) {
        if (description.length() <= MAX_DESCRIPTION) {
            return description;
        }
        return description.substring(0, MAX_DESCRIPTION - 1) + ELLIPSIS;
    }

    private static String shortSha(PushEvent event) {
        String sha = event.after();
        return sha.substring(0, Math.min(sha.length(), SHORT_SHA_LENGTH));
    }
}
