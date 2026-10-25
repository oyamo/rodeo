package dev.rodeo.pipeline;

import java.util.List;

public record PipelineFile(List<PipelineSpec> pipelines) {

    public List<StageSpec> stagesFor(String branch) {
        return pipelines.stream()
                .filter(pipeline -> pipeline.branches().contains(branch))
                .flatMap(pipeline -> pipeline.stages().stream())
                .toList();
    }
}
