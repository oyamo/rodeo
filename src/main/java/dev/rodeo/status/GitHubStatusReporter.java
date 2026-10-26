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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
    private static final String ELLIPSIS = "…";
    private static final String TRAILING_SLASHES = "/+$";
    private static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
    private static final String API_VERSION = "2022-11-28";
    private static final String ACCEPT_TYPE = "application/vnd.github+json";
    private static final String USER_AGENT = "rodeo";
    private static final String JSON = "application/json";
    private static final String BEARER = "Bearer ";
    private static final String DISABLED_MESSAGE = "GitHub commit statuses are disabled; set github.token to enable them";

    private final String token;
    private final String apiUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final ExecutorService sender = Executors.newVirtualThreadPerTaskExecutor();
    private CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);

    public GitHubStatusReporter(GitHubProperties properties, ObjectMapper objectMapper) {
        this.token = properties.token();
        this.apiUrl = properties.apiUrl().replaceAll(TRAILING_SLASHES, "");
        this.objectMapper = objectMapper;
        if (token == null) {
            log.info(DISABLED_MESSAGE);
        }
    }

    @Override
    public synchronized void report(PushEvent event, String context, CommitState state, String description) {
        if (!enabled(event)) {
            return;
        }
        HttpRequest request = request(event, context, state, description);
        queue = queue.handle((ignored, failure) -> null)
                .thenCompose(ignored -> attempt(request, context, event, 1, sender));
    }

    /** Waits until every status queued so far has been sent or has given up. */
    void awaitIdle() {
        queue.join();
    }

    private boolean enabled(PushEvent event) {
        return token != null && event.provider() == Provider.GITHUB;
    }

    private CompletableFuture<Void> attempt(
            HttpRequest request,
            String context,
            PushEvent event,
            int attempt,
            Executor executor) {
        return CompletableFuture.supplyAsync(() -> send(request), executor)
                .thenCompose(delivery -> conclude(delivery, request, context, event, attempt));
    }

    private CompletableFuture<Void> conclude(
            Delivery delivery,
            HttpRequest request,
            String context,
            PushEvent event,
            int attempt) {
        switch (delivery) {
            case DELIVERED -> log.info("status {} set on {} for {}", context, shortSha(event), event.repository());
            case RETRYABLE -> {
                if (attempt < ATTEMPTS) {
                    log.warn("status {} attempt {} of {} failed; retrying", context, attempt, ATTEMPTS);
                    return attempt(request, context, event, attempt + 1, delayed());
                }
                log.error("status {} could not be set on {} after {} attempts", context, shortSha(event), ATTEMPTS);
            }
            case REJECTED, STOPPED -> {
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private Executor delayed() {
        return CompletableFuture.delayedExecutor(RETRY_DELAY.toMillis(), TimeUnit.MILLISECONDS, sender);
    }

    private Delivery send(HttpRequest request) {
        try {
            return classify(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        } catch (IOException e) {
            log.warn("status update could not reach GitHub: {}", e.getMessage());
            return Delivery.RETRYABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Delivery.STOPPED;
        }
    }

    private static Delivery classify(int code) {
        HttpStatusCode status = HttpStatusCode.valueOf(code);
        if (status.is2xxSuccessful()) {
            return Delivery.DELIVERED;
        }
        if (status.is5xxServerError() || status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            log.warn("GitHub returned HTTP {} for a status update", code);
            return Delivery.RETRYABLE;
        }
        log.error("GitHub rejected a status update with HTTP {}", code);
        return Delivery.REJECTED;
    }

    private HttpRequest request(PushEvent event, String context, CommitState state, String description) {
        return HttpRequest.newBuilder(URI.create(endpoint(event)))
                .header(HttpHeaders.AUTHORIZATION, BEARER + token)
                .header(HttpHeaders.ACCEPT, ACCEPT_TYPE)
                .header(API_VERSION_HEADER, API_VERSION)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header(HttpHeaders.CONTENT_TYPE, JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body(context, state, description)))
                .build();
    }

    private String endpoint(PushEvent event) {
        return apiUrl + "/repos/" + event.repository() + "/statuses/" + event.after();
    }

    private String body(String context, CommitState state, String description) {
        return objectMapper.writeValueAsString(Map.of(
                "state", state.value(),
                "context", context,
                "description", truncate(description)));
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

    /** The result of one attempt, or of the whole delivery when it is the last. */
    private enum Delivery {
        DELIVERED,
        RETRYABLE,
        REJECTED,
        STOPPED
    }
}
