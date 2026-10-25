package dev.rodeo.pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunEngine {

    private static final Logger log = LoggerFactory.getLogger(RunEngine.class);

    private final ExecutorService pool;

    public RunEngine(ExecutorService pool) {
        this.pool = pool;
    }

    public RunResult execute(List<StageSpec> stages, StageRunner runner) throws InterruptedException {
        Map<String, StageSpec> byId = new LinkedHashMap<>();
        Map<String, Status> status = new HashMap<>();
        Map<String, Duration> elapsed = new HashMap<>();
        for (StageSpec stage : stages) {
            byId.put(stage.id(), stage);
            status.put(stage.id(), Status.PENDING);
        }

        BlockingQueue<Finished> finished = new LinkedBlockingQueue<>();
        int running = 0;
        while (true) {
            running += startReady(byId, status, runner, finished);
            if (running == 0) {
                break;
            }
            Finished done = finished.take();
            status.put(done.id(), done.status());
            elapsed.put(done.id(), done.elapsed());
            running--;
        }

        List<StageReport> reports = byId.values().stream()
                .map(stage -> new StageReport(
                        stage.id(),
                        stage.label(),
                        status.get(stage.id()),
                        elapsed.getOrDefault(stage.id(), Duration.ZERO)))
                .toList();
        boolean succeeded = reports.stream()
                .noneMatch(report -> report.status() == Status.FAILED && !byId.get(report.id()).allowFailure());
        return new RunResult(reports, succeeded);
    }

    private int startReady(
            Map<String, StageSpec> byId,
            Map<String, Status> status,
            StageRunner runner,
            BlockingQueue<Finished> finished) {
        int started = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (StageSpec stage : byId.values()) {
                if (status.get(stage.id()) != Status.PENDING || !allNeedsTerminal(stage, status)) {
                    continue;
                }
                changed = true;
                if (shouldRun(stage, byId, status)) {
                    status.put(stage.id(), Status.RUNNING);
                    started++;
                    pool.execute(() -> finished.add(run(stage, runner)));
                } else {
                    status.put(stage.id(), Status.SKIPPED);
                }
            }
        }
        return started;
    }

    private static boolean allNeedsTerminal(StageSpec stage, Map<String, Status> status) {
        return stage.needs().stream().allMatch(need -> status.getOrDefault(need, Status.SKIPPED).isTerminal());
    }

    private static boolean shouldRun(StageSpec stage, Map<String, StageSpec> byId, Map<String, Status> status) {
        return switch (stage.when()) {
            case SUCCESS -> stage.needs().stream().allMatch(need -> satisfied(need, byId, status));
            case FAILURE -> stage.needs().stream().anyMatch(need -> status.get(need) == Status.FAILED);
            case ALWAYS -> true;
        };
    }

    private static boolean satisfied(String need, Map<String, StageSpec> byId, Map<String, Status> status) {
        Status outcome = status.getOrDefault(need, Status.SKIPPED);
        return outcome == Status.SUCCEEDED || (outcome == Status.FAILED && byId.get(need).allowFailure());
    }

    private static Finished run(StageSpec stage, StageRunner runner) {
        Instant start = Instant.now();
        Status outcome;
        try {
            outcome = runner.run(stage);
        } catch (RuntimeException e) {
            log.error("Stage {} failed unexpectedly", stage.id(), e);
            outcome = Status.FAILED;
        }
        return new Finished(stage.id(), outcome, Duration.between(start, Instant.now()));
    }

    private record Finished(String id, Status status, Duration elapsed) {
    }
}
