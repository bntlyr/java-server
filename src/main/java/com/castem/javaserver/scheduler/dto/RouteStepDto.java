package com.castem.javaserver.scheduler.dto;

import java.math.BigDecimal;

public record RouteStepDto(
    Integer stepNo,
    Integer processId,
    String processName,
    BigDecimal leadTime,
    String leadTimeUnit, // "Minutes", "Hours", "Days"
    BigDecimal prepTime,
    String prepTimeUnit,
    BigDecimal cureTime,
    String cureTimeUnit,
    String durationType, // "Batch" or "Piece"
    Boolean hasPacking,
    BigDecimal maxPcsPerDay,
    BigDecimal maxWeightPerDay,
    BigDecimal qtyPerTree,
    BigDecimal qtyPerShot
) {
    public RouteStepDto {
        if (leadTime == null) leadTime = BigDecimal.ZERO;
        if (leadTimeUnit == null || leadTimeUnit.isBlank()) leadTimeUnit = "Minutes";
        if (prepTime == null) prepTime = BigDecimal.ZERO;
        if (prepTimeUnit == null || prepTimeUnit.isBlank()) prepTimeUnit = "Minutes";
        if (cureTime == null) cureTime = BigDecimal.ZERO;
        if (cureTimeUnit == null || cureTimeUnit.isBlank()) cureTimeUnit = "Hours";
        if (durationType == null || durationType.isBlank()) durationType = "Batch";
        if (hasPacking == null) hasPacking = false;
        if (maxPcsPerDay == null) maxPcsPerDay = BigDecimal.ZERO;
        if (maxWeightPerDay == null) maxWeightPerDay = BigDecimal.ZERO;
        if (qtyPerTree == null) qtyPerTree = BigDecimal.ZERO;
        if (qtyPerShot == null) qtyPerShot = BigDecimal.ZERO;
    }
}
