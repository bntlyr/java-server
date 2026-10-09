package com.castem.javaserver.print;

import com.castem.javaserver.export.service.StorageService;
import com.castem.javaserver.print.dto.PrintReportResponseDTO;
import com.castem.javaserver.print.dto.PrintTrialCardRequestDTO;
import com.castem.javaserver.print.service.TrialCardPrintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TrialCardPrintServiceTest {

    private TrialCardPrintService trialCardPrintService;
    private StorageService storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageService = new StorageService(tempDir.toString());
        trialCardPrintService = new TrialCardPrintService(storageService);
    }

    @Test
    void testGenerateTrialCardOriginal() {
        PrintTrialCardRequestDTO request = new PrintTrialCardRequestDTO(
            10,
            101,
            "ORIGINAL",
            LocalDate.of(2026, 10, 15),
            "TRIAL-2026-X01",
            "10-54321-000",
            "SCM440M",
            "Trial evaluation for new pattern mold release",
            "Wax Injection & Tree Assembly",
            "SO-2026-999",
            "LOT-01",
            null
        );

        PrintReportResponseDTO response = trialCardPrintService.generateAndSaveTrialCard(request);

        assertTrue(response.success());
        assertNotNull(response.relativePath());
        assertTrue(response.fileSize() > 0);
        assertTrue(storageService.fileExists(response.relativePath()));
    }

    @Test
    void testGenerateTrialCardRework() {
        PrintTrialCardRequestDTO request = new PrintTrialCardRequestDTO(
            11,
            102,
            "REWORK",
            LocalDate.of(2026, 10, 16),
            "TRIAL-REW-02",
            "10-54321-000",
            "SUS304",
            "Rework trial for secondary machining tolerance",
            "CNC Machining",
            "SO-2026-999",
            "LOT-02",
            null
        );

        PrintReportResponseDTO response = trialCardPrintService.generateAndSaveTrialCard(request);

        assertTrue(response.success());
        assertNotNull(response.relativePath());
        assertTrue(storageService.fileExists(response.relativePath()));
    }
}
