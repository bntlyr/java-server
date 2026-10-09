package com.castem.javaserver.export;

import com.castem.javaserver.export.service.QrCodeService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeServiceTest {

    private QrCodeService qrCodeService;

    @BeforeEach
    void setUp() {
        qrCodeService = new QrCodeService(new ObjectMapper());
    }

    @Test
    void testGenerateQrFromString() throws Exception {
        BufferedImage img = qrCodeService.generateQrBufferedImage("ORDER-12345", 200, 200, "M", 1);
        assertNotNull(img);
        assertEquals(200, img.getWidth());
        assertEquals(200, img.getHeight());
    }

    @Test
    void testGenerateQrFromJson() throws Exception {
        Map<String, Object> payload = Map.of(
            "orderNo", "ORD-2026-999",
            "lotNo", "LOT-882",
            "stepId", 5
        );
        byte[] pngBytes = qrCodeService.generateQrPngBytes(payload, 150, 150, "H", 1);
        assertNotNull(pngBytes);
        assertTrue(pngBytes.length > 0);
    }

    @Test
    void testGenerateQrBase64() throws Exception {
        String base64 = qrCodeService.generateQrBase64DataUrl("TEST-QR", 100, 100, "M", 1);
        assertNotNull(base64);
        assertTrue(base64.startsWith("data:image/png;base64,"));
    }
}
