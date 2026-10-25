package dev.rodeo.pipeline;

import java.util.List;

public record PipelineSpec(String name, List<String> branches, List<StageSpec> stages) {
}
