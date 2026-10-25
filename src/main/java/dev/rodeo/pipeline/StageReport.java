package dev.rodeo.pipeline;

import java.time.Duration;

public record StageReport(String id, String label, Status status, Duration elapsed) {
}
