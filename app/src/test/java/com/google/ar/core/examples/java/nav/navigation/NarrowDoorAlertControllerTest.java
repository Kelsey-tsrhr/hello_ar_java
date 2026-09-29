package com.google.ar.core.examples.java.nav.navigation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NarrowDoorAlertControllerTest {

    @Test
    public void alertsOnlyAfterStableEvidenceAndDoesNotRepeatWhileVisible() {
        NarrowDoorAlertController controller = new NarrowDoorAlertController();
        DepthDoorwayAnalyzer.DoorEvidence evidence = evidence(0L);

        assertFalse(controller.update(evidence, 0L).shouldAlert);
        assertTrue(controller.update(evidence(700L), 700L).shouldAlert);
        assertFalse(controller.update(evidence(1400L), 1400L).shouldAlert);
    }

    @Test
    public void invalidEvidenceResetsTheCurrentDoorEvent() {
        NarrowDoorAlertController controller = new NarrowDoorAlertController();
        assertFalse(controller.update(evidence(0L), 0L).shouldAlert);
        assertFalse(controller.update(
                DepthDoorwayAnalyzer.DoorEvidence.unavailable(500L),
                500L
        ).shouldAlert);
        assertFalse(controller.update(evidence(600L), 600L).shouldAlert);
        assertTrue(controller.update(evidence(1300L), 1300L).shouldAlert);
    }

    private DepthDoorwayAnalyzer.DoorEvidence evidence(long timestamp) {
        return new DepthDoorwayAnalyzer.DoorEvidence(
                true,
                true,
                true,
                0.82f,
                2.5f,
                3,
                3,
                timestamp
        );
    }
}
