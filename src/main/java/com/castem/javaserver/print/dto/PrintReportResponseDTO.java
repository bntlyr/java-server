package com.castem.javaserver.print.dto;

public record PrintReportResponseDTO(
    boolean success,
    String filename,
    String downloadUrl,
    String relativePath,
    Long fileSize,
    Long durationMs,
    String errorMessage
) {
    public static PrintReportResponseDTO success(
        String filename,
        String downloadUrl,
        String relativePath,
        long fileSize,
        long durationMs
    ) {
        return new PrintReportResponseDTO(
            true,
            filename,
            downloadUrl,
            relativePath,
            fileSize,
            durationMs,
            null
        );
    }

    public static PrintReportResponseDTO failure(String errorMessage, long durationMs) {
        return new PrintReportResponseDTO(
            false,
            null,
            null,
            null,
            0L,
            durationMs,
            errorMessage
        );
    }
}
