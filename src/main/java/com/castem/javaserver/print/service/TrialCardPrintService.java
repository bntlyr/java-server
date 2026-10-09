package com.castem.javaserver.print.service;

import com.castem.javaserver.export.service.StorageService;
import com.castem.javaserver.print.dto.PrintReportResponseDTO;
import com.castem.javaserver.print.dto.PrintTrialCardRequestDTO;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class TrialCardPrintService {

    private static final Logger log = LoggerFactory.getLogger(TrialCardPrintService.class);

    private final StorageService storageService;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public TrialCardPrintService(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Generates a Trial Card PDF and saves it to the bucket.
     */
    public PrintReportResponseDTO generateAndSaveTrialCard(PrintTrialCardRequestDTO request) {
        long startTime = System.currentTimeMillis();
        try {
            byte[] pdfBytes = renderTrialCardBytes(request);
            String filename = "trial_card_" + request.itemId() + "_" + request.slipId() + ".pdf";
            String subPath = "private/exports/trial-cards";

            StorageService.StorageResult result = storageService.saveBuffer(subPath, filename, pdfBytes);
            long duration = System.currentTimeMillis() - startTime;

            String downloadUrl = "/api/v1/print/download/trial-card?itemId=" + request.itemId() + "&slipId=" + request.slipId();

            return PrintReportResponseDTO.success(
                filename,
                downloadUrl,
                result.relativePath(),
                result.fileSize(),
                duration
            );
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to generate trial card: {}", ex.getMessage(), ex);
            return PrintReportResponseDTO.failure(ex.getMessage(), duration);
        }
    }

    /**
     * Renders a Trial Card PDF stamped onto sample-trial-template.pdf.
     */
    public byte[] renderTrialCardBytes(PrintTrialCardRequestDTO params) throws Exception {
        ClassPathResource resource = new ClassPathResource("templates/sample-trial-template.pdf");
        byte[] templateBytes = resource.getInputStream().readAllBytes();

        PdfReader reader = new PdfReader(templateBytes);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfStamper stamper = new PdfStamper(reader, baos);
        PdfContentByte cb = stamper.getOverContent(1);

        BaseFont bfHelvetica = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        BaseFont bfHelveticaBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

        String createdAtStr = params.createdAt() != null ? params.createdAt().format(dateFormatter) : "";
        drawText(cb, bfHelvetica, 12, Color.BLACK, createdAtStr, 455, 760);

        drawText(cb, bfHelvetica, 12, Color.BLACK, params.trialLotNo(), 130, 735);
        drawText(cb, bfHelvetica, 12, Color.BLACK, params.itemCode(), 130, 710);
        drawText(cb, bfHelvetica, 12, Color.BLACK, params.rawMaterial(), 130, 685);
        drawText(cb, bfHelvetica, 11, Color.BLACK, params.trialPurpose(), 58, 642);
        drawText(cb, bfHelvetica, 11, Color.BLACK, params.trialProcess(), 58, 597);
        drawText(cb, bfHelvetica, 7, Color.BLACK, params.orderNo(), 110, 570);
        drawText(cb, bfHelvetica, 8, Color.BLACK, params.lotNo(), 175, 567);

        // Watermarks
        String cardType = params.getTypeOrDefault();
        if (params.watermarkText() != null && !params.watermarkText().isBlank()) {
            drawCenterWatermark(cb, bfHelveticaBold, params.watermarkText(), 55f, 0.18f, 45f, Color.RED);
        } else if ("REWORK".equalsIgnoreCase(cardType)) {
            drawCenterWatermark(cb, bfHelveticaBold, "REWORK TRIAL", 60f, 0.16f, 45f, new Color(220, 38, 38));
        } else if ("EVALUATION".equalsIgnoreCase(cardType)) {
            drawCenterWatermark(cb, bfHelveticaBold, "EVALUATION", 60f, 0.16f, 45f, new Color(59, 130, 246));
        }

        stamper.close();
        reader.close();
        return baos.toByteArray();
    }

    private void drawText(PdfContentByte cb, BaseFont bf, float fontSize, Color color, String text, float x, float y) {
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
        // Center of A4 Portrait (595 x 842) -> (297, 421)
        cb.showTextAligned(Element.ALIGN_CENTER, text, 297, 421, rotation);
        cb.endText();
        cb.restoreState();
    }
}
