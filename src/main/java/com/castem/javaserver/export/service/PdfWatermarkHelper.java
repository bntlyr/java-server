package com.castem.javaserver.export.service;

import com.castem.javaserver.export.dto.WatermarkConfigDto;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;

public class PdfWatermarkHelper extends PdfPageEventHelper {

    private static final Logger log = LoggerFactory.getLogger(PdfWatermarkHelper.class);

    private final WatermarkConfigDto watermarkConfig;
    private final StorageService storageService;

    public PdfWatermarkHelper(WatermarkConfigDto watermarkConfig, StorageService storageService) {
        this.watermarkConfig = watermarkConfig;
        this.storageService = storageService;
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        if (watermarkConfig == null) {
            return;
        }

        try {
            PdfContentByte cb = watermarkConfig.isOverContent()
                ? writer.getDirectContent()
                : writer.getDirectContentUnder();

            cb.saveState();

            // Set transparency / opacity
            PdfGState gState = new PdfGState();
            float opacity = watermarkConfig.getOpacityOrDefault(0.15f);
            gState.setFillOpacity(opacity);
            gState.setStrokeOpacity(opacity);
            cb.setGState(gState);

            Rectangle page = document.getPageSize();
            float centerX = (page.getLeft() + page.getRight()) / 2f;
            float centerY = (page.getTop() + page.getBottom()) / 2f;

            // 1. Text Watermark
            if (watermarkConfig.text() != null && !watermarkConfig.text().isBlank()) {
                BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                cb.beginText();
                cb.setFontAndSize(bf, watermarkConfig.getFontSizeOrDefault(48f));
                cb.setColorFill(parseColor(watermarkConfig.color(), Color.GRAY));

                float rotation = watermarkConfig.getRotationOrDefault(45f);
                cb.showTextAligned(Element.ALIGN_CENTER, watermarkConfig.text(), centerX, centerY, rotation);
                cb.endText();
            }

            // 2. Image Watermark
            if (watermarkConfig.imageSource() != null && !watermarkConfig.imageSource().isBlank()) {
                Image image = loadImage(watermarkConfig.imageSource());
                if (image != null) {
                    image.setRotationDegrees(watermarkConfig.getRotationOrDefault(0f));
                    image.setAbsolutePosition(centerX - (image.getScaledWidth() / 2f), centerY - (image.getScaledHeight() / 2f));
                    cb.addImage(image);
                }
            }

            cb.restoreState();
        } catch (Exception ex) {
            log.error("Failed to render watermark overlay on page: {}", ex.getMessage(), ex);
        }
    }

    private Image loadImage(String source) {
        try {
            if (source.startsWith("data:image/")) {
                String base64Data = source.substring(source.indexOf(",") + 1);
                byte[] imgBytes = java.util.Base64.getDecoder().decode(base64Data);
                return Image.getInstance(imgBytes);
            }
            if (source.startsWith("http://") || source.startsWith("https://")) {
                return Image.getInstance(new java.net.URI(source).toURL());
            }
            // Resolve from storage bucket
            File file = storageService.resolveSafePath("", source).toFile();
            if (file.exists()) {
                return Image.getInstance(file.getAbsolutePath());
            }
        } catch (Exception ex) {
            log.warn("Could not load watermark image [{}]: {}", source, ex.getMessage());
        }
        return null;
    }

    public static Color parseColor(String hexOrRgb, Color fallback) {
        if (hexOrRgb == null || hexOrRgb.isBlank()) {
            return fallback;
        }
        try {
            String cleanHex = hexOrRgb.trim();
            if (cleanHex.startsWith("#")) {
                cleanHex = cleanHex.substring(1);
            }
            if (cleanHex.length() == 3) {
                // Expand 3-char hex like #FFF to #FFFFFF
                StringBuilder expanded = new StringBuilder();
                for (char c : cleanHex.toCharArray()) {
                    expanded.append(c).append(c);
                }
                cleanHex = expanded.toString();
            }
            if (cleanHex.length() == 6) {
                return new Color(Integer.parseInt(cleanHex, 16));
            }
        } catch (Exception ex) {
            log.warn("Invalid color value [{}], using fallback", hexOrRgb);
        }
        return fallback;
    }
}
