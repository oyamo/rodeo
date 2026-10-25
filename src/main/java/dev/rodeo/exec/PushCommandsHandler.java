package dev.rodeo.exec;

import dev.rodeo.config.ExecProperties;
import dev.rodeo.config.GitProperties;
import dev.rodeo.config.ToolProperties;
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
import dev.rodeo.status.CommitState;
import dev.rodeo.status.StatusReporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

@Component
public class PushCommandsHandler implements PushEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PushCommandsHandler.class);
    private static final String CONFIG_FILE = "rodeo.toml";
    private static final String STATUS_PREFIX = "rodeo/";
    private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(5);

    private final ExecutorService executor;
    private final ExecutorService stagePool;
    private final PipelineParser parser;
    private final StageExecutor stageExecutor;
    private final StatusReporter statusReporter;
    private final ExecProperties execProperties;
    private final ToolProperties tools;
    private final GitProperties git;

    public PushCommandsHandler(
            @Qualifier("pushExecutor") ExecutorService executor,
            @Qualifier("stagePool") ExecutorService stagePool,
            PipelineParser parser,
            StageExecutor stageExecutor,
            StatusReporter statusReporter,
            ExecProperties execProperties,
            ToolProperties tools,
            GitProperties git) {
        this.executor = executor;
        this.stagePool = stagePool;
        this.parser = parser;
        this.stageExecutor = stageExecutor;
        this.statusReporter = statusReporter;
        this.execProperties = execProperties;
        this.tools = tools;
        this.git = git;
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
            Path credentials = git.token() == null || git.sshKey() != null ? null : writeCredentials(event);
            try {
                runInWorkspace(runId, event, workspace, credentials);
            } finally {
                FileSystemUtils.deleteRecursively(workspace);
                if (credentials != null) {
                    Files.deleteIfExists(credentials);
                }
            }
        } catch (Exception e) {
            log.error("run {} aborted for {} on {}: {}", runId, event.repository(), event.branch(), e.getMessage());
            statusReporter.report(event, STATUS_PREFIX + "run", CommitState.FAILURE,
                    "Run " + runId + " aborted: " + e.getMessage());
        }
    }

    private Path writeCredentials(PushEvent event) throws IOException {
        Path file = Files.createTempFile("rodeo-credentials-", "");
        Files.writeString(file, GitAccess.credentialLine(event.cloneUrl(), git.token()) + "\n");
        return file;
    }

    private void runInWorkspace(String runId, PushEvent event, Path workspace, Path credentials)
            throws IOException, InterruptedException {
        Instant started = Instant.now();
        log.info("run {} received {} push to {} on {}", runId, event.provider(), event.repository(), event.branch());

        clone(event, workspace, credentials);

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

        Map<String, List<StageSpec>> pipelines = stages.stream()
                .collect(Collectors.groupingBy(StageSpec::pipeline, LinkedHashMap::new, Collectors.toList()));
        pipelines.keySet().forEach(name -> statusReporter.report(
                event, STATUS_PREFIX + name, CommitState.PENDING, "Run " + runId + " started"));

        log.info("run {} starting {} stage(s)", runId, stages.size());
        RunContext context = new RunContext(runId, workspace, event);
        RunResult result = new RunEngine(stagePool).execute(stages, stage -> stageExecutor.execute(stage, context));

        logSummary(runId, result, Duration.between(started, Instant.now()));
        reportPipelines(event, stages, result);
    }

    private void reportPipelines(PushEvent event, List<StageSpec> stages, RunResult result) {
        Map<String, StageSpec> specs = stages.stream()
                .collect(Collectors.toMap(StageSpec::id, stage -> stage));
        Map<String, List<StageSpec>> pipelines = stages.stream()
                .collect(Collectors.groupingBy(StageSpec::pipeline, LinkedHashMap::new, Collectors.toList()));

        for (String pipeline : pipelines.keySet()) {
            StageReport failure = result.stages().stream()
                    .filter(report -> report.status() == Status.FAILED)
                    .filter(report -> !specs.get(report.id()).allowFailure())
                    .filter(report -> specs.get(report.id()).pipeline().equals(pipeline))
                    .findFirst()
                    .orElse(null);
            String context = STATUS_PREFIX + pipeline;
            if (failure == null) {
                statusReporter.report(event, context, CommitState.SUCCESS, "All stages succeeded");
            } else {
                statusReporter.report(event, context, CommitState.FAILURE, "Failed: " + failure.label());
            }
        }
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

    private void clone(PushEvent event, Path workspace, Path credentials) throws IOException, InterruptedException {
        List<String> argv = new ArrayList<>();
        argv.add(tools.git());
        argv.addAll(GitAccess.options(git, credentials));
        argv.addAll(List.of("clone", "--depth", "1", "--branch", event.branch(),
                "--", GitAccess.cloneUrl(git, event.cloneUrl()), workspace.toString()));
        ProcessBuilder builder = new ProcessBuilder(argv).inheritIO();
        builder.environment().clear();
        builder.environment().put("PATH", execProperties.path());
        builder.environment().put("HOME", execProperties.home());
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
