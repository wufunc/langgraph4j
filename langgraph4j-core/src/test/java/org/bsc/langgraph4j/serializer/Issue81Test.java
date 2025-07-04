package org.bsc.langgraph4j.serializer;

import org.bsc.langgraph4j.serializer.plain_text.PlainTextStateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Issue81Test {

    String generateLongString( String pattern ) {
        return pattern.repeat(70_000 );
    }

    @Test
    public void testSerializeLongString() throws IOException, ClassNotFoundException {

        Map<String,Object> largeMap = Map.of(
                "largeString1", generateLongString( "A"),
                "largeString2", generateLongString( "B"),
                "largeString3", generateLongString( "C" )
        );

        var ser = new JacksonStateSerializer<>( AgentState::new ) {

        };

        var cloned = ser.cloneObject( largeMap );
    }
}