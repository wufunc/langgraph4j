package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.exception.SubGraphInterruptionException;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncCommandAction.command_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;
import static org.junit.jupiter.api.Assertions.*;

public class CompiledSubGraphTest {

    static class MyState extends MessagesState<String> {

        public MyState(Map<String, Object> initData) {
            super(initData);
        }

        boolean resumeSubgraph() {
            return this.<Boolean>value("resume_subgraph")
                    .orElse(false);
        }
    }

    private AsyncNodeAction<MyState> _makeNode(String withMessage) {
        return node_async(state ->
                Map.of("messages", format("[%s]", withMessage))
        );
    }

    private AsyncCommandAction<MyState> _makeCommandNode(Command command) {
        return command_async((state, config) ->
                requireNonNull(command)
        );
    }

    private AsyncCommandAction<MyState> _makeCommandNode(String goToNode) {
        return _makeCommandNode(new Command(goToNode));
    }

    private AsyncNodeAction<MyState> _makeSubgraphNode(String parentNodeId, CompiledGraph<MyState> subGraph) {
        final var runnableConfig = RunnableConfig.builder()
                .threadId(format("%s_subgraph", parentNodeId))
                .build();
        return node_async(state -> {

            var input = (state.resumeSubgraph()) ?
                    GraphInput.resume() :
                    GraphInput.args(state.data());

            var output = subGraph.stream(input, runnableConfig).stream()
                    .reduce((a, b) -> b)
                    .orElseThrow();

            if (!output.isEND()) {
                throw new SubGraphInterruptionException(parentNodeId,
                        output.node(),
                        mergeMap(output.state().data(), Map.of("resume_subgraph", true)));
            }
            return mergeMap(output.state().data(), Map.of("resume_subgraph", AgentState.MARK_FOR_REMOVAL));
        });
    }

    private CompiledGraph<MyState> subGraph(BaseCheckpointSaver saver) throws Exception {

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptAfter("NODE3.2")
                .build();

        var stateSerializer = new ObjectStreamStateSerializer<>(MyState::new);

        return new StateGraph<>(MyState.SCHEMA, stateSerializer)
                .addEdge(START, "NODE3.1")
                .addNode("NODE3.1", _makeNode("NODE3.1"))
                .addNode("NODE3.2", _makeNode("NODE3.2"))
                .addNode("NODE3.3", _makeNode("NODE3.3"))
                .addNode("NODE3.4", _makeNode("NODE3.4"))
                .addEdge("NODE3.1", "NODE3.2")
                .addEdge("NODE3.2", "NODE3.3")
                .addEdge("NODE3.3", "NODE3.4")
                .addEdge("NODE3.4", END)
                .compile(compileConfig);
    }

    @Test
    public void testCompileSubGraphWithInterruptionUsingException() throws Exception {
        final var console = System.console();

        var saver = new MemorySaver();

        var stateSerializer = new ObjectStreamStateSerializer<>(MyState::new);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var subGraph = subGraph(saver); // create subgraph

        var parentGraph =  new StateGraph<>(MyState.SCHEMA, stateSerializer)
                .addEdge(START, "NODE1")
                .addNode("NODE1", _makeNode("NODE1"))
                .addNode("NODE2", _makeNode("NODE2"))
                .addNode("NODE3", _makeSubgraphNode("NODE3", subGraph))
                .addNode("NODE4", _makeNode("NODE4"))
                .addNode("NODE5", _makeNode("NODE5"))
                .addEdge("NODE1", "NODE2")
                .addEdge("NODE2", "NODE3")
                .addEdge("NODE3", "NODE4")
                .addEdge("NODE4", "NODE5")
                .addEdge("NODE5", END)
                .compile(compileConfig);
        var runnableConfig = RunnableConfig.builder().build();

        var input = GraphInput.args(Map.of());

        do {
            try {
                for (var output : parentGraph.stream(input, runnableConfig)) {

                    console.format("output: %s\n", output);
                }

                break;
            }
            catch( Exception ex ) {
                var interruptException = SubGraphInterruptionException.from(ex);
                if( interruptException.isPresent() ) {

                    console.format("SubGraphInterruptionException: %s\n", interruptException.get().getMessage());
                    var interruptionState = interruptException.get().state();


                    // ==== METHOD 1 =====
                    // FIND NODE BEFORE SUBGRAPH AND RESUME
                    /*
                    StateSnapshot<?> lastNodeBeforeSubGraph = workflow.getStateHistory(runnableConfig).stream()
                                                                .skip(1)
                                                                .findFirst()
                                                                .orElseThrow( () -> new IllegalStateException("lastNodeBeforeSubGraph is null"));
                    var nodeBeforeSubgraph = lastNodeBeforeSubGraph.node();
                    runnableConfig = workflow.updateState( lastNodeBeforeSubGraph.config(), interruptionState );
                    */

                    // ===== METHOD 2 =======
                    // UPDATE STATE ASSUMING TO BE ON NODE BEFORE SUBGRAPH ('NODE2') AND RESUME
                    var nodeBeforeSubgraph = "NODE2";
                    runnableConfig = parentGraph.updateState( runnableConfig, interruptionState, nodeBeforeSubgraph );
                    input = GraphInput.resume();

                    console.format( "RESUME GRAPH FROM END OF NODE: %s\n", nodeBeforeSubgraph);
                    continue;
                }

                throw ex;
            }
        } while( true );

    }

    @Test
    public void testCompileSubGraphWithInterruptionSharingSaver() throws Exception {
        final var console = System.console();

        var saver = new MemorySaver();

        var stateSerializer = new ObjectStreamStateSerializer<>(MyState::new);

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        var subGraph = subGraph(saver); // create subgraph

        var parentGraph =  new StateGraph<>(MyState.SCHEMA, stateSerializer)
                .addEdge(START, "NODE1")
                .addNode("NODE1", _makeNode("NODE1"))
                .addNode("NODE2", _makeNode("NODE2"))
                .addNode("NODE3", subGraph)
                .addNode("NODE4", _makeNode("NODE4"))
                .addNode("NODE5", _makeNode("NODE5"))
                .addEdge("NODE1", "NODE2")
                .addEdge("NODE2", "NODE3")
                .addEdge("NODE3", "NODE4")
                .addEdge("NODE4", "NODE5")
                .addEdge("NODE5", END)
                .compile(compileConfig);
        var runnableConfig = RunnableConfig.builder().build();

        var input = GraphInput.args(Map.of());

        var graphIterator = parentGraph.stream(input, runnableConfig);

        var output = graphIterator.stream()
                .peek( out -> console.format("output: %s\n", out) )
                .reduce((a, b) -> b);

        assertTrue( output.isPresent() );

        assertFalse( output.get().isEND() );
        assertTrue( output.get().isSubGraph() );

        var iteratorResult = AsyncGenerator.resultValue(graphIterator);

        assertTrue( iteratorResult.isPresent() );
        assertInstanceOf(InterruptionMetadata.class, iteratorResult.get());

        input = GraphInput.resume();

        graphIterator = parentGraph.stream(input, runnableConfig);

        output = graphIterator.stream()
                .peek( out -> console.format("output: %s\n", out) )
                .reduce((a, b) -> b);

        assertTrue( output.isPresent() );
        assertTrue( output.get().isEND() );

        assertIterableEquals(List.of(
                "[NODE1]",
                "[NODE2]",
                "[NODE3.1]",
                "[NODE3.2]",
                "[NODE3.3]",
                "[NODE3.4]",
                "[NODE4]",
                "[NODE5]"), output.get().state().messages() );
    }

    @Test
    public void testCompileSubGraphWithInterruptionWithDifferentSaver() throws Exception {
        final var console = System.console();

        var parentSaver = new MemorySaver();

        var stateSerializer = new ObjectStreamStateSerializer<>(MyState::new);

        BaseCheckpointSaver childSaver = new MemorySaver();
        var subGraph = subGraph( childSaver ); // create subgraph

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(parentSaver)
                .build();

        var parentGraph =  new StateGraph<>(MyState.SCHEMA, stateSerializer)
                .addEdge(START, "NODE1")
                .addNode("NODE1", _makeNode("NODE1"))
                .addNode("NODE2", _makeNode("NODE2"))
                .addNode("NODE3", subGraph)
                .addNode("NODE4", _makeNode("NODE4"))
                .addNode("NODE5", _makeNode("NODE5"))
                .addEdge("NODE1", "NODE2")
                .addEdge("NODE2", "NODE3")
                .addEdge("NODE3", "NODE4")
                .addEdge("NODE4", "NODE5")
                .addEdge("NODE5", END)
                .compile(compileConfig);

        var runnableConfig = RunnableConfig.builder().build();

        var input = GraphInput.args(Map.of());

        var graphIterator = parentGraph.stream(input, runnableConfig);

        var output = graphIterator.stream()
                .peek( out -> console.format("output: %s\n", out) )
                .reduce((a, b) -> b);

        assertTrue( output.isPresent() );

        assertFalse( output.get().isEND() );
        assertTrue( output.get().isSubGraph() );

        var iteratorResult = AsyncGenerator.resultValue(graphIterator);

        assertTrue( iteratorResult.isPresent() );
        assertInstanceOf(InterruptionMetadata.class, iteratorResult.get());

        input = GraphInput.resume();

        graphIterator = parentGraph.stream(input, runnableConfig);

        output = graphIterator.stream()
                .peek( out -> console.format("output: %s\n", out) )
                .reduce((a, b) -> b);

        assertTrue( output.isPresent() );
        assertTrue( output.get().isEND() );

        assertIterableEquals(List.of(
                "[NODE1]",
                "[NODE2]",
                "[NODE3.1]",
                "[NODE3.2]",
                "[NODE3.3]",
                "[NODE3.4]",
                "[NODE4]",
                "[NODE5]"), output.get().state().messages() );
    }

}