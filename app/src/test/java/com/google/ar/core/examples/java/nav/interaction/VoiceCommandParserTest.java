package com.google.ar.core.examples.java.nav.interaction;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VoiceCommandParserTest {

    private final VoiceCommandParser parser = new VoiceCommandParser();

    @Test
    public void parse_extractsEnglishDestination() {
        VoiceCommandParser.ParsedCommand command = parser.parse("Take me to room five twenty one");

        assertEquals(VoiceCommandParser.CommandType.NAVIGATE, command.type);
        assertEquals("room five twenty one", command.destination);
    }

    @Test
    public void parse_keepsCommandClassificationFreeOfSideEffects() {
        VoiceCommandParser.ParsedCommand command = parser.parse("where am I now");

        assertEquals(VoiceCommandParser.CommandType.STATUS_QUERY, command.type);
        assertEquals("where am I now", command.rawText);
    }

    @Test
    public void parse_returnsUnknownForUnmatchedText() {
        assertEquals(
                VoiceCommandParser.CommandType.UNKNOWN,
                parser.parse("please describe this object").type
        );
    }
}
