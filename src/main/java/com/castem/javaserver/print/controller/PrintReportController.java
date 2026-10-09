package com.castem.javaserver.print.controller;

import com.castem.javaserver.export.service.StorageService;
import com.castem.javaserver.print.dto.*;
import com.castem.javaserver.print.service.ProductionSlipPrintService;
import com.castem.javaserver.print.service.TrialCardPrintService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/print")
public class PrintReportController {

    private final ProductionSlipPrintService productionSlipPrintService;
    private final TrialCardPrintService trialCardPrintService;
    private final StorageService storageService;

    public PrintReportController(
        ProductionSlipPrintService productionSlipPrintService,
        TrialCardPrintService trialCardPrintService,
        StorageService storageService
    ) {
        this.productionSlipPrintService = productionSlipPrintService;
        this.trialCardPrintService = trialCardPrintService;
        this.storageService = storageService;
    }

    /**
     * Generates a single production slip (ORIGINAL, REWORK, REMAKE, TRIAL) and saves it to bucket.
     */
    @PostMapping("/production-slip")
    public ResponseEntity<PrintReportResponseDTO> createProductionSlip(@RequestBody PrintProductionSlipRequestDTO params) {
        PrintReportResponseDTO response = productionSlipPrintService.generateAndSaveSlip(params);
        if (!response.success()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Generates a batch of production slips merged into a single multi-page PDF and saves it to bucket.
     */
    @PostMapping("/production-slip/batch")
    public ResponseEntity<PrintReportResponseDTO> createBatchProductionSlips(@RequestBody List<PrintProductionSlipRequestDTO> batchParams) {
        PrintReportResponseDTO response = productionSlipPrintService.generateAndSaveBatchSlips(batchParams);
        if (!response.success()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Directly streams a single production slip as binary PDF.
     */
    @PostMapping(value = "/production-slip/stream", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> streamProductionSlip(@RequestBody PrintProductionSlipRequestDTO params) {
        try {
            byte[] pdfBytes = productionSlipPrintService.renderSingleSlipBytes(params);
            String filename = "slip_" + (params.slipId() != null ? params.slipId() : params.orderNo()) + ".pdf";

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
     * Directly streams a batch of production slips as binary multi-page PDF.
     */
    @PostMapping(value = "/production-slip/batch/stream", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> streamBatchProductionSlips(@RequestBody List<PrintProductionSlipRequestDTO> batchParams) {
        try {
            byte[] pdfBytes = productionSlipPrintService.renderBatchSlipsBytes(batchParams);
            String orderNo = !batchParams.isEmpty() && batchParams.get(0).orderNo() != null ? batchParams.get(0).orderNo() : "batch";
            String filename = orderNo + "-slips.pdf";

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
     * Generates a trial card PDF and saves it to bucket.
     */
    @PostMapping("/trial-card")
    public ResponseEntity<PrintReportResponseDTO> createTrialCard(@RequestBody PrintTrialCardRequestDTO params) {
        PrintReportResponseDTO response = trialCardPrintService.generateAndSaveTrialCard(params);
        if (!response.success()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Directly streams a trial card as binary PDF.
     */
    @PostMapping(value = "/trial-card/stream", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> streamTrialCard(@RequestBody PrintTrialCardRequestDTO params) {
        try {
            byte[] pdfBytes = trialCardPrintService.renderTrialCardBytes(params);
            String filename = "trial_card_" + params.itemId() + "_" + params.slipId() + ".pdf";

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
     * Downloads an already saved Production Slip PDF from bucket storage.
     */
    @GetMapping("/download/production-slip")
    public ResponseEntity<byte[]> downloadProductionSlip(@RequestParam Long slipId) throws IOException {
        String relativePath = "private/exports/production-slips/slip_" + slipId + ".pdf";
        if (!storageService.fileExists(relativePath)) {
            return ResponseEntity.notFound().build();
        }

        byte[] data = storageService.readFile(relativePath);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "slip_" + slipId + ".pdf");
        headers.setContentLength(data.length);

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    /**
     * Downloads an already saved Batch Production Slips PDF from bucket storage.
     */
    @GetMapping("/download/batch-production-slips")
    public ResponseEntity<byte[]> downloadBatchProductionSlips(@RequestParam String orderNo) throws IOException {
        String relativePath = "private/exports/production-slips/batch/" + orderNo + "-slips.pdf";
        if (!storageService.fileExists(relativePath)) {
            return ResponseEntity.notFound().build();
        }

        byte[] data = storageService.readFile(relativePath);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", orderNo + "-slips.pdf");
        headers.setContentLength(data.length);

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    /**
     * Downloads an already saved Trial Card PDF from bucket storage.
     */
    @GetMapping("/download/trial-card")
    public ResponseEntity<byte[]> downloadTrialCard(@RequestParam Long itemId, @RequestParam Long slipId) throws IOException {
        String relativePath = "private/exports/trial-cards/trial_card_" + itemId + "_" + slipId + ".pdf";
        if (!storageService.fileExists(relativePath)) {
            return ResponseEntity.notFound().build();
        }

        byte[] data = storageService.readFile(relativePath);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "trial_card_" + itemId + "_" + slipId + ".pdf");
        headers.setContentLength(data.length);

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }
}
