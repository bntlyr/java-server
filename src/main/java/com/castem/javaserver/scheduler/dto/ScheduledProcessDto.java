package com.castem.javaserver.scheduler.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ScheduledProcessDto(
    Integer stepNo,
    Integer processId,
    String processName,
    String itemCode,
    Boolean isFabrication,
    LocalDate scheduledStartDate,
    LocalDate scheduledEndDate,
    BigDecimal leadTime,
    BigDecimal productionTime, // in hours
    BigDecimal neededQty,
    Integer previousStepNo,
    List<Integer> nextStepNos
) {
    public ScheduledProcessDto {
        if (isFabrication == null) isFabrication = false;
        if (leadTime == null) leadTime = BigDecimal.ZERO;
        if (productionTime == null) productionTime = BigDecimal.ZERO;
        if (neededQty == null) neededQty = BigDecimal.ZERO;
        if (nextStepNos == null) nextStepNos = List.of();
    }
}
