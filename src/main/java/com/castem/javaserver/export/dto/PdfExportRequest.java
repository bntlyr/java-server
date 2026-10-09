package com.castem.javaserver.export.dto;

import java.util.List;

public record PdfExportRequest(
    PdfConfigDto config,
    List<PdfElementDto> elements
) {
    public List<PdfElementDto> getElementsOrDefault() {
        return elements != null ? elements : List.of();
    }
}
