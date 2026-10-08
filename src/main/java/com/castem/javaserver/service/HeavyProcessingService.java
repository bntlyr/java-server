package com.castem.javaserver.service;

import com.castem.javaserver.dto.ProcessTaskRequest;
import com.castem.javaserver.dto.ProcessTaskResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class HeavyProcessingService {

    private static final Logger log = LoggerFactory.getLogger(HeavyProcessingService.class);

    private final JdbcTemplate jdbcTemplate;

    public HeavyProcessingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Executes a heavy processing task synchronously.
     */
    public ProcessTaskResponse executeTask(ProcessTaskRequest request) {
        String taskId = request.taskId() != null ? request.taskId() : UUID.randomUUID().toString();
        String taskType = request.taskType() != null ? request.taskType() : "DEFAULT_PROCESSING";
        long startTime = System.currentTimeMillis();

        log.info("Starting processing task [{}] of type [{}]", taskId, taskType);

        try {
            Map<String, Object> resultMap = processInternal(taskType, request.parameters());
            long duration = System.currentTimeMillis() - startTime;
            log.info("Completed processing task [{}] in {} ms", taskId, duration);
            return ProcessTaskResponse.success(taskId, taskType, duration, resultMap);
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed processing task [{}]: {}", taskId, ex.getMessage(), ex);
            return ProcessTaskResponse.failure(taskId, taskType, duration, ex.getMessage());
        }
    }

    /**
     * Executes a heavy processing task asynchronously.
     */
    public CompletableFuture<ProcessTaskResponse> executeTaskAsync(ProcessTaskRequest request) {
        return CompletableFuture.supplyAsync(() -> executeTask(request));
    }

    private Map<String, Object> processInternal(String taskType, Map<String, Object> parameters) {
        Map<String, Object> result = new HashMap<>();

        switch (taskType) {
            case "DATABASE_METRICS_AGGREGATION" -> {
                // Example of direct high-performance SQL query via JdbcTemplate
                Integer userCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM \"User\" WHERE \"deletedAt\" IS NULL",
                    Integer.class
                );
                result.put("activeUsersCount", userCount != null ? userCount : 0);
                result.put("status", "completed");
            }
            case "DATA_TRANSFORMATION" -> {
                result.put("transformedRecords", parameters != null ? parameters.size() : 0);
                result.put("status", "completed");
            }
            default -> {
                result.put("message", "Task " + taskType + " executed successfully");
                result.put("status", "completed");
            }
        }

        return result;
    }
}
