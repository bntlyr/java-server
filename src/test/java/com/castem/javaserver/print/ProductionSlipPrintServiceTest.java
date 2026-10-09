package com.castem.javaserver.print;

import com.castem.javaserver.export.service.QrCodeService;
import com.castem.javaserver.export.service.StorageService;
import com.castem.javaserver.print.dto.PrintProductionSlipRequestDTO;
import com.castem.javaserver.print.dto.PrintReportResponseDTO;
import com.castem.javaserver.print.dto.ProductionSlipTableInfoDTO;
import com.castem.javaserver.print.service.ProductionSlipPrintService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductionSlipPrintServiceTest {

    private ProductionSlipPrintService slipPrintService;
    private StorageService storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(tempDir.toString());
        QrCodeService qrCodeService = new QrCodeService(new ObjectMapper());
        slipPrintService = new ProductionSlipPrintService(qrCodeService, storageService);
    }

    private PrintProductionSlipRequestDTO createSampleSlip(int slipId, String type) {
        List<ProductionSlipTableInfoDTO> steps = List.of(
            new ProductionSlipTableInfoDTO(1, "Wax Injection", LocalDate.of(2026, 10, 15), null, LocalDate.of(2026, 10, 16), null, "Operator 1", 0, 500, ""),
            new ProductionSlipTableInfoDTO(2, "Slurry Dipping & Stucco", LocalDate.of(2026, 10, 17), null, LocalDate.of(2026, 10, 19), null, "Operator 2", 0, 500, ""),
            new ProductionSlipTableInfoDTO(3, "Casting & Pouring", LocalDate.of(2026, 10, 20), null, LocalDate.of(2026, 10, 21), null, "Operator 3", 0, 500, "")
        );

        return new PrintProductionSlipRequestDTO(
            slipId,
            type,
            LocalDate.of(2026, 11, 20),
            "10-12345-000",
            "SO-2026-001",
            1000,
            "SUS316L",
            "CHG-9923",
            "TRIAL".equalsIgnoreCase(type) ? "TRIAL-LOT-01" : null,
            "BOX-A",
            "10x15",
            2,
            50,
            50,
            100,
            15,
            "Handle with care",
            null,
            "500",
            "10",
            "E5.5D3A",
            "TM-88",
            "10-12345-100",
            1550f,
            1050f,
            2.5f,
            50,
            "1/2",
            "Standard production run",
            "Special order from Castem",
            "ORDER-2026-001-SLIP-" + slipId,
            "REWORK".equalsIgnoreCase(type) ? "REW-LOT-99" : null,
            "Admin User",
            null,
            steps
        );
    }

    @Test
    void testGenerateOriginalProductionSlip() {
        PrintProductionSlipRequestDTO request = createSampleSlip(101, "ORIGINAL");
        PrintReportResponseDTO response = slipPrintService.generateAndSaveSlip(request);

        assertTrue(response.success());
        assertNotNull(response.relativePath());
        assertTrue(response.fileSize() > 0);
        assertTrue(storageService.fileExists(response.relativePath()));
    }

    @Test
    void testGenerateReworkProductionSlip() {
        PrintProductionSlipRequestDTO request = createSampleSlip(102, "REWORK");
        PrintReportResponseDTO response = slipPrintService.generateAndSaveSlip(request);

        assertTrue(response.success());
        assertNotNull(response.relativePath());
        assertTrue(storageService.fileExists(response.relativePath()));
    }

    @Test
    void testGenerateRemakeProductionSlip() {
        PrintProductionSlipRequestDTO request = createSampleSlip(103, "REMAKE");
        PrintReportResponseDTO response = slipPrintService.generateAndSaveSlip(request);

        assertTrue(response.success());
        assertNotNull(response.relativePath());
        assertTrue(storageService.fileExists(response.relativePath()));
    }

    @Test
    void testGenerateTrialProductionSlip() {
        PrintProductionSlipRequestDTO request = createSampleSlip(104, "TRIAL");
        PrintReportResponseDTO response = slipPrintService.generateAndSaveSlip(request);

        assertTrue(response.success());
        assertNotNull(response.relativePath());
        assertTrue(storageService.fileExists(response.relativePath()));
    }

    @Test
    void testGenerateBatchProductionSlips() {
        List<PrintProductionSlipRequestDTO> batch = List.of(
            createSampleSlip(201, "ORIGINAL"),
            createSampleSlip(202, "ORIGINAL"),
            createSampleSlip(203, "REWORK")
        );

        PrintReportResponseDTO response = slipPrintService.generateAndSaveBatchSlips(batch);

        assertTrue(response.success());
        assertNotNull(response.relativePath());
        assertTrue(response.fileSize() > 0);
        assertTrue(storageService.fileExists(response.relativePath()));
    }
}
