package com.castem.javaserver.print;

import com.castem.javaserver.export.service.QrCodeService;
import com.castem.javaserver.export.service.StorageService;
import com.castem.javaserver.print.controller.PrintReportController;
import com.castem.javaserver.print.dto.PrintProductionSlipRequestDTO;
import com.castem.javaserver.print.dto.PrintReportResponseDTO;
import com.castem.javaserver.print.dto.PrintTrialCardRequestDTO;
import com.castem.javaserver.print.service.ProductionSlipPrintService;
import com.castem.javaserver.print.service.TrialCardPrintService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrintReportControllerTest {

    private PrintReportController controller;
    private StorageService storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(tempDir.toString());
        QrCodeService qrCodeService = new QrCodeService(new ObjectMapper());
        ProductionSlipPrintService productionSlipPrintService = new ProductionSlipPrintService(qrCodeService, storageService);
        TrialCardPrintService trialCardPrintService = new TrialCardPrintService(storageService);
        controller = new PrintReportController(productionSlipPrintService, trialCardPrintService, storageService);
    }

    @Test
    void testCreateProductionSlipEndpoint() {
        PrintProductionSlipRequestDTO params = new PrintProductionSlipRequestDTO(
            301,
            "ORIGINAL",
            LocalDate.of(2026, 12, 1),
            "10-99999-000",
            "SO-PRINT-01",
            500,
            "SUS316L",
            "CHG-101",
            null,
            "BOX-B",
            "Small",
            1,
            25,
            25,
            50,
            10,
            "Urgent delivery",
            null,
            "250",
            "5",
            "E5.5D3A",
            "TM-01",
            "10-99999-000",
            1500f,
            1000f,
            2.0f,
            50,
            "1/1",
            "Remark",
            "Sales Remark",
            "QR-SO-PRINT-01",
            null,
            "Planner 1",
            null,
            List.of()
        );

        ResponseEntity<PrintReportResponseDTO> response = controller.createProductionSlip(params);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().success());
        assertNotNull(response.getBody().downloadUrl());
        assertTrue(storageService.fileExists(response.getBody().relativePath()));
    }

    @Test
    void testStreamProductionSlipEndpoint() {
        PrintProductionSlipRequestDTO params = new PrintProductionSlipRequestDTO(
            302,
            "REMAKE",
            LocalDate.of(2026, 12, 1),
            "10-99999-000",
            "SO-PRINT-02",
            100,
            "SUS316L",
            "CHG-102",
            null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            List.of()
        );

        ResponseEntity<byte[]> response = controller.streamProductionSlip(params);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length > 0);
    }

    @Test
    void testCreateTrialCardEndpoint() {
        PrintTrialCardRequestDTO params = new PrintTrialCardRequestDTO(
            55,
            401,
            "ORIGINAL",
            LocalDate.of(2026, 11, 1),
            "TRIAL-LOT-99",
            "10-88888-000",
            "SCM440",
            "Testing mold release agent",
            "Wax Pattern Molding",
            "SO-TRIAL-01",
            "LOT-01",
            null
        );

        ResponseEntity<PrintReportResponseDTO> response = controller.createTrialCard(params);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().success());
        assertTrue(storageService.fileExists(response.getBody().relativePath()));
    }
}
