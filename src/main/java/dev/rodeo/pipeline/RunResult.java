package dev.rodeo.pipeline;

import java.util.List;

public record RunResult(List<StageReport> stages, boolean succeeded) {

    public Status statusOf(String id) {
        return stages.stream()
                .filter(stage -> stage.id().equals(id))
                .findFirst()
                .orElseThrow()
                .status();
    }
}
