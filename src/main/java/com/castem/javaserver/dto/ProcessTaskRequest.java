package com.castem.javaserver.dto;

import java.util.Map;

public record ProcessTaskRequest(
    String taskId,
    String taskType,
    Map<String, Object> parameters,
    Map<String, Object> metadata
) {}
