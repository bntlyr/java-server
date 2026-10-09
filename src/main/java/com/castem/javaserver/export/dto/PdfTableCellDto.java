package com.castem.javaserver.export.dto;

public record PdfTableCellDto(
    String content,
    String type,               // "TEXT", "IMAGE", "QR_CODE"
    Object qrPayload,          // String or JSON object for QR
    Integer qrWidth,
    Integer qrHeight,
    String imageSource,
    Float imageWidth,
    Float imageHeight,
    Float fontSize,
    String fontFamily,
    String color,
    String backgroundColor,
    Boolean bold,
    Boolean italic,
    Boolean underline,
    Boolean strikethrough,
    String align,              // "LEFT", "CENTER", "RIGHT"
    Boolean wrap,
    Integer colspan,
    Integer rowspan,
    Float rowGap,              // Vertical cell padding (top & bottom)
    Float colGap               // Horizontal cell padding (left & right)
) {
    public static PdfTableCellDto text(String content) {
        return new PdfTableCellDto(content, "TEXT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, "LEFT", true, 1, 1, null, null);
    }

    public static PdfTableCellDto text(String content, String align, boolean bold) {
        return new PdfTableCellDto(content, "TEXT", null, null, null, null, null, null, null, null, null, null, bold, false, false, false, align, true, 1, 1, null, null);
    }

    public static PdfTableCellDto header(String content, String align) {
        return new PdfTableCellDto(content, "TEXT", null, null, null, null, null, null, 10f, null, "#111827", "#E5E7EB", true, false, false, false, align, true, 1, 1, 5f, 6f);
    }

    public static PdfTableCellDto qrCode(Object qrPayload, int width, int height) {
        return new PdfTableCellDto(null, "QR_CODE", qrPayload, width, height, null, null, null, null, null, null, null, null, null, null, null, "CENTER", false, 1, 1, 4f, 4f);
    }

    public String getTypeOrDefault() {
        if (type != null && !type.isBlank()) {
            return type.toUpperCase();
        }
        if (qrPayload != null) return "QR_CODE";
        if (imageSource != null) return "IMAGE";
        return "TEXT";
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

    public int getColspanOrDefault() {
        return colspan != null && colspan > 1 ? colspan : 1;
    }

    public int getRowspanOrDefault() {
        return rowspan != null && rowspan > 1 ? rowspan : 1;
    }
}
