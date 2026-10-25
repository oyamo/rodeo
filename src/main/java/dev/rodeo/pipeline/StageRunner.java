package dev.rodeo.pipeline;

@FunctionalInterface
public interface StageRunner {

    Status run(StageSpec stage);
}
