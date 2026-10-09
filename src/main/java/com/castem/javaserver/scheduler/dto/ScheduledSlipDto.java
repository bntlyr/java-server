package com.castem.javaserver.scheduler.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ScheduledSlipDto(
    String lotNo,
    String orderNo,
    String type, // "ORIGINAL", "REWORK", etc.
    BigDecimal totalQty,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal totalDays,
    LocalDate fabStartDate,
    LocalDate fabEndDate,
    BigDecimal fabTotalDays,
    List<ScheduledProcessDto> processes
) {
    public ScheduledSlipDto {
        if (type == null || type.isBlank()) type = "ORIGINAL";
        if (totalQty == null) totalQty = BigDecimal.ZERO;
        if (totalDays == null) totalDays = BigDecimal.ZERO;
        if (fabTotalDays == null) fabTotalDays = BigDecimal.ZERO;
        if (processes == null) processes = List.of();
    }
}
