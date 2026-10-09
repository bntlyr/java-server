package com.castem.javaserver.export.service;

import com.castem.javaserver.export.dto.PdfConfigDto;
import com.castem.javaserver.export.dto.PdfExportRequest;
import com.castem.javaserver.export.dto.PdfExportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PdfExportService {

    private static final Logger log = LoggerFactory.getLogger(PdfExportService.class);

    private final PdfRenderEngine pdfRenderEngine;
    private final StorageService storageService;

    public PdfExportService(PdfRenderEngine pdfRenderEngine, StorageService storageService) {
        this.pdfRenderEngine = pdfRenderEngine;
        this.storageService = storageService;
    }

    /**
     * Renders a PDF according to request specification and stores it in the configured bucket directory.
     */
    public PdfExportResponse generateAndSavePdf(PdfExportRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            PdfConfigDto config = request.config() != null 
                ? request.config() 
                : new PdfConfigDto("A4", "PORTRAIT", null, "HELVETICA", 10.0f, "#111827", null, null, null);

            String rawFilename = config.filename();
            String filename = (rawFilename != null && !rawFilename.isBlank()) 
                ? (rawFilename.endsWith(".pdf") ? rawFilename : rawFilename + ".pdf")
                : "export_" + UUID.randomUUID() + ".pdf";

            String destinationSubPath = config.getDestinationSubPathOrDefault();

            log.info("Starting PDF generation for [{}] to destination [{}]", filename, destinationSubPath);

            byte[] pdfBytes = pdfRenderEngine.renderPdf(request);

            StorageService.StorageResult storageResult = storageService.saveBuffer(
                destinationSubPath,
                filename,
                pdfBytes
            );

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully generated and saved PDF [{}] in {} ms", filename, duration);

            return PdfExportResponse.success(
                filename,
                storageResult.relativePath(),
                storageResult.absolutePath(),
                storageResult.fileSize(),
                duration
            );
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to generate PDF: {}", ex.getMessage(), ex);
            return PdfExportResponse.failure(ex.getMessage(), duration);
        }
    }

    /**
     * Directly renders and returns the raw PDF binary bytes without disk writing (for streaming responses).
     */
    public byte[] generatePdfStream(PdfExportRequest request) throws Exception {
        return pdfRenderEngine.renderPdf(request);
    }
}
