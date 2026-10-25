package dev.rodeo.pipeline;

import dev.rodeo.model.PushEvent;
import java.nio.file.Path;

public record RunContext(String runId, Path workspace, PushEvent event) {
}
