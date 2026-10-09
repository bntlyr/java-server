package com.castem.javaserver.export.service;

import tools.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeService {

    private final ObjectMapper objectMapper;

    public QrCodeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts a raw string or complex JSON payload into a string representation for QR encoding.
     */
    public String resolvePayloadString(Object payload) {
        if (payload == null) {
            return "";
        }
        if (payload instanceof String str) {
            return str;
        }
        if (payload instanceof Number || payload instanceof Boolean) {
            return String.valueOf(payload);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return payload.toString();
        }
    }

    /**
     * Generates a BufferedImage QR code.
     */
    public BufferedImage generateQrBufferedImage(
        Object payload,
        int width,
        int height,
        String errorCorrection,
        int margin
    ) throws Exception {
        String content = resolvePayloadString(payload);
        if (content.isBlank()) {
            throw new IllegalArgumentException("QR payload cannot be empty");
        }

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.MARGIN, Math.max(0, margin));

        ErrorCorrectionLevel ecLevel = switch (errorCorrection != null ? errorCorrection.toUpperCase() : "M") {
            case "L" -> ErrorCorrectionLevel.L;
            case "Q" -> ErrorCorrectionLevel.Q;
            case "H" -> ErrorCorrectionLevel.H;
            default -> ErrorCorrectionLevel.M;
        };
        hints.put(EncodeHintType.ERROR_CORRECTION, ecLevel);

        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }

    /**
     * Generates PNG byte array of the QR code.
     */
    public byte[] generateQrPngBytes(
        Object payload,
        int width,
        int height,
        String errorCorrection,
        int margin
    ) throws Exception {
        BufferedImage image = generateQrBufferedImage(payload, width, height, errorCorrection, margin);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Generates Base64 data URL string for frontend rendering.
     */
    public String generateQrBase64DataUrl(
        Object payload,
        int width,
        int height,
        String errorCorrection,
        int margin
    ) throws Exception {
        byte[] pngBytes = generateQrPngBytes(payload, width, height, errorCorrection, margin);
        String base64 = Base64.getEncoder().encodeToString(pngBytes);
        return "data:image/png;base64," + base64;
    }
}
