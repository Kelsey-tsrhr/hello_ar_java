package com.google.ar.core.examples.java.nav.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.ar.core.examples.java.nav.api.model.Node;

import org.junit.Test;

import java.util.Arrays;

public class NodeNameMatcherTest {

    @Test
    public void normalize_convertsSpokenEnglishDigits() {
        assertEquals("521", NodeNameMatcher.normalize("five two one"));
    }

    @Test
    public void normalize_convertsEnglishCardinalNumber() {
        assertEquals("521", NodeNameMatcher.normalize("five hundred and twenty one"));
    }

    @Test
    public void normalize_stripsEnglishRoomNoise() {
        assertEquals("521", NodeNameMatcher.normalize("room five twenty one"));
    }

    @Test
    public void normalize_preservesLetterPrefix() {
        assertEquals("A521", NodeNameMatcher.normalize("A five two one"));
    }

    @Test
    public void findBestMatch_matchesEnglishDoorplateSpeech() {
        Node target = new Node("521", 0f, 0f, 0f);
        Node other = new Node("512", 0f, 0f, 0f);

        NodeNameMatcher.MatchResult match = NodeNameMatcher.findBestMatch(
                "five hundred and twenty one",
                Arrays.asList(other, target)
        );

        assertNotNull(match);
        assertEquals("521", match.node.id);
        assertEquals("521", match.normalizedInput);
    }
}
