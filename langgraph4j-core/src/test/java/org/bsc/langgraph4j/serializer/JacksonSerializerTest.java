package org.bsc.langgraph4j.serializer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.TypeMapper;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonSerializerTest {

    static class State extends AgentState {

        /**
         * needed for Jackson deserialization unless use a custom deserializer
         */
        protected State() {
            super( Map.of() );
        }

        /**
         * Constructs an AgentState with the given initial data.
         *
         * @param initData the initial data for the agent state
         */
        public State(Map<String, Object> initData) {
            super(initData);
        }
    }

    @Test
    public void serializeWithTypeInferenceTest() throws IOException, ClassNotFoundException {

        State state = new State( Map.of( "prop1", "value1") );

        JacksonStateSerializer<State> serializer = new JacksonStateSerializer<State>(State::new) {};

        Class<?> type = serializer.getStateType();

        assertEquals(State.class, type);

        byte[] bytes = serializer.objectToBytes(state);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        AgentState deserializedState = serializer.bytesToObject(bytes);

        assertNotNull(deserializedState);
        assertEquals( 1, deserializedState.data().size() );
        assertEquals( "value1", deserializedState.data().get("prop1") );
    }

    static class JacksonSerializer extends JacksonStateSerializer<AgentState> {

        public JacksonSerializer() {
            super(AgentState::new);
        }

        ObjectMapper getObjectMapper() {
            return objectMapper;
        }
    }

    static class NodeOutputTest extends NodeOutput<AgentState> {
        protected NodeOutputTest(String node, AgentState state, boolean subGraph) {
            super(node, state);
            setSubGraph(subGraph);
        }
    }

    @Test
    public void NodOutputJacksonSerializationTest() throws Exception {

        JacksonSerializer serializer = new JacksonSerializer();

        NodeOutput<AgentState> output = new NodeOutputTest("node", null, true);
        var mapper = serializer.getObjectMapper()
                            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        var json = mapper.writeValueAsString(output);
        assertEquals("""
                {"end":false,"node":"node","start":false,"state":null,"subGraph":true}""", json );

        output = new NodeOutputTest("node", null, false);
        json = serializer.getObjectMapper().writeValueAsString(output);

        assertEquals( """
                {"end":false,"node":"node","start":false,"state":null,"subGraph":false}""", json );
    }

    @Test
    public void TypeMapperTest() throws Exception {

        var mapper = new TypeMapper();

        var tr = new TypeReference<State>() {};
        System.out.println(tr.getType());
        mapper.register( new TypeMapper.Reference<State>("MyState") { } );

        var ref = mapper.getReference("MyState");

        assertTrue( ref.isPresent() );
        assertEquals( "MyState", ref.get().getTypeName() );
        System.out.println( ref.get().getType() );
        assertEquals( State.class, ref.get().getType() );
    }
}
