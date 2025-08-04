package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class InterruptionTest {

    private AsyncNodeActionWithConfig<MessagesState<String>> _nodeAction(String id) {
        return node_async((state, config) ->
                Map.of("messages", id)
        );
    }

    @Test
    public void interruptAfterEdgeEvaluation() throws Exception {
        var saver = new MemorySaver();

        var workflow = new MessagesStateGraph<String>()
                .addNode("A", _nodeAction("A"))
                .addNode("B", _nodeAction("B"))
                .addNode("C", _nodeAction("C"))
                .addNode("D", _nodeAction("D"))
                .addConditionalEdges("B",
                        edge_async(state -> {
                            var message = state.lastMessage().orElse( END );
                            return message.equals("B") ? "D" : message ;
                        }),
                        EdgeMappings.builder()
                                .to("A")
                                .to( "C" )
                                .to( "D" )
                                .toEND()
                                .build())
                .addEdge( START, "A" )
                .addEdge("A", "B")
                .addEdge("C", END)
                .addEdge("D", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter("B")
                        .build());

        var runnableConfig = RunnableConfig.builder().build();

        var results = workflow.stream(Map.of(), runnableConfig)
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();

        assertIterableEquals(List.of(
                START,
                "A",
                "B"
        ), results);

        results = workflow.stream(GraphInput.resume(), runnableConfig )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
        assertIterableEquals(List.of(
                "D",
                END
        ), results );

        var snapshotForNodeB = workflow.getStateHistory(runnableConfig)
                                    .stream()
                                    .filter( s -> s.node().equals("B") )
                                    .findFirst()
                                    .orElseThrow();

        runnableConfig = workflow.updateState( snapshotForNodeB.config(), Map.of( "messages", "C"));

        results = workflow.stream(GraphInput.resume(), runnableConfig )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
        assertIterableEquals(List.of(
                "D",
                END
        ), results );
    }

    @Test
    public void interruptBeforeEdgeEvaluation() throws Exception {

        var saver = new MemorySaver();

        var workflow = new MessagesStateGraph<String>()
                .addNode("A", _nodeAction("A"))
                .addNode("B", _nodeAction("B"))
                .addNode("C", _nodeAction("C"))
                .addConditionalEdges("B",
                        edge_async(state ->
                                state.lastMessage().orElse( END ) ),
                        EdgeMappings.builder()
                                .to("A")
                                .to( "C" )
                                .toEND()
                                .build())
                .addEdge( START, "A" )
                .addEdge("A", "B")
                .addEdge("C", END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter("B")
                        .interruptBeforeEdge(true)
                        .build());

        var runnableConfig = RunnableConfig.builder().build();

        var results = workflow.stream(Map.of(), runnableConfig)
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();

        assertIterableEquals(List.of(
                START,
                "A",
                "B"
        ), results);

        runnableConfig = workflow.updateState( runnableConfig, Map.of( "messages", "C"));
        results = workflow.stream(GraphInput.resume(), runnableConfig )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
        assertIterableEquals(List.of(
                "C",
                END
        ), results );
    }
}

