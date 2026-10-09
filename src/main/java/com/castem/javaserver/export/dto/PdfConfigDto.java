package com.castem.javaserver.export.dto;

public record PdfConfigDto(
    String pageSize,          // "A4", "LETTER", "LEGAL", "A3"
    String orientation,       // "PORTRAIT", "LANDSCAPE"
    PdfMarginsDto margins,
    String defaultFontFamily, // "HELVETICA", "TIMES_ROMAN", "COURIER"
    Float defaultFontSize,
    String defaultTextColor,
    String filename,
    String destinationSubPath,
    WatermarkConfigDto watermark
) {
    public String getPageSizeOrDefault() {
        return pageSize != null && !pageSize.isBlank() ? pageSize.toUpperCase() : "A4";
    }

    public String getOrientationOrDefault() {
        return orientation != null && !orientation.isBlank() ? orientation.toUpperCase() : "PORTRAIT";
    }

    public String getDefaultFontFamilyOrDefault() {
        return defaultFontFamily != null && !defaultFontFamily.isBlank() ? defaultFontFamily.toUpperCase() : "HELVETICA";
    }

    public float getDefaultFontSizeOrDefault() {
        return defaultFontSize != null && defaultFontSize > 0 ? defaultFontSize : 10.0f;
    }

    public String getDefaultTextColorOrDefault() {
        return defaultTextColor != null && !defaultTextColor.isBlank() ? defaultTextColor : "#111827";
    }

    public String getDestinationSubPathOrDefault() {
        return destinationSubPath != null && !destinationSubPath.isBlank() 
            ? destinationSubPath 
            : "private/exports/pdf";
    }
}
