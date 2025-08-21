package org.bsc.langgraph4j.serializer.plain_text;

import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.utils.Types;

import java.io.*;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;


public abstract class PlainTextStateSerializer<State extends AgentState> extends StateSerializer<State> {

    protected PlainTextStateSerializer(AgentStateFactory<State> stateFactory) {
        super(stateFactory);
    }

    @Override
    public String contentType() {
        return "plain/text";
    }


    @SuppressWarnings("unchecked")
    public Optional<Class<State>> getStateType() {
        return Types.parameterizedType(getClass())
                .map(ParameterizedType::getActualTypeArguments)
                .filter( args -> args.length > 0 )
                .map( args -> (Class<State>)args[0] );
    }

    public State read( String data ) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytesStream =  new ByteArrayOutputStream();

        try(ObjectOutputStream out = new ObjectOutputStream( bytesStream )) {
            Serializer.writeUTF(data, out);
            out.flush();
        }

        try(ObjectInput in = new ObjectInputStream( new ByteArrayInputStream( bytesStream.toByteArray() ) ) ) {
            return read(in);
        }

    }

    public State read( Reader reader ) throws IOException, ClassNotFoundException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }
        return read( sb.toString() );
    }

}
