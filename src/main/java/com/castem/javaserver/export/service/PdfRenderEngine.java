package com.castem.javaserver.export.service;

import com.castem.javaserver.export.dto.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.util.Base64;
import java.util.List;

@Service
public class PdfRenderEngine {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderEngine.class);

    private final QrCodeService qrCodeService;
    private final StorageService storageService;

    public PdfRenderEngine(QrCodeService qrCodeService, StorageService storageService) {
        this.qrCodeService = qrCodeService;
        this.storageService = storageService;
    }

    /**
     * Renders a complete PDF document into a binary byte array according to the request specification.
     */
    public byte[] renderPdf(PdfExportRequest request) throws Exception {
        PdfConfigDto config = request.config() != null 
            ? request.config() 
            : new PdfConfigDto("A4", "PORTRAIT", null, "HELVETICA", 10.0f, "#111827", null, null, null);

        Rectangle pageSize = resolvePageSize(config.getPageSizeOrDefault(), config.getOrientationOrDefault());
        
        PdfMarginsDto margins = config.margins();
        float marginLeft = margins != null ? margins.getLeftOrDefault(36f) : 36f;
        float marginRight = margins != null ? margins.getRightOrDefault(36f) : 36f;
        float marginTop = margins != null ? margins.getTopOrDefault(36f) : 36f;
        float marginBottom = margins != null ? margins.getBottomOrDefault(36f) : 36f;

        Document document = new Document(pageSize, marginLeft, marginRight, marginTop, marginBottom);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);

        // Watermark Overlay Page Event
        if (config.watermark() != null) {
            writer.setPageEvent(new PdfWatermarkHelper(config.watermark(), storageService));
        }

        document.open();

        List<PdfElementDto> elements = request.getElementsOrDefault();
        for (PdfElementDto elem : elements) {
            renderElement(document, elem, config);
        }

        document.close();
        return baos.toByteArray();
    }

    private void renderElement(Document document, PdfElementDto elem, PdfConfigDto config) throws Exception {
        String type = elem.getTypeOrDefault();

        switch (type) {
            case "TEXT" -> renderText(document, elem, config);
            case "TABLE" -> renderTable(document, elem, config);
            case "IMAGE" -> renderImage(document, elem);
            case "QR_CODE" -> renderQrCode(document, elem);
            case "SPACER" -> renderSpacer(document, elem);
            case "DIVIDER" -> renderDivider(document, elem);
            case "PAGE_BREAK" -> document.newPage();
            default -> log.warn("Unsupported PDF element type: {}", type);
        }
    }

    private void renderText(Document document, PdfElementDto elem, PdfConfigDto config) throws Exception {
        if (elem.content() == null) {
            return;
        }

        Font font = resolveFont(
            elem.fontFamily() != null ? elem.fontFamily() : config.getDefaultFontFamilyOrDefault(),
            elem.fontSize() != null ? elem.fontSize() : config.getDefaultFontSizeOrDefault(),
            elem.color() != null ? elem.color() : config.getDefaultTextColorOrDefault(),
            elem.isBold(),
            elem.isItalic(),
            elem.isUnderline(),
            elem.isStrikethrough()
        );

        Paragraph paragraph = new Paragraph(elem.content(), font);
        paragraph.setAlignment(resolveAlignment(elem.getAlignOrDefault()));
        paragraph.setSpacingBefore(elem.getSpacingBeforeOrDefault());
        paragraph.setSpacingAfter(elem.getSpacingAfterOrDefault());

        if (elem.lineSpacing() != null && elem.lineSpacing() > 0) {
            paragraph.setLeading(elem.lineSpacing());
        }

        document.add(paragraph);
    }

    private void renderTable(Document document, PdfElementDto elem, PdfConfigDto config) throws Exception {
        List<PdfTableCellDto> headers = elem.headers() != null ? elem.headers() : List.of();
        List<List<PdfTableCellDto>> rows = elem.rows() != null ? elem.rows() : List.of();

        int numCols = 1;
        if (elem.columnWidths() != null && !elem.columnWidths().isEmpty()) {
            numCols = elem.columnWidths().size();
        } else if (!headers.isEmpty()) {
            numCols = headers.size();
        } else if (!rows.isEmpty() && !rows.get(0).isEmpty()) {
            numCols = rows.get(0).size();
        }

        PdfPTable table = new PdfPTable(numCols);
        table.setWidthPercentage(elem.tableWidthPercentage() != null ? elem.tableWidthPercentage() : 100f);
        table.setSpacingBefore(elem.getSpacingBeforeOrDefault());
        table.setSpacingAfter(elem.getSpacingAfterOrDefault());

        if (elem.columnWidths() != null && !elem.columnWidths().isEmpty()) {
            float[] widths = new float[elem.columnWidths().size()];
            for (int i = 0; i < elem.columnWidths().size(); i++) {
                widths[i] = elem.columnWidths().get(i);
            }
            table.setWidths(widths);
        }

        if (Boolean.TRUE.equals(elem.repeatHeader()) && !headers.isEmpty()) {
            table.setHeaderRows(1);
        }

        float defaultRowGap = elem.getRowGapOrDefault(5.0f);
        float defaultColGap = elem.getColGapOrDefault(6.0f);
        Color borderColor = PdfWatermarkHelper.parseColor(elem.borderColor(), new Color(209, 213, 219));
        float borderWidth = elem.borderWidth() != null ? elem.borderWidth() : 0.5f;
        boolean showBorders = elem.showBorders() == null || Boolean.TRUE.equals(elem.showBorders());

        // 1. Render Table Headers
        if (!headers.isEmpty()) {
            for (PdfTableCellDto headerCell : headers) {
                PdfPCell cell = buildTableCell(headerCell, defaultRowGap, defaultColGap, showBorders, borderColor, borderWidth, config, true);
                table.addCell(cell);
            }
        }

        // 2. Render Table Rows
        boolean zebra = Boolean.TRUE.equals(elem.zebraStriping());
        Color altRowColor = PdfWatermarkHelper.parseColor(elem.alternateRowColor(), new Color(249, 250, 251));

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<PdfTableCellDto> row = rows.get(rowIndex);
            boolean isAltRow = zebra && (rowIndex % 2 == 1);

            for (PdfTableCellDto cellDto : row) {
                PdfPCell cell = buildTableCell(cellDto, defaultRowGap, defaultColGap, showBorders, borderColor, borderWidth, config, false);
                if (isAltRow && (cellDto.backgroundColor() == null || cellDto.backgroundColor().isBlank())) {
                    cell.setBackgroundColor(altRowColor);
                }
                table.addCell(cell);
            }
        }

        document.add(table);
    }

    private PdfPCell buildTableCell(
        PdfTableCellDto dto,
        float defaultRowGap,
        float defaultColGap,
        boolean showBorders,
        Color borderColor,
        float borderWidth,
        PdfConfigDto config,
        boolean isHeader
    ) throws Exception {
        PdfPCell cell = new PdfPCell();

        // Row and Column gaps (Padding)
        float rowGap = dto.rowGap() != null ? dto.rowGap() : defaultRowGap;
        float colGap = dto.colGap() != null ? dto.colGap() : defaultColGap;
        cell.setPaddingTop(rowGap);
        cell.setPaddingBottom(rowGap);
        cell.setPaddingLeft(colGap);
        cell.setPaddingRight(colGap);

        // Colspan & Rowspan
        if (dto.getColspanOrDefault() > 1) {
            cell.setColspan(dto.getColspanOrDefault());
        }
        if (dto.getRowspanOrDefault() > 1) {
            cell.setRowspan(dto.getRowspanOrDefault());
        }

        // Borders
        if (!showBorders) {
            cell.setBorder(Rectangle.NO_BORDER);
        } else {
            cell.setBorderColor(borderColor);
            cell.setBorderWidth(borderWidth);
        }

        // Background Color
        if (dto.backgroundColor() != null && !dto.backgroundColor().isBlank()) {
            cell.setBackgroundColor(PdfWatermarkHelper.parseColor(dto.backgroundColor(), Color.WHITE));
        } else if (isHeader) {
            cell.setBackgroundColor(new Color(243, 244, 246));
        }

        // Vertical Alignment
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        String cellType = dto.getTypeOrDefault();

        switch (cellType) {
            case "QR_CODE" -> {
                int qrW = dto.qrWidth() != null ? dto.qrWidth() : 40;
                int qrH = dto.qrHeight() != null ? dto.qrHeight() : 40;
                byte[] qrBytes = qrCodeService.generateQrPngBytes(dto.qrPayload(), qrW, qrH, "M", 0);
                Image qrImg = Image.getInstance(qrBytes);
                qrImg.scaleAbsolute(qrW, qrH);
                qrImg.setAlignment(resolveAlignment(dto.getAlignOrDefault()));
                cell.addElement(qrImg);
            }
            case "IMAGE" -> {
                if (dto.imageSource() != null && !dto.imageSource().isBlank()) {
                    Image img = loadImageFromSource(dto.imageSource());
                    if (img != null) {
                        float w = dto.imageWidth() != null ? dto.imageWidth() : 50f;
                        float h = dto.imageHeight() != null ? dto.imageHeight() : 50f;
                        img.scaleToFit(w, h);
                        img.setAlignment(resolveAlignment(dto.getAlignOrDefault()));
                        cell.addElement(img);
                    }
                }
            }
            default -> { // "TEXT"
                String textContent = dto.content() != null ? dto.content() : "";
                float fontSize = dto.fontSize() != null 
                    ? dto.fontSize() 
                    : (isHeader ? 10.0f : config.getDefaultFontSizeOrDefault());
                boolean bold = isHeader || dto.isBold();

                Font font = resolveFont(
                    dto.fontFamily() != null ? dto.fontFamily() : config.getDefaultFontFamilyOrDefault(),
                    fontSize,
                    dto.color() != null ? dto.color() : config.getDefaultTextColorOrDefault(),
                    bold,
                    dto.isItalic(),
                    dto.isUnderline(),
                    dto.isStrikethrough()
                );

                Paragraph p = new Paragraph(textContent, font);
                p.setAlignment(resolveAlignment(dto.getAlignOrDefault()));
                cell.addElement(p);
            }
        }

        return cell;
    }

    private void renderImage(Document document, PdfElementDto elem) throws Exception {
        if (elem.source() == null || elem.source().isBlank()) {
            return;
        }

        Image image = loadImageFromSource(elem.source());
        if (image == null) {
            return;
        }

        if (elem.width() != null && elem.height() != null) {
            if (Boolean.TRUE.equals(elem.fit())) {
                image.scaleToFit(elem.width(), elem.height());
            } else {
                image.scaleAbsolute(elem.width(), elem.height());
            }
        } else if (elem.width() != null) {
            float ratio = elem.width() / image.getWidth();
            image.scaleAbsolute(elem.width(), image.getHeight() * ratio);
        }

        image.setAlignment(resolveAlignment(elem.getAlignOrDefault()));
        image.setSpacingBefore(elem.getSpacingBeforeOrDefault());
        image.setSpacingAfter(elem.getSpacingAfterOrDefault());

        document.add(image);
    }

    private void renderQrCode(Document document, PdfElementDto elem) throws Exception {
        if (elem.qrPayload() == null) {
            return;
        }

        int width = elem.qrWidth() != null ? elem.qrWidth() : 120;
        int height = elem.qrHeight() != null ? elem.qrHeight() : 120;

        byte[] qrBytes = qrCodeService.generateQrPngBytes(
            elem.qrPayload(),
            width,
            height,
            elem.errorCorrection(),
            1
        );

        Image image = Image.getInstance(qrBytes);
        image.scaleAbsolute(width, height);
        image.setAlignment(resolveAlignment(elem.getAlignOrDefault()));
        image.setSpacingBefore(elem.getSpacingBeforeOrDefault());
        image.setSpacingAfter(elem.getSpacingAfterOrDefault());

        document.add(image);
    }

    private void renderSpacer(Document document, PdfElementDto elem) throws Exception {
        float height = elem.height() != null ? elem.height() : 10f;
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(height);
        document.add(p);
    }

    private void renderDivider(Document document, PdfElementDto elem) throws Exception {
        LineSeparator line = new LineSeparator();
        line.setLineWidth(elem.borderWidth() != null ? elem.borderWidth() : 0.5f);
        line.setLineColor(PdfWatermarkHelper.parseColor(elem.borderColor(), new Color(209, 213, 219)));
        line.setPercentage(elem.tableWidthPercentage() != null ? elem.tableWidthPercentage() : 100f);
        line.setAlignment(resolveAlignment(elem.getAlignOrDefault()));

        Paragraph p = new Paragraph();
        p.setSpacingBefore(elem.getSpacingBeforeOrDefault());
        p.setSpacingAfter(elem.getSpacingAfterOrDefault());
        p.add(line);
        document.add(p);
    }

    private Image loadImageFromSource(String source) {
        try {
            if (source.startsWith("data:image/")) {
                String base64Data = source.substring(source.indexOf(",") + 1);
                byte[] bytes = Base64.getDecoder().decode(base64Data);
                return Image.getInstance(bytes);
            }
            if (source.startsWith("http://") || source.startsWith("https://")) {
                return Image.getInstance(new URI(source).toURL());
            }
            // Resolve from storage bucket
            File file = storageService.resolveSafePath("", source).toFile();
            if (file.exists()) {
                return Image.getInstance(file.getAbsolutePath());
            }
        } catch (Exception ex) {
            log.error("Failed to load image from source [{}]: {}", source, ex.getMessage());
        }
        return null;
    }

    private Font resolveFont(
        String family,
        float size,
        String hexColor,
        boolean bold,
        boolean italic,
        boolean underline,
        boolean strikethrough
    ) {
        int fontIndex = switch (family != null ? family.toUpperCase() : "HELVETICA") {
            case "TIMES_ROMAN", "TIMES" -> Font.TIMES_ROMAN;
            case "COURIER" -> Font.COURIER;
            default -> Font.HELVETICA;
        };

        int style = Font.NORMAL;
        if (bold && italic) {
            style = Font.BOLDITALIC;
        } else if (bold) {
            style = Font.BOLD;
        } else if (italic) {
            style = Font.ITALIC;
        }

        if (underline) {
            style |= Font.UNDERLINE;
        }
        if (strikethrough) {
            style |= Font.STRIKETHRU;
        }

        Color color = PdfWatermarkHelper.parseColor(hexColor, Color.BLACK);
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    private int resolveAlignment(String align) {
        return switch (align != null ? align.toUpperCase() : "LEFT") {
            case "CENTER" -> Element.ALIGN_CENTER;
            case "RIGHT" -> Element.ALIGN_RIGHT;
            case "JUSTIFIED", "JUSTIFY" -> Element.ALIGN_JUSTIFIED;
            default -> Element.ALIGN_LEFT;
        };
    }

    private Rectangle resolvePageSize(String sizeName, String orientation) {
        Rectangle rect = switch (sizeName != null ? sizeName.toUpperCase() : "A4") {
            case "LETTER" -> PageSize.LETTER;
            case "LEGAL" -> PageSize.LEGAL;
            case "A3" -> PageSize.A3;
            case "A5" -> PageSize.A5;
            default -> PageSize.A4;
        };

        if ("LANDSCAPE".equalsIgnoreCase(orientation)) {
            return rect.rotate();
        }
        return rect;
    }
}
