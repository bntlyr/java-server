package com.castem.javaserver.scheduler.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ScheduleRequest(
    String orderNo,
    Integer salesOrderId,
    String itemCode,
    BigDecimal orderQty,
    BigDecimal allocatedQty,
    BigDecimal rejectionRate,
    BigDecimal autoSplitQty,
    BigDecimal stdItemWt,
    BigDecimal castingWt,
    LocalDate requestedEtd,
    LocalDate startDateOverride,
    Integer maxConcurrentLots,
    Boolean includeSaturday,
    BigDecimal maxWorkingHoursPerDay,
    List<LocalDate> holidays,
    List<DailyLoadDto> existingLoads,
    List<RouteStepDto> itemRoutes,
    Boolean hasFabrication,
    String fabCode,
    List<RouteStepDto> fabRoutes
) {
    public ScheduleRequest {
        if (orderQty == null) orderQty = BigDecimal.ZERO;
        if (allocatedQty == null) allocatedQty = BigDecimal.ZERO;
        if (rejectionRate == null) rejectionRate = BigDecimal.ZERO;
        if (autoSplitQty == null || autoSplitQty.compareTo(BigDecimal.ZERO) <= 0) autoSplitQty = new BigDecimal("500");
        if (stdItemWt == null) stdItemWt = BigDecimal.ZERO;
        if (castingWt == null) castingWt = BigDecimal.ZERO;
        if (maxConcurrentLots == null || maxConcurrentLots <= 0) maxConcurrentLots = 1;
        if (includeSaturday == null) includeSaturday = true;
        if (maxWorkingHoursPerDay == null || maxWorkingHoursPerDay.compareTo(BigDecimal.ZERO) <= 0) {
            maxWorkingHoursPerDay = new BigDecimal("14.0");
        }
        if (holidays == null) holidays = List.of();
        if (existingLoads == null) existingLoads = List.of();
        if (itemRoutes == null) itemRoutes = List.of();
        if (hasFabrication == null) hasFabrication = false;
        if (fabRoutes == null) fabRoutes = List.of();
    }
}
