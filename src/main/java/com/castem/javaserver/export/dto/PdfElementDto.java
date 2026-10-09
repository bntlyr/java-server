package com.castem.javaserver.export.dto;

import java.util.List;

public record PdfElementDto(
    String type,                  // "TEXT", "IMAGE", "QR_CODE", "TABLE", "PAGE_BREAK", "SPACER", "DIVIDER"
    
    // --- TEXT Attributes ---
    String content,
    Float fontSize,
    String fontFamily,
    String color,
    Boolean bold,
    Boolean italic,
    Boolean underline,
    Boolean strikethrough,
    String align,                 // "LEFT", "CENTER", "RIGHT", "JUSTIFIED"
    Boolean wrap,
    Float lineSpacing,
    
    // --- IMAGE Attributes ---
    String source,                // Base64 data URL, local file path, or HTTP URL
    Float width,
    Float height,                 // Also used for SPACER height
    Boolean fit,
    
    // --- QR CODE Attributes ---
    Object qrPayload,             // String or Map/DTO (serialized as JSON)
    Integer qrWidth,
    Integer qrHeight,
    String errorCorrection,       // "L", "M", "Q", "H"
    
    // --- TABLE Attributes ---
    List<Float> columnWidths,     // Proportional relative widths, e.g., [10, 40, 25, 25]
    Float rowGap,                 // Vertical gap/padding inside cells (top/bottom)
    Float colGap,                 // Horizontal gap/padding inside cells (left/right)
    Float tableWidthPercentage,   // Width percentage of page, default 100%
    Boolean showBorders,
    String borderColor,
    Float borderWidth,
    Boolean zebraStriping,
    String alternateRowColor,
    Boolean repeatHeader,
    List<PdfTableCellDto> headers,
    List<List<PdfTableCellDto>> rows,
    
    // --- Common Block Layout ---
    Float spacingBefore,
    Float spacingAfter
) {
    public static PdfElementDto text(String content, float fontSize, boolean bold, String align) {
        return new PdfElementDto(
            "TEXT", content, fontSize, "HELVETICA", "#111827", bold, false, false, false, align, true, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            0f, 4f
        );
    }

    public static PdfElementDto styledText(
        String content,
        float fontSize,
        String color,
        boolean bold,
        boolean italic,
        boolean underline,
        String align,
        float spacingBefore,
        float spacingAfter
    ) {
        return new PdfElementDto(
            "TEXT", content, fontSize, "HELVETICA", color, bold, italic, underline, false, align, true, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            spacingBefore, spacingAfter
        );
    }

    public static PdfElementDto qrCode(Object payload, int width, int height, String align) {
        return new PdfElementDto(
            "QR_CODE", null, null, null, null, null, null, null, null, align, null, null,
            null, null, null, null, payload, width, height, "M", null, null, null, null, null, null, null, null, null, null, null, null,
            4f, 4f
        );
    }

    public static PdfElementDto table(
        List<Float> columnWidths,
        float rowGap,
        float colGap,
        boolean showBorders,
        boolean zebraStriping,
        List<PdfTableCellDto> headers,
        List<List<PdfTableCellDto>> rows
    ) {
        return new PdfElementDto(
            "TABLE", null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null,
            columnWidths, rowGap, colGap, 100f, showBorders, "#D1D5DB", 0.5f, zebraStriping, "#F9FAFB", true,
            headers, rows, 6f, 10f
        );
    }

    public static PdfElementDto spacer(float height) {
        return new PdfElementDto(
            "SPACER", null, null, null, null, null, null, null, null, null, null, null,
            null, null, height, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            0f, 0f
        );
    }

    public static PdfElementDto divider() {
        return new PdfElementDto(
            "DIVIDER", null, null, null, null, null, null, null, null, "CENTER", null, null,
            null, null, null, null, null, null, null, null, null, null, null, 100f, null, "#D1D5DB", 0.5f, null, null, null, null, null,
            8f, 8f
        );
    }

    public String getTypeOrDefault() {
        return type != null && !type.isBlank() ? type.toUpperCase() : "TEXT";
    }

    public String getAlignOrDefault() {
        return align != null && !align.isBlank() ? align.toUpperCase() : "LEFT";
    }

    public boolean isBold() {
        return Boolean.TRUE.equals(bold);
    }

    public boolean isItalic() {
        return Boolean.TRUE.equals(italic);
    }

    public boolean isUnderline() {
        return Boolean.TRUE.equals(underline);
    }

    public boolean isStrikethrough() {
        return Boolean.TRUE.equals(strikethrough);
    }

    public boolean isWrap() {
        return wrap == null || wrap;
    }

    public float getSpacingBeforeOrDefault() {
        return spacingBefore != null && spacingBefore >= 0 ? spacingBefore : 0.0f;
    }

    public float getSpacingAfterOrDefault() {
        return spacingAfter != null && spacingAfter >= 0 ? spacingAfter : 4.0f;
    }

    public float getRowGapOrDefault(float defaultValue) {
        return rowGap != null && rowGap >= 0 ? rowGap : defaultValue;
    }

    public float getColGapOrDefault(float defaultValue) {
        return colGap != null && colGap >= 0 ? colGap : defaultValue;
    }
}
