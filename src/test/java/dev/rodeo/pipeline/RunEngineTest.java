package dev.rodeo.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunEngineTest {

    private ExecutorService pool;
    private RunEngine engine;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(4);
        engine = new RunEngine(pool);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    void independentPipelinesRunConcurrently() throws Exception {
        CountDownLatch bothBuildsStarted = new CountDownLatch(2);
        StageRunner runner = stage -> stage.name().equals("build")
                ? awaitPeer(bothBuildsStarted)
                : Status.SUCCEEDED;

        RunResult result = engine.execute(List.of(
                stage("frontend", "build", Condition.SUCCESS, false),
                stage("backend", "build", Condition.SUCCESS, false),
                stage("frontend", "deploy", Condition.SUCCESS, false, "frontend/build", "backend/build")),
                runner);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.statusOf("frontend/deploy")).isEqualTo(Status.SUCCEEDED);
    }

    @Test
    void failureSkipsSuccessDependentsAndRunsFailureAndAlwaysStages() throws Exception {
        StageRunner runner = stage -> stage.name().equals("a") ? Status.FAILED : Status.SUCCEEDED;

        RunResult result = engine.execute(List.of(
                stage("p", "a", Condition.SUCCESS, false),
                stage("p", "b", Condition.SUCCESS, false, "p/a"),
                stage("p", "notify", Condition.FAILURE, false, "p/a"),
                stage("p", "cleanup", Condition.ALWAYS, false, "p/b")),
                runner);

        assertThat(result.statusOf("p/a")).isEqualTo(Status.FAILED);
        assertThat(result.statusOf("p/b")).isEqualTo(Status.SKIPPED);
        assertThat(result.statusOf("p/notify")).isEqualTo(Status.SUCCEEDED);
        assertThat(result.statusOf("p/cleanup")).isEqualTo(Status.SUCCEEDED);
        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void optionalFailureDoesNotBlockDependents() throws Exception {
        StageRunner runner = stage -> stage.name().equals("lint") ? Status.FAILED : Status.SUCCEEDED;

        RunResult result = engine.execute(List.of(
                stage("p", "lint", Condition.SUCCESS, true),
                stage("p", "deploy", Condition.SUCCESS, false, "p/lint")),
                runner);

        assertThat(result.statusOf("p/deploy")).isEqualTo(Status.SUCCEEDED);
        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void missingDependencyCountsAsSkipped() throws Exception {
        RunResult result = engine.execute(List.of(
                stage("p", "deploy", Condition.SUCCESS, false, "other/build")),
                stage -> Status.SUCCEEDED);

        assertThat(result.statusOf("p/deploy")).isEqualTo(Status.SKIPPED);
    }

    @Test
    void reportsLabelsAndElapsedTimePerStage() throws Exception {
        RunResult result = engine.execute(List.of(
                stage("p", "build", Condition.SUCCESS, false)),
                stage -> Status.SUCCEEDED);

        StageReport report = result.stages().getFirst();
        assertThat(report.label()).isEqualTo("build");
        assertThat(report.elapsed()).isGreaterThanOrEqualTo(Duration.ZERO);
    }

    private static StageSpec stage(String pipeline, String name, Condition when, boolean optional, String... needs) {
        return new StageSpec(
                pipeline,
                name,
                name,
                List.of(needs),
                when,
                List.of("true"),
                List.of(),
                Map.of(),
                List.of(),
                null,
                Duration.ofMinutes(1),
                0,
                optional);
    }

    private static Status awaitPeer(CountDownLatch latch) {
        latch.countDown();
        try {
            return latch.await(5, TimeUnit.SECONDS) ? Status.SUCCEEDED : Status.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Status.FAILED;
        }
    }
}
