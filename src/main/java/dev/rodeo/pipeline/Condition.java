package dev.rodeo.pipeline;

public enum Condition {
    SUCCESS,
    FAILURE,
    ALWAYS;

    public static Condition parse(String value) {
        return switch (value) {
            case "success" -> SUCCESS;
            case "failure" -> FAILURE;
            case "always" -> ALWAYS;
            default -> throw new PipelineException("when must be success, failure or always, got '" + value + "'");
        };
    }
}
