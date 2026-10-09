package com.castem.javaserver.scheduler.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CapacityWarningDto(
    LocalDate requestedDate,
    LocalDate adjustedDate,
    Integer processId,
    String processName,
    String lotNo,
    String message,
    BigDecimal requestedPcs,
    BigDecimal maxPcsPerDay,
    BigDecimal requestedWeight,
    BigDecimal maxWeightPerDay
) {}
