package com.castem.javaserver.export.dto;

public record WatermarkConfigDto(
    String text,
    String imageSource,
    Float fontSize,
    String color,
    Float opacity,
    Float rotationDegrees,
    String layer // "UNDER_CONTENT" or "OVER_CONTENT"
) {
    public float getFontSizeOrDefault(float defaultValue) {
        return fontSize != null && fontSize > 0 ? fontSize : defaultValue;
    }

    public float getOpacityOrDefault(float defaultValue) {
        if (opacity != null) {
            return Math.max(0.01f, Math.min(1.0f, opacity));
        }
        return defaultValue;
    }

    public float getRotationOrDefault(float defaultValue) {
        return rotationDegrees != null ? rotationDegrees : defaultValue;
    }

    public boolean isOverContent() {
        return "OVER_CONTENT".equalsIgnoreCase(layer);
    }
}
