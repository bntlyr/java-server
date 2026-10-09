package com.castem.javaserver.print.service;

import com.castem.javaserver.export.service.QrCodeService;
import com.castem.javaserver.export.service.StorageService;
import com.castem.javaserver.print.dto.PrintProductionSlipRequestDTO;
import com.castem.javaserver.print.dto.PrintReportResponseDTO;
import com.castem.javaserver.print.dto.ProductionSlipTableInfoDTO;
import com.castem.javaserver.print.util.PatternParserUtil;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
public class ProductionSlipPrintService {

    private static final Logger log = LoggerFactory.getLogger(ProductionSlipPrintService.class);

    private final QrCodeService qrCodeService;
    private final StorageService storageService;

    private final DateTimeFormatter dateFormatterETD = DateTimeFormatter.ofPattern("MMM-dd-yyyy", Locale.ENGLISH);
    private final DateTimeFormatter dateFormatterTable = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);

    public ProductionSlipPrintService(QrCodeService qrCodeService, StorageService storageService) {
        this.qrCodeService = qrCodeService;
        this.storageService = storageService;
    }

    /**
     * Generates a single Production Slip PDF and saves it to the bucket.
     */
    public PrintReportResponseDTO generateAndSaveSlip(PrintProductionSlipRequestDTO request) {
        long startTime = System.currentTimeMillis();
        try {
            byte[] pdfBytes = renderSingleSlipBytes(request);
            String filename = "slip_" + (request.slipId() != null ? request.slipId() : request.orderNo()) + ".pdf";
            String subPath = "private/exports/production-slips";

            StorageService.StorageResult result = storageService.saveBuffer(subPath, filename, pdfBytes);
            long duration = System.currentTimeMillis() - startTime;

            String downloadUrl = "/api/v1/print/download/production-slip?slipId=" + request.slipId();

            return PrintReportResponseDTO.success(
                filename,
                downloadUrl,
                result.relativePath(),
                result.fileSize(),
                duration
            );
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to generate production slip: {}", ex.getMessage(), ex);
            return PrintReportResponseDTO.failure(ex.getMessage(), duration);
        }
    }

    /**
     * Generates a batch of Production Slips concatenated into a single PDF document.
     */
    public PrintReportResponseDTO generateAndSaveBatchSlips(List<PrintProductionSlipRequestDTO> batchRequests) {
        long startTime = System.currentTimeMillis();
        try {
            if (batchRequests == null || batchRequests.isEmpty()) {
                throw new IllegalArgumentException("Batch request list cannot be empty");
            }

            byte[] combinedBytes = renderBatchSlipsBytes(batchRequests);
            String orderNo = batchRequests.get(0).orderNo();
            String filename = (orderNo != null ? orderNo : "batch") + "-slips.pdf";
            String subPath = "private/exports/production-slips/batch";

            StorageService.StorageResult result = storageService.saveBuffer(subPath, filename, combinedBytes);
            long duration = System.currentTimeMillis() - startTime;

            String downloadUrl = "/api/v1/print/download/batch-production-slips?orderNo=" + orderNo;

            return PrintReportResponseDTO.success(
                filename,
                downloadUrl,
                result.relativePath(),
                result.fileSize(),
                duration
            );
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to generate batch production slips: {}", ex.getMessage(), ex);
            return PrintReportResponseDTO.failure(ex.getMessage(), duration);
        }
    }

    /**
     * Renders a batch of production slips into a multi-page PDF byte array.
     */
    public byte[] renderBatchSlipsBytes(List<PrintProductionSlipRequestDTO> batchRequests) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfCopy copy = new PdfCopy(document, output);
        document.open();

        for (PrintProductionSlipRequestDTO slipReq : batchRequests) {
            byte[] singlePdf = renderSingleSlipBytes(slipReq);
            PdfReader reader = new PdfReader(singlePdf);
            int totalPages = reader.getNumberOfPages();
            for (int i = 1; i <= totalPages; i++) {
                copy.addPage(copy.getImportedPage(reader, i));
            }
            reader.close();
        }

        document.close();
        return output.toByteArray();
    }

    /**
     * Renders a single Production Slip PDF stamped onto sample-template.pdf.
     */
    public byte[] renderSingleSlipBytes(PrintProductionSlipRequestDTO params) throws Exception {
        ClassPathResource resource = new ClassPathResource("templates/sample-template.pdf");
        byte[] templateBytes = resource.getInputStream().readAllBytes();

        PdfReader reader = new PdfReader(templateBytes);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfStamper stamper = new PdfStamper(reader, baos);
        PdfContentByte cb = stamper.getOverContent(1);

        BaseFont bfHelvetica = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        BaseFont bfHelveticaBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        BaseFont bfTimesRoman = BaseFont.createFont(BaseFont.TIMES_ROMAN, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        BaseFont bfTimesItalic = BaseFont.createFont(BaseFont.TIMES_ITALIC, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

        // ==========================================
        // 1. TOP HEADER PANEL (x: 18, y: 478, w: 791, h: 70)
        // ==========================================
        String etd = params.requestedETD() != null ? params.requestedETD().format(dateFormatterETD) : "";
        drawText(cb, bfHelveticaBold, 17, Color.BLACK, etd, 38, 520, false);

        drawText(cb, bfHelveticaBold, 17, Color.BLACK, params.itemCode(), 38, 500, true);
        drawText(cb, bfHelvetica, 15, Color.BLACK, params.trialLotNo(), 38, 480, false);
        drawText(cb, bfHelveticaBold, 17, Color.BLACK, params.orderNo(), 155, 500, true);
        drawText(cb, bfHelveticaBold, 17, Color.BLACK, params.orderQty() != null ? params.orderQty().toString() : "", 273, 500, true);
        drawText(cb, bfHelveticaBold, 17, Color.BLACK, params.rawMaterial(), 343, 500, true);
        drawText(cb, bfHelvetica, 15, Color.BLACK, params.chargeNo(), 475, 477, false);

        // Packing Details
        drawText(cb, bfHelvetica, 6, Color.BLACK, params.boxLayer(), 640, 520, false);
        drawText(cb, bfHelvetica, 6, Color.BLACK, params.plasticQty() != null ? params.plasticQty().toString() : "0", 640, 508, false);
        drawText(cb, bfHelvetica, 6, Color.BLACK, params.plasticSize(), 640, 497, false);
        drawText(cb, bfHelvetica, 6, Color.BLACK, params.topLayerQty() != null ? params.topLayerQty().toString() : "0", 740, 520, false);
        drawText(cb, bfHelvetica, 6, Color.BLACK, params.btmLayerQty() != null ? params.btmLayerQty().toString() : "0", 740, 508, false);
        drawText(cb, bfHelvetica, 6, Color.BLACK, params.maxQty() != null ? params.maxQty().toString() : "0", 740, 497, false);
        drawText(cb, bfHelvetica, 6, Color.BLACK, params.maxWt() != null ? params.maxWt().toString() : "0", 740, 487, false);
        drawText(cb, bfHelvetica, 6, Color.BLACK, params.packingRemarks(), 580, 487, false);

        // ==========================================
        // 2. LEFT SIDE PANEL (x: 18, y: 23, w: 169, h: 454)
        // ==========================================
        // Blueprint Image
        if (params.imageUrl() != null && !params.imageUrl().isBlank() && !params.imageUrl().equals("---")) {
            Image itemImg = loadImage(params.imageUrl());
            if (itemImg != null) {
                itemImg.scaleToFit(161, 131);
                itemImg.setAbsolutePosition(35, 346);
                cb.addImage(itemImg);
            }
        }

        // Qty/Tree & Casting Wt
        drawText(cb, bfHelvetica, 7, Color.BLACK, "Pcs/Tree: " + (params.pcsPerTree() != null ? params.pcsPerTree() : "0") + " pcs", 35, 226, false);
        drawText(cb, bfHelvetica, 7, Color.BLACK, "Casting Wt: " + (params.castingWt() != null ? params.castingWt() : "0") + " kg", 133, 226, false);

        // Production Qty & Scheduled Trees
        drawText(cb, bfHelvetica, 12, Color.BLACK, params.productionQty() != null ? params.productionQty() : "0", 48, 303, false);
        drawText(cb, bfHelvetica, 12, Color.BLACK, params.scheduledTrees() != null ? params.scheduledTrees() : "0", 130, 303, false);

        // Coating Pattern (Parsed via regex: Letter, Thickness, D-Letter, D-Number, Suffix)
        PatternParserUtil.CoatingResult coating = PatternParserUtil.parseCoatingPattern(params.coatingPattern());
        if (coating != null) {
            drawText(cb, bfHelvetica, 12, Color.BLACK, coating.letter(), 40, 242, false);
            drawText(cb, bfHelvetica, 12, Color.BLACK, coating.thickness(), 74, 242, false);
            drawText(cb, bfHelveticaBold, 18, Color.BLACK, coating.dLetter(), 106, 236, false);
            drawText(cb, bfHelveticaBold, 18, Color.BLACK, String.valueOf(coating.dNumber()), 129, 242, false);
            if (coating.lastLetter() != null) {
                drawText(cb, bfHelveticaBold, 18, Color.BLACK, coating.lastLetter(), 168, 242, false);
            }
        }

        drawText(cb, bfHelvetica, 9, Color.BLACK, params.treeMark(), 38, 210, false);
        drawText(cb, bfHelvetica, 7, Color.BLACK, params.similarProducts(), 80, 188, false);

        // Temperatures
        if (params.castingTemp() != null) {
            drawText(cb, bfHelveticaBold, 9, Color.BLACK, formatTemp(params.castingTemp()) + " (+10) ºC", 83, 171, false);
        }
        if (params.moldTemp() != null) {
            drawText(cb, bfHelveticaBold, 9, Color.BLACK, formatTemp(params.moldTemp()) + " ºC", 83, 156, false);
        }

        drawText(cb, bfHelvetica, 6, Color.BLACK, params.trialRemarks(), 38, 115, false);

        // Slip Size (e.g. 1/2)
        PatternParserUtil.SlipSizeResult slipSize = PatternParserUtil.parseSlipSize(params.slipSize());
        drawText(cb, bfHelveticaBold, 18, Color.BLACK, slipSize.first(), 40, 58, false);
        if (!slipSize.second().isBlank()) {
            drawText(cb, bfHelveticaBold, 18, Color.BLACK, slipSize.second(), 73, 45, false);
        }

        // Rework Lot No (for Rework Slips)
        if (params.reworkLotNo() != null && !params.reworkLotNo().isBlank()) {
            drawText(cb, bfHelveticaBold, 12, Color.BLACK, params.reworkLotNo(), 40, 30, false);
        }

        // QR Code
        String qrContent = params.qrCode() != null && !params.qrCode().isBlank() 
            ? params.qrCode() 
            : "SLIP-" + params.slipId() + "-" + params.orderNo();
        byte[] qrPng = qrCodeService.generateQrPngBytes(qrContent, 70, 70, "M", 0);
        Image qrImg = Image.getInstance(qrPng);
        qrImg.scaleAbsolute(70, 70);
        qrImg.setAbsolutePosition(128, 34);
        cb.addImage(qrImg);

        // ==========================================
        // 3. ROUTE STEPS TABLE (x: 200, y: 61, w: 621, h: 403)
        // ==========================================
        List<ProductionSlipTableInfoDTO> processes = params.getProcessTableDetailsOrDefault();
        float startRowY = 456;
        float rowHeight = 10f;

        for (int i = 0; i < processes.size(); i++) {
            ProductionSlipTableInfoDTO proc = processes.get(i);
            float currentY = startRowY - (i * rowHeight);

            // Col 0: Process Name (x: 228)
            drawText(cb, bfHelvetica, 7, Color.BLACK, proc.processName(), 228, currentY, false);

            // Col 1: Scheduled Start Date (x: 339)
            if (proc.startDate() != null) {
                drawText(cb, bfHelvetica, 7, Color.BLACK, proc.startDate().format(dateFormatterTable), 339, currentY, false);
            }

            // Col 3: Scheduled End Date (x: 402)
            if (proc.endDate() != null) {
                drawText(cb, bfHelvetica, 7, Color.BLACK, proc.endDate().format(dateFormatterTable), 402, currentY, false);
            }
        }

        // ==========================================
        // 4. FOOTER PANEL (x: 18, y: 5, w: 791, h: 45)
        // ==========================================
        String printedAtStr = "Printed At: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        drawText(cb, bfTimesRoman, 7, Color.BLACK, printedAtStr, 23, 9, false);

        String printedByStr = "Printed By: " + (params.createdBy() != null ? params.createdBy() : "SYSTEM");
        drawText(cb, bfTimesRoman, 7, Color.BLACK, printedByStr, 173, 9, false);

        drawText(cb, bfTimesItalic, 7, Color.BLACK, "CPC-PC-FO-05106B / REV.08", 723, 9, false);
        drawText(cb, bfTimesRoman, 7, Color.BLACK, params.salesRemarks(), 216, 33, false);

        // ==========================================
        // 5. WATERMARKS & SLIP TYPES (ORIGINAL, REWORK, REMAKE, TRIAL)
        // ==========================================
        String slipType = params.getTypeOrDefault();
        boolean hasTrial = "TRIAL".equalsIgnoreCase(slipType) || (params.trialLotNo() != null && !params.trialLotNo().isBlank());
        boolean hasRemake = "REMAKE".equalsIgnoreCase(slipType);
        boolean hasRework = "REWORK".equalsIgnoreCase(slipType);

        if (params.watermarkText() != null && !params.watermarkText().isBlank()) {
            drawCenterWatermark(cb, bfHelveticaBold, params.watermarkText(), 60f, 0.18f, 45f, Color.RED);
        } else if (hasTrial) {
            drawCenterWatermark(cb, bfHelveticaBold, "TRIAL", 110f, 0.20f, 0f, Color.BLACK);
        } else if (hasRemake) {
            drawCenterWatermark(cb, bfHelveticaBold, "REMAKE", 80f, 0.18f, 45f, Color.RED);
        } else if (hasRework) {
            drawCenterWatermark(cb, bfHelveticaBold, "REWORK", 80f, 0.18f, 45f, new Color(220, 38, 38));
        }

        stamper.close();
        reader.close();
        return baos.toByteArray();
    }

    private void drawText(PdfContentByte cb, BaseFont bf, float fontSize, Color color, String text, float x, float y, boolean underline) {
        if (text == null || text.isBlank()) {
            return;
        }
        cb.saveState();
        cb.beginText();
        cb.setFontAndSize(bf, fontSize);
        cb.setColorFill(color);
        cb.setTextMatrix(x, y);
        cb.showText(text);
        cb.endText();

        if (underline) {
            float textWidth = bf.getWidthPoint(text, fontSize);
            cb.setLineWidth(1.2f);
            cb.setColorStroke(color);
            cb.moveTo(x, y - 2f);
            cb.lineTo(x + textWidth, y - 2f);
            cb.stroke();
        }
        cb.restoreState();
    }

    private void drawCenterWatermark(PdfContentByte cb, BaseFont bf, String text, float fontSize, float opacity, float rotation, Color color) {
        cb.saveState();
        PdfGState gState = new PdfGState();
        gState.setFillOpacity(opacity);
        gState.setStrokeOpacity(opacity);
        cb.setGState(gState);

        cb.beginText();
        cb.setFontAndSize(bf, fontSize);
        cb.setColorFill(color);
        // Center of A4 Landscape (842 x 595) -> (421, 297)
        cb.showTextAligned(Element.ALIGN_CENTER, text, 421, 297, rotation);
        cb.endText();
        cb.restoreState();
    }

    private Image loadImage(String source) {
        try {
            if (source.startsWith("data:image/")) {
                String base64Data = source.substring(source.indexOf(",") + 1);
                byte[] bytes = Base64.getDecoder().decode(base64Data);
                return Image.getInstance(bytes);
            }
            if (source.startsWith("http://") || source.startsWith("https://")) {
                return Image.getInstance(new URI(source).toURL());
            }
            File file = storageService.resolveSafePath("", source).toFile();
            if (file.exists()) {
                return Image.getInstance(file.getAbsolutePath());
            }
        } catch (Exception ex) {
            log.warn("Could not load image for slip from [{}]: {}", source, ex.getMessage());
        }
        return null;
    }

    private String formatTemp(Float temp) {
        if (temp == null) return "0";
        if (temp == temp.intValue()) {
            return String.valueOf(temp.intValue());
        }
        return String.valueOf(temp);
    }
}
