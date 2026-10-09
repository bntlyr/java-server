package com.castem.javaserver.scheduler.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyLoadDto(
    LocalDate date,
    Integer processId,
    String processName,
    BigDecimal scheduledPcs,
    BigDecimal scheduledWeight
) {
    public DailyLoadDto {
        if (scheduledPcs == null) scheduledPcs = BigDecimal.ZERO;
        if (scheduledWeight == null) scheduledWeight = BigDecimal.ZERO;
    }
}
