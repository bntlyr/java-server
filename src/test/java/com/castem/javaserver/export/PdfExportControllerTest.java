package com.castem.javaserver.export;

import com.castem.javaserver.export.controller.PdfExportController;
import com.castem.javaserver.export.dto.*;
import com.castem.javaserver.export.service.PdfExportService;
import com.castem.javaserver.export.service.PdfRenderEngine;
import com.castem.javaserver.export.service.QrCodeService;
import com.castem.javaserver.export.service.StorageService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PdfExportControllerTest {

    private PdfExportController controller;
    private StorageService storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(tempDir.toString());
        QrCodeService qrCodeService = new QrCodeService(new ObjectMapper());
        PdfRenderEngine pdfRenderEngine = new PdfRenderEngine(qrCodeService, storageService);
        PdfExportService pdfExportService = new PdfExportService(pdfRenderEngine, storageService);
        controller = new PdfExportController(pdfExportService, qrCodeService);
    }

    @Test
    void testControllerGeneratePdfSuccess() {
        PdfConfigDto config = new PdfConfigDto(
            "A4",
            "PORTRAIT",
            null,
            "HELVETICA",
            10f,
            "#111827",
            "controller_test.pdf",
            "test/exports",
            new WatermarkConfigDto("CONFIDENTIAL", null, 40f, "#888888", 0.12f, 45f, "UNDER_CONTENT")
        );

        List<PdfTableCellDto> headers = List.of(
            PdfTableCellDto.header("Code", "CENTER"),
            PdfTableCellDto.header("Description", "LEFT")
        );

        List<List<PdfTableCellDto>> rows = List.of(
            List.of(
                PdfTableCellDto.text("ITEM-01", "CENTER", true),
                PdfTableCellDto.text("High Precision Anodized Flange", "LEFT", false)
            )
        );

        List<PdfElementDto> elements = List.of(
            PdfElementDto.text("CONTROLLER TEST INVOICE", 18f, true, "CENTER"),
            PdfElementDto.table(List.of(30f, 70f), 5f, 6f, true, true, headers, rows)
        );

        PdfExportRequest request = new PdfExportRequest(config, elements);

        ResponseEntity<PdfExportResponse> response = controller.generatePdf(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().status());
        assertEquals("controller_test.pdf", response.getBody().filename());
        assertTrue(storageService.fileExists(response.getBody().relativePath()));
    }

    @Test
    void testControllerStreamPdfSuccess() {
        PdfExportRequest request = new PdfExportRequest(
            new PdfConfigDto("A4", "PORTRAIT", null, null, null, null, "stream_test.pdf", null, null),
            List.of(PdfElementDto.text("Streamed Raw PDF", 14f, false, "LEFT"))
        );

        ResponseEntity<byte[]> response = controller.streamPdf(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length > 0);
    }

    @Test
    void testControllerGenerateQrSuccess() {
        QrGenerateRequest request = new QrGenerateRequest(
            Map.of("code", "LOT-9923", "qty", 500),
            120,
            120,
            "M",
            1
        );

        ResponseEntity<QrGenerateResponse> response = controller.generateQr(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().status());
        assertNotNull(response.getBody().base64DataUrl());
        assertTrue(response.getBody().base64DataUrl().startsWith("data:image/png;base64,"));
    }
}
