package com.castem.javaserver.scheduler.controller;

import com.castem.javaserver.scheduler.dto.ScheduleRequest;
import com.castem.javaserver.scheduler.dto.ScheduleResponse;
import com.castem.javaserver.scheduler.service.ProductionSchedulerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scheduler")
public class ProductionSchedulerController {

    private final ProductionSchedulerService schedulerService;

    public ProductionSchedulerController(ProductionSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @PostMapping("/schedule")
    public ResponseEntity<ScheduleResponse> createSchedule(@RequestBody ScheduleRequest request) {
        ScheduleResponse response = schedulerService.schedule(request);
        return ResponseEntity.ok(response);
    }
}
