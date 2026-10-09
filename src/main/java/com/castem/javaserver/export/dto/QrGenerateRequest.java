package com.castem.javaserver.export.dto;

public record QrGenerateRequest(
    Object payload,          // String or JSON Map/DTO
    Integer width,
    Integer height,
    String errorCorrection,  // "L", "M", "Q", "H"
    Integer margin           // Quiet zone border margin
) {
    public int getWidthOrDefault() {
        return width != null && width > 0 ? width : 200;
    }

    public int getHeightOrDefault() {
        return height != null && height > 0 ? height : 200;
    }

    public String getErrorCorrectionOrDefault() {
        return errorCorrection != null && !errorCorrection.isBlank() ? errorCorrection.toUpperCase() : "M";
    }

    public int getMarginOrDefault() {
        return margin != null && margin >= 0 ? margin : 1;
    }
}
