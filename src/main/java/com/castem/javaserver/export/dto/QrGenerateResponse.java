package com.castem.javaserver.export.dto;

public record QrGenerateResponse(
    String status,
    String base64DataUrl,
    Integer width,
    Integer height,
    String errorMessage
) {
    public static QrGenerateResponse success(String base64DataUrl, int width, int height) {
        return new QrGenerateResponse("SUCCESS", base64DataUrl, width, height, null);
    }

    public static QrGenerateResponse failure(String errorMessage) {
        return new QrGenerateResponse("FAILED", null, 0, 0, errorMessage);
    }
}
