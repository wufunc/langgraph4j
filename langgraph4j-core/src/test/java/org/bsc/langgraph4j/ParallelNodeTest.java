package org.bsc.langgraph4j;


import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.internal.node.ParallelNode;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParallelNodeTest {

    private <T> T measureTime(Supplier<T> runnable, Consumer<Duration> consumer) {
        final var start = Instant.now();

        var result = runnable.get();

        final var end = Instant.now();

        consumer.accept(Duration.between(start, end));

        return result;

    }
    private static AsyncNodeActionWithConfig<AgentState> createSyncAction(int taskId ) {

        return ( state, config ) -> {
                    long waitMills = (long) (Math.random() * 1000);
                    try {
                        // Simulate work
                        Thread.sleep(waitMills);
                    } catch (InterruptedException e) {
                        throw new CompletionException(e);
                    }

                    final var value = format( "TASK [%d] COMPLETED in [%d] MILLS BY [%s]", taskId, waitMills, Thread.currentThread().getName() );

                    System.out.println(value);

                    return completedFuture(Map.of(
                            format( "task-%d", taskId ),
                            value )); // return some result
                };
    }

    @Test
    public void parallelNodeTestWithSyncAction() throws Exception {

        var numberOfAsyncTask = 10;

        var actions = IntStream.range(0, numberOfAsyncTask)
                .mapToObj(ParallelNodeTest::createSyncAction)
                .toList();

        var parallelNode = new ParallelNode<>("parallelNodeTest", actions, Map.of());

        var parallelNodeAction = parallelNode.actionFactory().apply(CompileConfig.builder().build());

        Map<String, Object> initialData = Map.of("item1", "test1", "task-2", "test2");

        var agentState = new AgentState(new LinkedHashMap<>(initialData));

        var runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor( "parallelNodeTest", ForkJoinPool.commonPool() )
                .build();

        var result = measureTime(
                () -> parallelNodeAction.apply(agentState, runnableConfig).join(),
                duration -> System.out.println("Parallel Node with Sync Action managed by graph Took: " + duration.toMillis() + " ms") );


        result.entrySet().forEach(System.out::println);

        assertEquals(numberOfAsyncTask + 1, result.size());


    }

    private static AsyncNodeActionWithConfig<AgentState> createAsyncAction(int taskId, Executor executor) {

        return ( state, config ) ->
                CompletableFuture.supplyAsync(() -> {
                    long waitMills = (long) (Math.random() * 1000);
                    try {
                        // Simulate work
                        Thread.sleep(waitMills);
                    } catch (InterruptedException e) {
                        throw new CompletionException(e);
                    }

                    final var value = format( "TASK [%d] COMPLETED in [%d] MILLS BY [%s]", taskId, waitMills, Thread.currentThread().getName() );

                    System.out.println(value);

                    return Map.of(
                            format( "task-%d", taskId ),
                            value ); // return some result
                }, executor);
    }


    @Test
    public void parallelNodeTestWithAsyncAction() throws Exception {

        var numberOfAsyncTask = 10;

        var actions = IntStream.range(0, numberOfAsyncTask)
                .mapToObj(i -> createAsyncAction(i, ForkJoinPool.commonPool()))
                .toList();

        var parallelNode = new ParallelNode<>("parallelNodeTest", actions, Map.of());

        var parallelNodeAction = parallelNode.actionFactory().apply(CompileConfig.builder().build());

        Map<String, Object> initialData = Map.of("item1", "test1", "task-2", "test2");

        var agentState = new AgentState(new LinkedHashMap<>(initialData));

        var runnableConfig = RunnableConfig.builder().build();

        var result = measureTime(
                () -> parallelNodeAction.apply(agentState, runnableConfig).join(),
                duration -> System.out.println("Parallel Node with Async Action Took: " + duration.toMillis() + " ms") );

        result.entrySet().forEach(System.out::println);

        assertEquals(numberOfAsyncTask + 1, result.size());

    }

}
