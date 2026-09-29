package com.google.ar.core.examples.java.nav.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Test;

public class DepthCorridorAnalyzerTest {

    @Test
    public void analyzeBuffer_detectsSideBoundariesAndForwardSpace() {
        int width = 100;
        int height = 100;
        int rowStride = width * 2;
        ByteBuffer buffer = ByteBuffer.allocate(height * rowStride)
                .order(ByteOrder.LITTLE_ENDIAN);

        fill(buffer, width, height, rowStride, 2500);
        fillRegion(buffer, width, height, rowStride, 8, 30, 35, 75, 1000);
        fillRegion(buffer, width, height, rowStride, 70, 92, 35, 75, 1100);

        DepthCorridorAnalyzer.DepthEvidence evidence = DepthCorridorAnalyzer.analyzeBuffer(
                buffer,
                width,
                height,
                rowStride,
                2,
                1000L
        );

        assertTrue(evidence.available);
        assertTrue(evidence.qualitySufficient);
        assertTrue(evidence.corridorLike);
        assertEquals(1.0f, evidence.leftDistanceMeters, 0.01f);
        assertEquals(1.1f, evidence.rightDistanceMeters, 0.01f);
        assertEquals(2.5f, evidence.centerDistanceMeters, 0.01f);
    }

    @Test
    public void analyzeBuffer_rejectsMissingSideBoundary() {
        int width = 100;
        int height = 100;
        int rowStride = width * 2;
        ByteBuffer buffer = ByteBuffer.allocate(height * rowStride)
                .order(ByteOrder.LITTLE_ENDIAN);

        fill(buffer, width, height, rowStride, 0);
        fillRegion(buffer, width, height, rowStride, 38, 62, 35, 75, 2500);

        DepthCorridorAnalyzer.DepthEvidence evidence = DepthCorridorAnalyzer.analyzeBuffer(
                buffer,
                width,
                height,
                rowStride,
                2,
                1000L
        );

        assertTrue(evidence.available);
        assertTrue(!evidence.qualitySufficient);
        assertTrue(!evidence.corridorLike);
    }

    private void fill(ByteBuffer buffer, int width, int height, int rowStride, int depthMm) {
        fillRegion(buffer, width, height, rowStride, 0, width, 0, height, depthMm);
    }

    private void fillRegion(ByteBuffer buffer,
                            int width,
                            int height,
                            int rowStride,
                            int left,
                            int right,
                            int top,
                            int bottom,
                            int depthMm) {
        for (int y = Math.max(0, top); y < Math.min(height, bottom); y++) {
            for (int x = Math.max(0, left); x < Math.min(width, right); x++) {
                buffer.putShort(y * rowStride + x * 2, (short) depthMm);
            }
        }
    }
}
