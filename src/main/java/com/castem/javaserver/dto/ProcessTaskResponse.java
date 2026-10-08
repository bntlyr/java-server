package com.castem.javaserver.dto;

import java.time.Instant;
import java.util.Map;

public record ProcessTaskResponse(
    String taskId,
    String taskType,
    String status,
    long executionDurationMs,
    Map<String, Object> result,
    String errorMessage,
    Instant completedAt
) {
    public static ProcessTaskResponse success(String taskId, String taskType, long durationMs, Map<String, Object> result) {
        return new ProcessTaskResponse(taskId, taskType, "SUCCESS", durationMs, result, null, Instant.now());
    }

    public static ProcessTaskResponse failure(String taskId, String taskType, long durationMs, String errorMessage) {
        return new ProcessTaskResponse(taskId, taskType, "FAILED", durationMs, null, errorMessage, Instant.now());
    }
}
