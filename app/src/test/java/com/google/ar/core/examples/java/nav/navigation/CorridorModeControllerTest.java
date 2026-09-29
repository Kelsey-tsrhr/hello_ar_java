package com.google.ar.core.examples.java.nav.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CorridorModeControllerTest {

    @Test
    public void entersCorridorModeAfterStableForwardEvidence() {
        CorridorModeController controller = new CorridorModeController();
        List<float[]> path = straightPath();

        assertEquals(CorridorModeController.Mode.NORMAL,
                update(controller, path, 0L, depth(0L)));
        assertEquals(CorridorModeController.Mode.NORMAL,
                update(controller, path, 500L, depth(500L)));
        assertEquals(CorridorModeController.Mode.NORMAL,
                update(controller, path, 1000L, depth(1000L)));
        assertEquals(CorridorModeController.Mode.CORRIDOR,
                update(controller, path, 1600L, depth(1600L)));
    }

    @Test
    public void reverseDirectionDoesNotEnterCorridorMode() {
        CorridorModeController controller = new CorridorModeController();
        float[] reverseView = reverseView();

        CorridorModeController.Decision decision = null;
        for (long now = 0L; now <= 2000L; now += 500L) {
            decision = controller.update(
                    0f,
                    0f,
                    straightPath(),
                    null,
                    reverseView,
                    depth(now),
                    true,
                    now
            );
        }

        assertTrue(decision != null);
        assertEquals(CorridorModeController.Mode.NORMAL, decision.mode);
        assertTrue(decision.routeDirectionReverse);
    }

    @Test
    public void nearTurnExitsCorridorModeImmediately() {
        CorridorModeController controller = new CorridorModeController();
        List<float[]> straight = straightPath();
        update(controller, straight, 0L, depth(0L));
        update(controller, straight, 500L, depth(500L));
        update(controller, straight, 1000L, depth(1000L));
        assertEquals(CorridorModeController.Mode.CORRIDOR,
                update(controller, straight, 1600L, depth(1600L)));

        List<float[]> turnPath = Arrays.asList(
                point(0f, 0f, -4f),
                point(0f, 0f, -5f),
                point(2f, 0f, -5f)
        );
        CorridorModeController.Decision decision = controller.update(
                0f,
                -4f,
                turnPath,
                point(0f, 0f, 0f),
                identityView(),
                depth(2000L),
                true,
                2000L
        );

        assertTrue(decision.modeChanged);
        assertEquals(CorridorModeController.Mode.NORMAL, decision.mode);
        assertTrue(decision.nearTurn);
    }

    @Test
    public void severeLateralOffsetExitsCorridorMode() {
        CorridorModeController controller = new CorridorModeController();
        enter(controller);

        CorridorModeController.Decision decision = controller.update(
                2.1f,
                -4f,
                Arrays.asList(
                        point(2.1f, 0f, -4f),
                        point(0f, 0f, -5f),
                        point(0f, 0f, -10f)
                ),
                point(0f, 0f, 0f),
                identityView(),
                depth(2500L),
                true,
                2500L
        );

        assertEquals(CorridorModeController.Mode.NORMAL, decision.mode);
        assertTrue(decision.modeChanged);
        assertTrue(decision.lateralOffsetMeters >= 2.0f);
    }

    @Test
    public void missingDepthFallsBackToRouteEvidence() {
        CorridorModeController controller = new CorridorModeController();
        CorridorModeController.Decision decision = null;
        for (long now = 0L; now <= 1600L; now += 500L) {
            decision = controller.update(
                    0f,
                    0f,
                    straightPath(),
                    null,
                    identityView(),
                    DepthCorridorAnalyzer.DepthEvidence.unavailable(now),
                    true,
                    now
            );
        }

        assertTrue(decision != null);
        assertEquals(CorridorModeController.Mode.CORRIDOR, decision.mode);
        assertFalse(decision.depthEvidenceUsable);
        assertTrue(decision.spatiallyEligible);
    }

    @Test
    public void transientDepthLossDoesNotExitCorridorMode() {
        CorridorModeController controller = new CorridorModeController();
        enter(controller);

        CorridorModeController.Decision afterLoss = controller.update(
                0f,
                0f,
                straightPath(),
                null,
                identityView(),
                new DepthCorridorAnalyzer.DepthEvidence(true, false, 1f, 1f, 0.6f, 30, 2000L),
                true,
                2000L
        );
        CorridorModeController.Decision recovered = controller.update(
                0f,
                0f,
                straightPath(),
                null,
                identityView(),
                depth(2400L),
                true,
                2400L
        );

        assertEquals(CorridorModeController.Mode.CORRIDOR, afterLoss.mode);
        assertEquals(CorridorModeController.Mode.CORRIDOR, recovered.mode);
    }

    @Test
    public void sidewaysDirectionEventuallyExitsCorridorMode() {
        CorridorModeController controller = new CorridorModeController();
        enter(controller);

        float[] sidewaysView = new float[16];
        sidewaysView[0] = 0f;
        sidewaysView[5] = 1f;
        sidewaysView[2] = -1f;
        sidewaysView[10] = 0f;
        sidewaysView[15] = 1f;

        CorridorModeController.Decision decision = controller.update(
                0f,
                0f,
                straightPath(),
                null,
                sidewaysView,
                depth(2500L),
                true,
                2500L
        );
        assertEquals(CorridorModeController.Mode.CORRIDOR, decision.mode);

        decision = controller.update(
                0f,
                0f,
                straightPath(),
                null,
                sidewaysView,
                depth(3600L),
                true,
                3600L
        );
        assertEquals(CorridorModeController.Mode.NORMAL, decision.mode);
        assertTrue(decision.modeChanged);
    }

    private void enter(CorridorModeController controller) {
        for (long now = 0L; now <= 1600L; now += 500L) {
            update(controller, straightPath(), now, depth(now));
        }
    }

    private CorridorModeController.Mode update(CorridorModeController controller,
                                                List<float[]> path,
                                                long now,
                                                DepthCorridorAnalyzer.DepthEvidence depth) {
        return controller.update(
                0f,
                0f,
                path,
                null,
                identityView(),
                depth,
                true,
                now
        ).mode;
    }

    private DepthCorridorAnalyzer.DepthEvidence depth(long now) {
        return new DepthCorridorAnalyzer.DepthEvidence(
                true, true, 1f, 1f, 2.5f, 30, now
        );
    }

    private List<float[]> straightPath() {
        return Arrays.asList(
                point(0f, 0f, 0f),
                point(0f, 0f, -5f),
                point(0f, 0f, -10f)
        );
    }

    private float[] identityView() {
        float[] view = new float[16];
        view[0] = 1f;
        view[5] = 1f;
        view[10] = 1f;
        view[15] = 1f;
        return view;
    }

    private float[] reverseView() {
        float[] view = new float[16];
        view[0] = -1f;
        view[5] = 1f;
        view[10] = -1f;
        view[15] = 1f;
        return view;
    }

    private float[] point(float x, float y, float z) {
        return new float[]{x, y, z};
    }
}
