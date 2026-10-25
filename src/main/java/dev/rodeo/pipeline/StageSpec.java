package dev.rodeo.pipeline;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record StageSpec(
        String pipeline,
        String name,
        String label,
        List<String> needs,
        Condition when,
        List<String> run,
        List<String> onFailure,
        Map<String, String> env,
        List<String> secrets,
        String dir,
        Duration timeout,
        int retries,
        boolean allowFailure) {

    public String id() {
        return pipeline + "/" + name;
    }
}
