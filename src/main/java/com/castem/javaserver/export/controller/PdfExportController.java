package com.castem.javaserver.export.controller;

import com.castem.javaserver.export.dto.*;
import com.castem.javaserver.export.service.PdfExportService;
import com.castem.javaserver.export.service.QrCodeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/export")
public class PdfExportController {

    private final PdfExportService pdfExportService;
    private final QrCodeService qrCodeService;

    public PdfExportController(PdfExportService pdfExportService, QrCodeService qrCodeService) {
        this.pdfExportService = pdfExportService;
        this.qrCodeService = qrCodeService;
    }

    /**
     * Generates a PDF according to the dynamic payload specification and saves it to the bucket.
     * Returns JSON metadata including the relative path and file size.
     */
    @PostMapping("/pdf")
    public ResponseEntity<PdfExportResponse> generatePdf(@RequestBody PdfExportRequest request) {
        PdfExportResponse response = pdfExportService.generateAndSavePdf(request);
        if ("FAILED".equals(response.status())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Generates and directly streams the raw PDF binary bytes to the caller.
     */
    @PostMapping(value = "/pdf/stream", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> streamPdf(@RequestBody PdfExportRequest request) {
        try {
            byte[] pdfBytes = pdfExportService.generatePdfStream(request);
            String filename = request.config() != null && request.config().filename() != null
                ? request.config().filename()
                : "document.pdf";

            if (!filename.endsWith(".pdf")) {
                filename += ".pdf";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("inline", filename);
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Standalone endpoint to generate a QR Code from string or JSON payload as a Base64 data URL.
     */
    @PostMapping("/qr")
    public ResponseEntity<QrGenerateResponse> generateQr(@RequestBody QrGenerateRequest request) {
        try {
            String base64 = qrCodeService.generateQrBase64DataUrl(
                request.payload(),
                request.getWidthOrDefault(),
                request.getHeightOrDefault(),
                request.getErrorCorrectionOrDefault(),
                request.getMarginOrDefault()
            );
            return ResponseEntity.ok(QrGenerateResponse.success(base64, request.getWidthOrDefault(), request.getHeightOrDefault()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(QrGenerateResponse.failure(ex.getMessage()));
        }
    }

    /**
     * Standalone endpoint to generate and stream a QR Code image as raw PNG binary.
     */
    @PostMapping(value = "/qr/png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> streamQrPng(@RequestBody QrGenerateRequest request) {
        try {
            byte[] pngBytes = qrCodeService.generateQrPngBytes(
                request.payload(),
                request.getWidthOrDefault(),
                request.getHeightOrDefault(),
                request.getErrorCorrectionOrDefault(),
                request.getMarginOrDefault()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(pngBytes.length);

            return new ResponseEntity<>(pngBytes, headers, HttpStatus.OK);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
