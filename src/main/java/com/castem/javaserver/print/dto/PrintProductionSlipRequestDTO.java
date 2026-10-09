package com.castem.javaserver.print.dto;

import java.time.LocalDate;
import java.util.List;

public record PrintProductionSlipRequestDTO(
    Integer slipId,
    String type,                  // "ORIGINAL", "REWORK", "REMAKE", "TRIAL"
    
    // Header Info
    LocalDate requestedETD,
    String itemCode,
    String orderNo,
    Integer orderQty,
    String rawMaterial,
    String chargeNo,
    String trialLotNo,

    // Packing Instructions
    String boxLayer,
    String plasticSize,
    Integer plasticQty,
    Integer topLayerQty,
    Integer btmLayerQty,
    Integer maxQty,
    Integer maxWt,
    String packingRemarks,

    // Side Info Panel
    String imageUrl,
    String productionQty,
    String scheduledTrees,
    String coatingPattern,
    String treeMark,
    String similarProducts,
    Float castingTemp,
    Float moldTemp,
    Float castingWt,
    Integer pcsPerTree,
    String slipSize,
    String trialRemarks,
    String salesRemarks,
    String qrCode,
    String reworkLotNo,
    String createdBy,
    String watermarkText,

    // Table Route Details
    List<ProductionSlipTableInfoDTO> processTableDetails
) {
    public String getTypeOrDefault() {
        return type != null && !type.isBlank() ? type.toUpperCase() : "ORIGINAL";
    }

    public List<ProductionSlipTableInfoDTO> getProcessTableDetailsOrDefault() {
        return processTableDetails != null ? processTableDetails : List.of();
    }
}
