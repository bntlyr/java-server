package com.castem.javaserver.print.dto;

import java.time.LocalDate;

public record PrintTrialCardRequestDTO(
    Integer itemId,
    Integer slipId,
    String type,              // "ORIGINAL", "REWORK", "EVALUATION"
    LocalDate createdAt,
    String trialLotNo,
    String itemCode,
    String rawMaterial,
    String trialPurpose,
    String trialProcess,
    String orderNo,
    String lotNo,
    String watermarkText
) {
    public String getTypeOrDefault() {
        return type != null && !type.isBlank() ? type.toUpperCase() : "ORIGINAL";
    }
}
