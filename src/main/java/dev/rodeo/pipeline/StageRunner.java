package dev.rodeo.pipeline;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface StageRunner {

    CompletableFuture<Status> run(StageSpec stage);
}
