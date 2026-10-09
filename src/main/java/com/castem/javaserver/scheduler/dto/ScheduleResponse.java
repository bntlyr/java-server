package com.castem.javaserver.scheduler.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ScheduleResponse(
    String orderNo,
    Integer salesOrderId,
    String productionId,
    BigDecimal netRequiredQty,
    BigDecimal totalProductionQty,
    Integer lotCount,
    LocalDate requestedEtd,
    LocalDate targetEndDate,
    LocalDate scheduledStartDate,
    LocalDate scheduledEndDate,
    BigDecimal totalWorkingDays,
    Boolean isDelayed,
    Integer delayWorkingDays,
    String schedulingMode, // "BACKWARD" or "FORWARD"
    List<ScheduledSlipDto> slips,
    List<DailyLoadDto> resultingLoads,
    List<CapacityWarningDto> capacityWarnings
) {
    public ScheduleResponse {
        if (netRequiredQty == null) netRequiredQty = BigDecimal.ZERO;
        if (totalProductionQty == null) totalProductionQty = BigDecimal.ZERO;
        if (lotCount == null) lotCount = 0;
        if (totalWorkingDays == null) totalWorkingDays = BigDecimal.ZERO;
        if (isDelayed == null) isDelayed = false;
        if (delayWorkingDays == null) delayWorkingDays = 0;
        if (schedulingMode == null) schedulingMode = "BACKWARD";
        if (slips == null) slips = List.of();
        if (resultingLoads == null) resultingLoads = List.of();
        if (capacityWarnings == null) capacityWarnings = List.of();
    }
}
