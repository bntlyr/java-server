package com.castem.javaserver.controller;

import com.castem.javaserver.dto.ProcessTaskRequest;
import com.castem.javaserver.dto.ProcessTaskResponse;
import com.castem.javaserver.service.HeavyProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/process")
public class HeavyProcessingController {

    private final HeavyProcessingService heavyProcessingService;

    public HeavyProcessingController(HeavyProcessingService heavyProcessingService) {
        this.heavyProcessingService = heavyProcessingService;
    }

    /**
     * Synchronous heavy task processing endpoint.
     */
    @PostMapping
    public ResponseEntity<ProcessTaskResponse> processSync(@RequestBody ProcessTaskRequest request) {
        ProcessTaskResponse response = heavyProcessingService.executeTask(request);
        if ("FAILED".equals(response.status())) {
            return ResponseEntity.internalServerError().body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Asynchronous task dispatch endpoint.
     */
    @PostMapping("/async")
    public CompletableFuture<ResponseEntity<ProcessTaskResponse>> processAsync(@RequestBody ProcessTaskRequest request) {
        return heavyProcessingService.executeTaskAsync(request)
            .thenApply(response -> {
                if ("FAILED".equals(response.status())) {
                    return ResponseEntity.internalServerError().body(response);
                }
                return ResponseEntity.ok(response);
            });
    }
}
