package dev.rodeo.status;

import dev.rodeo.config.StatusProperties;
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
    private static final int MAX_DESCRIPTION = 140;

    private final String token;
    private final String apiUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public GitHubStatusReporter(StatusProperties properties, ObjectMapper objectMapper) {
        this.token = properties.githubToken();
        this.apiUrl = properties.githubApiUrl().replaceAll("/+$", "");
        this.objectMapper = objectMapper;
        if (token.isEmpty()) {
            log.info("GitHub commit statuses are disabled; set rodeo.status.github-token to enable them");
        }
    }

    @Override
    public void report(PushEvent event, String context, CommitState state, String description) {
        if (token.isEmpty() || event.provider() != Provider.GITHUB) {
            return;
        }

        String body = objectMapper.writeValueAsString(Map.of(
                "state", state.value(),
                "context", context,
                "description", truncate(description)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        apiUrl + "/repos/" + event.repository() + "/statuses/" + event.after()))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "rodeo")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Outcome outcome = send(request);
            if (outcome == Outcome.DONE) {
                log.info("status {} {} set on {} for {}", context, state.value(), shortSha(event), event.repository());
                return;
            }
            if (attempt < ATTEMPTS) {
                log.warn("status {} attempt {} of {} failed; retrying", context, attempt, ATTEMPTS);
                if (!pause()) {
                    return;
                }
            }
        }
        log.error("status {} could not be set on {} after {} attempts", context, shortSha(event), ATTEMPTS);
    }

    private Outcome send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                return Outcome.DONE;
            }
            if (code >= 500 || code == 429) {
                log.warn("GitHub returned HTTP {} for a status update", code);
                return Outcome.RETRY;
            }
            log.error("GitHub rejected a status update with HTTP {}", code);
            return Outcome.DONE;
        } catch (IOException e) {
            log.warn("status update could not reach GitHub: {}", e.getMessage());
            return Outcome.RETRY;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.DONE;
        }
    }

    private static boolean pause() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String truncate(String description) {
        return description.length() <= MAX_DESCRIPTION ? description : description.substring(0, MAX_DESCRIPTION - 1) + "…";
    }

    private static String shortSha(PushEvent event) {
        String sha = event.after();
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }

    private enum Outcome {
        DONE,
        RETRY
    }
}
