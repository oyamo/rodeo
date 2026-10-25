package dev.rodeo.exec;

import dev.rodeo.model.PushEvent;
import dev.rodeo.pipeline.Elapsed;
import dev.rodeo.pipeline.PipelineFile;
import dev.rodeo.pipeline.PipelineParser;
import dev.rodeo.pipeline.RunContext;
import dev.rodeo.pipeline.RunEngine;
import dev.rodeo.pipeline.RunResult;
import dev.rodeo.pipeline.StageExecutor;
import dev.rodeo.pipeline.StageReport;
import dev.rodeo.pipeline.StageSpec;
import dev.rodeo.pipeline.Status;
import dev.rodeo.service.PushEventHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

@Component
public class PushCommandsHandler implements PushEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PushCommandsHandler.class);
    private static final String CONFIG_FILE = "rodeo.toml";
    private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(5);

    private final ExecutorService executor;
    private final ExecutorService stagePool;
    private final PipelineParser parser;
    private final StageExecutor stageExecutor;

    public PushCommandsHandler(
            @Qualifier("pushExecutor") ExecutorService executor,
            @Qualifier("stagePool") ExecutorService stagePool,
            PipelineParser parser,
            StageExecutor stageExecutor) {
        this.executor = executor;
        this.stagePool = stagePool;
        this.parser = parser;
        this.stageExecutor = stageExecutor;
    }

    @Override
    public void handle(PushEvent event) {
        if (event.branch().startsWith("refs/")) {
            return;
        }
        String runId = UUID.randomUUID().toString().substring(0, 8);
        executor.submit(() -> runForPush(runId, event));
    }

    private void runForPush(String runId, PushEvent event) {
        try {
            Path workspace = Files.createTempDirectory("rodeo-");
            try {
                runInWorkspace(runId, event, workspace);
            } finally {
                FileSystemUtils.deleteRecursively(workspace);
            }
        } catch (Exception e) {
            log.error("run {} aborted: {} on {}: {}", runId, event.repository(), event.branch(), e.getMessage());
        }
    }

    private void runInWorkspace(String runId, PushEvent event, Path workspace)
            throws IOException, InterruptedException {
        Instant started = Instant.now();
        log.info("run {} received {} push to {} on {}", runId, event.provider(), event.repository(), event.branch());

        clone(event, workspace);

        Path configFile = workspace.resolve(CONFIG_FILE);
        if (!Files.exists(configFile)) {
            log.info("run {} no {} in repository; nothing to run", runId, CONFIG_FILE);
            return;
        }

        PipelineFile file = parser.parse(Files.readString(configFile));
        List<StageSpec> stages = file.stagesFor(event.branch());
        if (stages.isEmpty()) {
            log.info("run {} no pipeline is triggered by branch {}; nothing to run", runId, event.branch());
            return;
        }

        log.info("run {} starting {} stage(s)", runId, stages.size());
        RunContext context = new RunContext(runId, workspace, event);
        RunResult result = new RunEngine(stagePool).execute(stages, stage -> stageExecutor.execute(stage, context));

        logSummary(runId, result, Duration.between(started, Instant.now()));
    }

    private static void logSummary(String runId, RunResult result, Duration total) {
        log.info("run {} {} in {}", runId, result.succeeded() ? "SUCCEEDED" : "FAILED", Elapsed.format(total));
        log.info("run {} {}", runId, String.format("%-26s %-24s %-10s %s", "STAGE", "LABEL", "STATUS", "TIME"));
        for (StageReport stage : result.stages()) {
            String time = stage.status() == Status.SKIPPED ? "-" : Elapsed.format(stage.elapsed());
            log.info("run {} {}", runId, String.format("%-26s %-24s %-10s %s",
                    stage.id(), stage.label(), stage.status(), time));
        }
    }

    private void clone(PushEvent event, Path workspace) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                "git", "clone", "--depth", "1", "--branch", event.branch(),
                "--", event.cloneUrl(), workspace.toString())
                .inheritIO();
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");

        Process process = builder.start();
        if (!process.waitFor(CLONE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("git clone timed out for " + event.repository());
        }
        if (process.exitValue() != 0) {
            throw new IOException("git clone failed for " + event.repository() + " with exit " + process.exitValue());
        }
    }
}
