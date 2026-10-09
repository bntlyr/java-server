package com.castem.javaserver.export.dto;

public record PdfExportResponse(
    String status,        // "SUCCESS" or "FAILED"
    String filename,
    String relativePath,
    String absolutePath,
    Long fileSize,
    String mimeType,
    Long durationMs,
    String errorMessage
) {
    public static PdfExportResponse success(
        String filename,
        String relativePath,
        String absolutePath,
        long fileSize,
        long durationMs
    ) {
        return new PdfExportResponse(
            "SUCCESS",
            filename,
            relativePath,
            absolutePath,
            fileSize,
            "application/pdf",
            durationMs,
            null
        );
    }

    public static PdfExportResponse failure(String errorMessage, long durationMs) {
        return new PdfExportResponse(
            "FAILED",
            null,
            null,
            null,
            0L,
            "application/pdf",
            durationMs,
            errorMessage
        );
    }
}
