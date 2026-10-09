package com.castem.javaserver.export;

import com.castem.javaserver.export.dto.*;
import com.castem.javaserver.export.service.PdfExportService;
import com.castem.javaserver.export.service.PdfRenderEngine;
import com.castem.javaserver.export.service.QrCodeService;
import com.castem.javaserver.export.service.StorageService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PdfExportServiceTest {

    private PdfExportService pdfExportService;
    private StorageService storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(tempDir.toString());
        QrCodeService qrCodeService = new QrCodeService(new ObjectMapper());
        PdfRenderEngine pdfRenderEngine = new PdfRenderEngine(qrCodeService, storageService);
        pdfExportService = new PdfExportService(pdfRenderEngine, storageService);
    }

    @Test
    void testGeneratePdfWithStyledTextTablesAndQr() {
        PdfConfigDto config = new PdfConfigDto(
            "A4",
            "PORTRAIT",
            new PdfMarginsDto(30f, 30f, 30f, 30f),
            "HELVETICA",
            10.0f,
            "#1F2937",
            "test_sample_output.pdf",
            "test/exports",
            new WatermarkConfigDto(
                "TEST WATERMARK",
                null,
                45f,
                "#EF4444",
                0.15f,
                45f,
                "UNDER_CONTENT"
            )
        );

        List<PdfTableCellDto> headers = List.of(
            PdfTableCellDto.header("#", "CENTER"),
            PdfTableCellDto.header("Process Name", "LEFT"),
            PdfTableCellDto.header("Lot No", "CENTER"),
            PdfTableCellDto.header("QR Code", "CENTER")
        );

        List<List<PdfTableCellDto>> rows = List.of(
            List.of(
                PdfTableCellDto.text("1", "CENTER", false),
                PdfTableCellDto.text("Wax Injection & Pattern Tree Assembly", "LEFT", false),
                PdfTableCellDto.text("LOT-2026-001", "CENTER", true),
                PdfTableCellDto.qrCode(Map.of("step", 1, "lot", "LOT-001"), 35, 35)
            ),
            List.of(
                PdfTableCellDto.text("2", "CENTER", false),
                new PdfTableCellDto("Slurry Dipping & Stuccoing", "TEXT", null, null, null, null, null, null, 10f, null, null, null, false, true, true, false, "LEFT", true, 1, 1, 6f, 8f),
                PdfTableCellDto.text("LOT-2026-002", "CENTER", true),
                PdfTableCellDto.qrCode("LOT-2026-002-STEP2", 35, 35)
            )
        );

        List<PdfElementDto> elements = List.of(
            PdfElementDto.text("PRODUCTION TRAVELER SLIP", 16f, true, "CENTER"),
            PdfElementDto.styledText(
                "Caution: Critical tolerance components require QA visual inspection before next stage.",
                9.5f,
                "#DC2626",
                false,
                true,
                true,
                "LEFT",
                2f,
                8f
            ),
            PdfElementDto.table(
                List.of(10f, 50f, 20f, 20f),
                6f,
                8f,
                true,
                true,
                headers,
                rows
            ),
            PdfElementDto.spacer(10f),
            PdfElementDto.divider(),
            PdfElementDto.qrCode("CASTEM-DOC-AUTHENTICATED-2026", 100, 100, "CENTER")
        );

        PdfExportRequest request = new PdfExportRequest(config, elements);

        PdfExportResponse response = pdfExportService.generateAndSavePdf(request);

        assertEquals("SUCCESS", response.status());
        assertNotNull(response.relativePath());
        assertTrue(response.fileSize() > 0);
        assertTrue(storageService.fileExists(response.relativePath()));
    }
}
