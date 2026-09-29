package com.google.ar.core.examples.java.nav.navigation;

import static org.junit.Assert.assertEquals;

import com.google.ar.core.examples.java.nav.api.model.Node;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class NavigationPathPlannerTest {

    @Test
    public void plan_returnsDistinctRouteNodeIds() {
        Node start = node("A", 0f, 0f);
        Node corridor = node("B", 1f, 0f);
        Node target = node("521", 2f, 0f);

        NavigationPathPlanner.PathPlan plan = new NavigationPathPlanner().plan(
                0f,
                0f,
                0f,
                target,
                Arrays.asList(start, corridor),
                Collections.singletonList(target)
        );

        assertEquals(Arrays.asList("A", "B", "521"), plan.nodeIds);
        assertEquals("A", plan.startNode.id);
    }

    private Node node(String id, float x, float z) {
        Node node = new Node(id, x, 0f, z);
        node.arX = x;
        node.arY = 0f;
        node.arZ = z;
        node.type = "doorplate".equals(id) ? "doorplate" : "camera";
        return node;
    }
}
