package com.castem.javaserver.export.dto;

public record PdfMarginsDto(
    Float top,
    Float bottom,
    Float left,
    Float right
) {
    public float getTopOrDefault(float defaultValue) {
        return top != null ? top : defaultValue;
    }

    public float getBottomOrDefault(float defaultValue) {
        return bottom != null ? bottom : defaultValue;
    }

    public float getLeftOrDefault(float defaultValue) {
        return left != null ? left : defaultValue;
    }

    public float getRightOrDefault(float defaultValue) {
        return right != null ? right : defaultValue;
    }
}
