package com.google.ar.core.examples.java.nav.navigation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Test;

public class DepthDoorwayAnalyzerTest {

    @Test
    public void analyzeBuffer_detectsNarrowCentralOpening() {
        int width = 160;
        int height = 100;
        int rowStride = width * 2;
        ByteBuffer buffer = ByteBuffer.allocate(height * rowStride)
                .order(ByteOrder.LITTLE_ENDIAN);

        fill(buffer, width, height, rowStride, 2000);
        fillRegion(buffer, width, height, rowStride, 36, 104, 35, 75, 5000);

        DepthDoorwayAnalyzer.DoorEvidence evidence = DepthDoorwayAnalyzer.analyzeBuffer(
                buffer,
                width,
                height,
                rowStride,
                2,
                Float.NaN,
                Float.NaN,
                1000L
        );

        assertTrue(evidence.available);
        assertTrue(evidence.qualitySufficient);
        assertTrue(evidence.narrowDoor);
        assertTrue(evidence.widthMeters >= 0.55f);
        assertTrue(evidence.widthMeters <= 2.0f);
        assertTrue(evidence.distanceMeters > 0f);
    }

    @Test
    public void analyzeBuffer_doesNotRequireTopOrBottomDoorFrame() {
        int width = 160;
        int height = 100;
        int rowStride = width * 2;
        ByteBuffer buffer = ByteBuffer.allocate(height * rowStride)
                .order(ByteOrder.LITTLE_ENDIAN);

        // 只有中部扫描带有深度数据；画面上方和下方没有门框数据。
        fillRegion(buffer, width, height, rowStride, 0, width, 35, 75, 2000);
        fillRegion(buffer, width, height, rowStride, 36, 104, 35, 75, 5000);

        DepthDoorwayAnalyzer.DoorEvidence evidence = DepthDoorwayAnalyzer.analyzeBuffer(
                buffer,
                width,
                height,
                rowStride,
                2,
                Float.NaN,
                Float.NaN,
                1000L
        );

        assertTrue(evidence.qualitySufficient);
        assertTrue(evidence.narrowDoor);
    }

    @Test
    public void analyzeBuffer_rejectsOpeningWiderThanTwoMeters() {
        int width = 160;
        int height = 100;
        int rowStride = width * 2;
        ByteBuffer buffer = ByteBuffer.allocate(height * rowStride)
                .order(ByteOrder.LITTLE_ENDIAN);

        fill(buffer, width, height, rowStride, 2000);
        fillRegion(buffer, width, height, rowStride, 36, 124, 35, 75, 5000);

        DepthDoorwayAnalyzer.DoorEvidence evidence = DepthDoorwayAnalyzer.analyzeBuffer(
                buffer,
                width,
                height,
                rowStride,
                2,
                80f,
                80f,
                1000L
        );

        assertTrue(evidence.qualitySufficient);
        assertFalse(evidence.narrowDoor);
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
