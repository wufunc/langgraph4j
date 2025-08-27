package org.bsc.langgraph4j.subgraph;

import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.AgentState;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public final class SubGraphOutput<State extends AgentState> extends NodeOutput<State> {

    public static <State extends AgentState> SubGraphOutput<State> of( NodeOutput<State> output, String subGraphId ) {
        if( output instanceof SubGraphOutput<State> subGraphOutput) {
            return subGraphOutput;
        }
        else {
            return new SubGraphOutput<>( output.node(), output.state(), subGraphId );
        }

    }
    /**
     * subgraph node id
     */
    private final String subGraphId;

    public String subGraphId() {
        return subGraphId;

    }

    private SubGraphOutput(String node, State state, String subGraphId) {
        super(node, state);
        this.subGraphId = requireNonNull(subGraphId, "subGraphId cannot be null");
    }

    @Override
    public String toString() {
        return format("SubGraphOutput{node=%s, subGraphId=%s, state=%s}",
                node(),
                subGraphId(),
                state());
    }

}
