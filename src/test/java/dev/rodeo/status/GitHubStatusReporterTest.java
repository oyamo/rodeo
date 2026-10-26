package dev.rodeo.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.rodeo.config.GitHubProperties;
import dev.rodeo.model.Provider;
import dev.rodeo.model.PushEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GitHubStatusReporterTest {

    private record Request(String method, String path, String authorization, String body) {
    }

    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Queue<Integer> responses = new ConcurrentLinkedQueue<>();
    private HttpServer server;
    private String apiUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    body));
            Integer code = responses.poll();
            exchange.sendResponseHeaders(Objects.requireNonNullElse(code, 201), -1);
            exchange.close();
        });
        server.start();
        apiUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsStateToTheCommitWithTheToken() {
        GitHubStatusReporter reporter = new GitHubStatusReporter(
                new GitHubProperties(null, "token-123", apiUrl, null), JsonMapper.builder().build());

        reporter.report(githubPush(), "rodeo/app", CommitState.SUCCESS, "All stages succeeded");
        reporter.awaitIdle();

        assertThat(requests).hasSize(1);
        Request request = requests.getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/repos/octo/hello/statuses/abc1234");
        assertThat(request.authorization()).isEqualTo("Bearer token-123");
        assertThat(request.body())
                .contains("\"state\":\"success\"")
                .contains("\"context\":\"rodeo/app\"")
                .contains("All stages succeeded");
    }

    @Test
    void retriesServerErrorsUntilGitHubAccepts() {
        responses.add(500);
        responses.add(503);
        GitHubStatusReporter reporter = new GitHubStatusReporter(
                new GitHubProperties(null, "token-123", apiUrl, null), JsonMapper.builder().build());

        reporter.report(githubPush(), "rodeo/app", CommitState.FAILURE, "Failed: Deploy");
        reporter.awaitIdle();

        assertThat(requests).hasSize(3);
    }

    @Test
    void doesNotRetryClientErrors() {
        responses.add(422);
        GitHubStatusReporter reporter = new GitHubStatusReporter(
                new GitHubProperties(null, "token-123", apiUrl, null), JsonMapper.builder().build());

        reporter.report(githubPush(), "rodeo/app", CommitState.PENDING, "Run started");
        reporter.awaitIdle();

        assertThat(requests).hasSize(1);
    }

    @Test
    void doesNothingWithoutAToken() {
        GitHubStatusReporter reporter = new GitHubStatusReporter(
                new GitHubProperties(null, "", apiUrl, null), JsonMapper.builder().build());

        reporter.report(githubPush(), "rodeo/app", CommitState.SUCCESS, "done");
        reporter.awaitIdle();

        assertThat(requests).isEmpty();
    }

    @Test
    void ignoresPushesFromOtherProviders() {
        GitHubStatusReporter reporter = new GitHubStatusReporter(
                new GitHubProperties(null, "token-123", apiUrl, null), JsonMapper.builder().build());
        PushEvent gitlab = new PushEvent(
                Provider.GITLAB, "group/project", "https://gitlab.com/group/project.git",
                "main", "abc1234", "octocat", 1);

        reporter.report(gitlab, "rodeo/app", CommitState.SUCCESS, "done");
        reporter.awaitIdle();

        assertThat(requests).isEmpty();
    }

    @Test
    void truncatesLongDescriptions() {
        GitHubStatusReporter reporter = new GitHubStatusReporter(
                new GitHubProperties(null, "token-123", apiUrl, null), JsonMapper.builder().build());

        reporter.report(githubPush(), "rodeo/app", CommitState.FAILURE, "x".repeat(300));
        reporter.awaitIdle();

        assertThat(requests.getFirst().body()).doesNotContain("x".repeat(141));
    }

    private static PushEvent githubPush() {
        return new PushEvent(
                Provider.GITHUB,
                "octo/hello",
                "https://github.com/octo/hello.git",
                "main",
                "abc1234",
                "octocat",
                1);
    }
}
