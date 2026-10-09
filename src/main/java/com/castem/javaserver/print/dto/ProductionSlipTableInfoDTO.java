package com.castem.javaserver.print.dto;

import java.time.LocalDate;

public record ProductionSlipTableInfoDTO(
    Integer id,
    String processName,
    LocalDate startDate,
    LocalDate actualStartDate,
    LocalDate endDate,
    LocalDate actualEndDate,
    String personInCharge,
    Integer rejectedQty,
    Integer passedQty,
    String remarks
) {}
