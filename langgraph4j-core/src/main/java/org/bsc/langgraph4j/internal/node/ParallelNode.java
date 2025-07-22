package org.bsc.langgraph4j.internal.node;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class ParallelNode<State extends AgentState> extends Node<State> {
    private static final String PARALLEL_PREFIX = "__PARALLEL__";

    public static String formatNodeId( String nodeId ) {
        return format( "%s(%s)", PARALLEL_PREFIX, requireNonNull(nodeId, "nodeId cannot be null!"));
    }

    record AsyncParallelNodeAction<State extends AgentState>(
            String nodeId,
            List<AsyncNodeActionWithConfig<State>> actions,
            Map<String, Channel<?>> channels ) implements AsyncNodeActionWithConfig<State> {

        private CompletableFuture<Map<String, Object>> evalGenerator(AsyncGenerator<NodeOutput<State>> generator, Map<String, Object> initPartialState) {
            return generator.collectAsync(new ArrayList<>(), ArrayList::add)
                    .thenApply(list -> {
                        Map<String, Object> result = initPartialState;
                        for (var output : list) {
                            result = AgentState.updateState(result, output.state().data(), channels);
                        }
                        return result;
                    });
        }

        @SuppressWarnings("unchecked")
        private CompletableFuture<Map<String, Object>> evalNodeActionSync(AsyncNodeActionWithConfig<State> action, State state, RunnableConfig config) {

            return action.apply(state, config).thenCompose(partialState ->
                    partialState.entrySet().stream()
                            .filter(e -> e.getValue() instanceof AsyncGenerator)
                            .findFirst()
                            .map(generatorEntry -> {

                                var partialStateWithoutGenerator = partialState.entrySet().stream()
                                        .filter(e -> !Objects.equals(e.getKey(), generatorEntry.getKey()))
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                                return evalGenerator((AsyncGenerator<NodeOutput<State>>) generatorEntry.getValue(), partialStateWithoutGenerator);

                            })
                            .orElse(completedFuture(partialState))
            );
        }

        private CompletableFuture<Map<String, Object>> evalNodeActionAsync(AsyncNodeActionWithConfig<State> action,
                                                                           State state,
                                                                           RunnableConfig config,
                                                                           Executor executor) {
            return CompletableFuture.supplyAsync(() -> evalNodeActionSync(action, state, config).join(), executor);

        }

        @Override
        public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {

            var evalNodeAction = config.metadata( nodeId )
                    .filter( value -> value instanceof Executor)
                    .map( Executor.class::cast)
                    .map( executor -> (Function<AsyncNodeActionWithConfig<State>, CompletableFuture<Map<String, Object>>>) action -> evalNodeActionAsync(action, state, config, executor))
                    .orElseGet( () -> (Function<AsyncNodeActionWithConfig<State>, CompletableFuture<Map<String, Object>>>) action -> evalNodeActionSync(action, state, config));

            @SuppressWarnings("unchecked")
            final CompletableFuture<Map<String, Object>>[] actionsArray = actions.stream()
                    .map(evalNodeAction)
                    .toArray( CompletableFuture[]::new);

            return CompletableFuture.allOf(actionsArray).thenApply(v ->
                    Stream.of(actionsArray)
                            .map(CompletableFuture::join)
                            .reduce( state.data(),
                                    (result, actionResult) ->
                                             AgentState.updateState(result, actionResult, channels)
                                    /* , (f1, f2) -> AgentState.updateState( f1, f2, channels) )  */ )
            );

        }
    }

    public ParallelNode(String id, List<AsyncNodeActionWithConfig<State>> actions, Map<String, Channel<?>> channels ) {
        super(  formatNodeId(id),
                (config ) -> new AsyncParallelNodeAction<>(formatNodeId(id), actions, channels ));
    }

    @Override
    public final boolean isParallel() {
        return true;
    }

}
