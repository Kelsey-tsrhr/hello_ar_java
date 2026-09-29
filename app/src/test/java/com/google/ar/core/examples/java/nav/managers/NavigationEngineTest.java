package com.google.ar.core.examples.java.nav.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class NavigationEngineTest {

    @Test
    public void corridorModeSuppressesMildVeerAndHeading() {
        NavigationEngine engine = new NavigationEngine();
        RecordingCallback callback = new RecordingCallback();
        engine.startNewNavigation();
        engine.setInitialRouteReferencePoint(point(0f, 0f, 0f));
        engine.setCorridorMode(true);

        List<float[]> path = Arrays.asList(
                point(0f, 0f, 0f),
                point(0f, 0f, -1f),
                point(0f, 0f, -5f)
        );
        engine.update(0f, 0f, 0f, path, identityView(), callback);
        engine.update(1.5f, 0f, -3f, path, identityView(), callback);

        assertEquals(0, callback.headingCount);
        assertEquals(0, callback.veerCount);
        assertNotNull(engine.getRouteReferencePoint());
        assertEquals(-1f, engine.getRouteReferencePoint()[2], 0.001f);
    }

    @Test
    public void initialRouteReferenceDoesNotFollowMovingPathStart() {
        NavigationEngine engine = new NavigationEngine();
        RecordingCallback callback = new RecordingCallback();
        engine.startNewNavigation();
        engine.setInitialRouteReferencePoint(point(0f, 0f, 0f));

        List<float[]> path = Arrays.asList(
                point(0f, 0f, 0f),
                point(0f, 0f, -5f),
                point(0f, 0f, -10f)
        );
        engine.update(0f, 0f, 0f, path, identityView(), callback);

        List<float[]> movedPath = Arrays.asList(
                point(1.5f, 0f, -3f),
                point(0f, 0f, -5f),
                point(0f, 0f, -10f)
        );
        engine.update(1.5f, 0f, -3f, movedPath, identityView(), callback);

        assertEquals(0f, engine.getRouteReferencePoint()[0], 0.001f);
        assertEquals(0f, engine.getRouteReferencePoint()[2], 0.001f);
    }

    @Test
    public void corridorModeKeepsTurnAndSevereVeerCallbacks() {
        NavigationEngine turnEngine = new NavigationEngine();
        RecordingCallback turnCallback = new RecordingCallback();
        turnEngine.startNewNavigation();
        turnEngine.setCorridorMode(true);
        List<float[]> turnPath = Arrays.asList(
                point(0f, 0f, 0f),
                point(0f, 0f, -1f),
                point(0f, 0f, -2f),
                point(2f, 0f, -2f)
        );
        turnEngine.update(
                0f,
                0f,
                0f,
                turnPath,
                identityView(),
                turnCallback
        );
        turnEngine.update(0f, 0f, -2f, turnPath, identityView(), turnCallback);
        assertEquals(1, turnCallback.turnNowCount);

        NavigationEngine veerEngine = new NavigationEngine();
        RecordingCallback veerCallback = new RecordingCallback();
        veerEngine.startNewNavigation();
        veerEngine.setCorridorMode(true);
        List<float[]> path = Arrays.asList(
                point(0f, 0f, 0f),
                point(0f, 0f, -1f),
                point(0f, 0f, -5f)
        );
        veerEngine.update(0f, 0f, 0f, path, identityView(), veerCallback);
        veerEngine.update(2.1f, 0f, -3f, path, identityView(), veerCallback);

        assertEquals(1, veerCallback.veerCount);
    }

    private float[] identityView() {
        float[] view = new float[16];
        view[0] = 1f;
        view[5] = 1f;
        view[10] = 1f;
        view[15] = 1f;
        return view;
    }

    private float[] point(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    private static final class RecordingCallback implements NavigationEngine.NavigationCallback {
        int headingCount;
        int veerCount;
        int turnNowCount;

        @Override
        public void onArrived() {
        }

        @Override
        public void onPreTurnInstruction(int direction, float distanceMeters) {
        }

        @Override
        public void onTurnNow(int direction) {
            turnNowCount++;
        }

        @Override
        public void onHeadingGuidance(int direction) {
            headingCount++;
        }

        @Override
        public void onVeerDetected(int veerDirection, float lateralOffsetMeters) {
            veerCount++;
        }

        @Override
        public void onVeerRecovered() {
        }
    }
}
