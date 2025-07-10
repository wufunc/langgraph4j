package org.bsc.langgraph4j.spring.ai.agentexecutor;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.serializer.std.SpringAIStateSerializer;
import org.bsc.langgraph4j.spring.ai.tool.SpringAIToolService;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_REMOVAL;
import static org.bsc.langgraph4j.state.AgentState.MARK_FOR_RESET;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mapOf;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

/**
 * Interface representing an Agent Executor (AKA ReACT agent).
 * This implementation make in evidence the tools execution using and action dispatcher node
 *              ┌─────┐
 *              │start│
 *              └─────┘
 *                 |
 *              ┌─────┐
 *              │model│
 *              └─────┘
 *                |
 *          ┌─────────────────┐
 *          │action_dispatcher│
 *          └─────────────────┘_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _
 *          |                 \              \                    \
 *       ┌────┐         ┌─────────────┐ ┌─────────────┐      ┌─────────────┐
 *       │stop│         │ tool_name 1 │ │ tool_name 2 │......│ tool_name N │
 *       └────┘         └─────────────┘ └─────────────┘      └─────────────┘
 */
public interface AgentExecutorEx {

    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentExecutorEx.class);

    /**
     * Represents the state of an agent in a system.
     * This class extends {@link AgentState} and defines constants for keys related to input, agent outcome,
     * and intermediate steps. It includes a static map schema that specifies how these keys should be handled.
     */
    class State extends MessagesState<Message> {

        static final Map<String, Channel<?>> SCHEMA = mergeMap(
                MessagesState.SCHEMA,
                Map.of( "tool_execution_results", Channels.appender(ArrayList::new) ) );

        /**
         * Constructs a new State with the given initialization data.
         *
         * @param initData the initialization data
         */
        public State(Map<String, Object> initData) {
            super(initData);
        }

        public List<ToolResponseMessage> toolExecutionResults() {
            return this.<List<ToolResponseMessage>>value("tool_execution_results")
                    .orElseThrow(() -> new RuntimeException("messages not found"));
        }

        public Optional<String> nextAction() {
            return value("next_action");
        }

        private  List<AssistantMessage.ToolCall> toolCallsByName( String actionName ) {
            return lastMessage()
                    .filter(m -> MessageType.ASSISTANT == m.getMessageType())
                    .map(AssistantMessage.class::cast)
                    .filter(AssistantMessage::hasToolCalls)
                    .map(AssistantMessage::getToolCalls)
                    .map(requests -> requests.stream()
                            .filter(req -> Objects.equals(req.name(), actionName)).toList())
                    .orElseGet(List::of)
                    ;
        }
    }


    enum ApprovalState {
        APPROVED,
        REJECTED
    }

    final class ApprovalNodeAction implements AsyncNodeActionWithConfig<State>, InterruptableAction<State> {

        private final String resumePropertyName;
        private final BiFunction<String, State, InterruptionMetadata<State>> interruptionMetadataProvider;

        private ApprovalNodeAction( Builder builder ) {
            this.resumePropertyName = builder.resumePropertyName;
            this.interruptionMetadataProvider = builder.interruptionMetadataProvider;
        }

        @Override
        public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
            return completedFuture(Map.of());
        }

        @Override
        public Optional<InterruptionMetadata<State>> interrupt(String nodeId, State state) {
            if( state.value( resumePropertyName ).isEmpty() ) {
                var metadata = interruptionMetadataProvider.apply(nodeId,state);
                return Optional.of(metadata);
            }
            return Optional.empty();
        }

        AsyncCommandAction<State> edgeAction() {
            return (state, config) -> {
                var result = new CompletableFuture<Command>();

                if( state.value( resumePropertyName ).isEmpty() ) {
                    result.completeExceptionally( new IllegalStateException(format("resume property '%s' not found!", resumePropertyName) ));
                    return result;
                }

                var resumeState = state.<String>value( resumePropertyName )
                                    .orElseThrow( () -> new IllegalStateException(format("resume property '%s' not found!", resumePropertyName) ));

                if( Objects.equals( resumeState, ApprovalState.APPROVED.name() )) {
                    result.complete( new Command( resumeState,
                            Map.of(resumePropertyName, MARK_FOR_REMOVAL)));

                }
                else {
                    var actionName = state.nextAction()
                            .map( v -> v.replace("approval_", "") )
                            .orElseThrow( () -> new IllegalStateException("no next action found!"));

                    var tools = state.toolCallsByName( actionName );

                    if(tools.isEmpty())  {
                        throw new IllegalStateException("no tool execution request found!");
                    }

                    var toolResponses = tools.stream().map( toolCall ->
                        new ToolResponseMessage.ToolResponse(toolCall.id(), actionName, "execution has been DENIED!")
                    ).toList();

                    result.complete( new Command( resumeState,
                            Map.of( "messages", new ToolResponseMessage( toolResponses ),
                                    "tool_execution_results", "execution has been DENIED!",
                                    resumePropertyName, MARK_FOR_REMOVAL)));

                }
                return result;
            };
        }
        public static  Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String resumePropertyName;
            private BiFunction<String, AgentExecutorEx.State, InterruptionMetadata<State>> interruptionMetadataProvider;

            public Builder resumePropertyName( String name  ) {
                resumePropertyName = name;
                return this;
            }
            public Builder interruptionMetadataProvider(  BiFunction<String, State, InterruptionMetadata<State>> provider  ) {
                interruptionMetadataProvider = provider;
                return this;
            }

            public ApprovalNodeAction build() {
                Objects.requireNonNull(resumePropertyName, "resumePropertyName cannot be null!");
                Objects.requireNonNull(interruptionMetadataProvider, "interruptionMetadataProvider cannot be null!");
                return new ApprovalNodeAction(this);
            }

        }
    }

    /**
     * Class responsible for building a state graph.
     */
    class Builder extends AgentExecutorBuilder<Builder, State> {

        private final Map<String,ApprovalNodeAction> approvals = new LinkedHashMap<>();

        public Builder approvalOn( String actionId, ApprovalNodeAction action ) {
            approvals.put( actionId, action  );
            return this;
        }

        /**
         * Builds and returns a StateGraph with the specified configuration.
         * Initializes the stateSerializer if it's null. Then, constructs a new StateGraph object using the provided schema
         * and serializer, adds an initial edge from the START node to "agent", and then proceeds to add nodes for "agent" and
         * "action". It also sets up conditional edges from the "agent" node based on whether or not to continue.
         *
         * @return A configured StateGraph object.
         * @throws GraphStateException If there is an issue with building the graph state.
         */
        public StateGraph<State> build() throws GraphStateException {

            if (stateSerializer == null) {
                stateSerializer = new SpringAIStateSerializer<>(AgentExecutorEx.State::new);
            }

            var chatService = new ChatService(this);

            var tools = chatService.tools();

            // verify approval
            for (var approval : approvals.keySet()) {

                tools.stream()
                        .filter( tool -> Objects.equals( tool.getToolDefinition().name(), approval) )
                        .findAny()
                        .orElseThrow( () -> new IllegalArgumentException(format("approval action %s not found!", approval) ));
            }

            final var toolService = new SpringAIToolService(tools);

            AsyncNodeActionWithConfig<State> callModelAction = CallModel.of( chatService, streaming );

            AsyncNodeAction<State> dispatchToolsAction = dispatchTools( approvals.keySet() );

            final EdgeAction<State> dispatchAction = (state) ->
                    state.nextAction().orElse("model");

            var graph = new StateGraph<>(State.SCHEMA, stateSerializer)
                    .addNode("model",callModelAction )
                    .addNode("action_dispatcher", dispatchToolsAction)
                    .addEdge(START, "model")
                    .addConditionalEdges("model",
                            edge_async(AgentExecutorEx::shouldContinue),
                            EdgeMappings.builder()
                                    .to("action_dispatcher", "continue")
                                    .toEND("end" )
                                    .build()) ;

            var actionMappingBuilder  =  EdgeMappings.builder()
                    .to( "model")
                    .toEND();

            for (var tool : tools) {

                var tool_name = tool.getToolDefinition().name();

                if( approvals.containsKey(tool_name) ) {

                    var approval_nodeId = format("approval_%s", tool_name);

                    var approvalAction = approvals.get(tool_name);

                    graph.addNode( approval_nodeId, approvalAction );

                    graph.addConditionalEdges( approval_nodeId, approvalAction.edgeAction() ,
                            EdgeMappings.builder()
                                    .to( "model", ApprovalState.REJECTED.name())
                                    .to( tool_name, ApprovalState.APPROVED.name() )
                                    .build()
                            );

                    actionMappingBuilder.to(approval_nodeId);
                }
                else {
                    actionMappingBuilder.to(tool_name);
                }

                graph.addNode(tool_name,
                        state -> executeTools( state, toolService, tool_name));
                graph.addEdge(tool_name, "action_dispatcher");

            }

            return   graph.addConditionalEdges( "action_dispatcher",
                    edge_async(dispatchAction),
                    actionMappingBuilder.build())
                    ;
        }

    }

    /**
     * Returns a new instance of {@link Builder}.
     *
     * @return a new {@link Builder} object
     */
    static Builder builder() {
        return new Builder();
    }

    private static AsyncNodeAction<State> dispatchTools(Set<String> approvals ) {

        return AsyncNodeAction.node_async(( state ) -> {
            log.trace( "DispatchTools" );

            var toolExecutionRequests = state.lastMessage()
                    .filter( m -> MessageType.ASSISTANT==m.getMessageType() )
                    .map( AssistantMessage.class::cast )
                    .filter(AssistantMessage::hasToolCalls)
                    .map(AssistantMessage::getToolCalls);

            if( toolExecutionRequests.isEmpty() ) {
                return Map.of("agent_response", "no tool execution request found!" );
            }

            var requests = toolExecutionRequests.get();

            return requests.stream()
                    .filter( request -> state.toolExecutionResults().stream()
                            .flatMap( r -> r.getResponses().stream() )
                            .noneMatch( r -> Objects.equals(r.name(), request.name())))
                    .findFirst()
                    .map( result -> ( approvals.contains(result.name()) ?
                            format( "approval_%s", result.name() ) :
                            result.name()))
                    .map( actionId -> Map.<String,Object>of( "next_action", actionId ))
                    .orElseGet( () ->  Map.of("messages",  state.toolExecutionResults(),
                            "tool_execution_results", MARK_FOR_RESET, /* reset results */
                            "next_action", MARK_FOR_RESET  /* remove element */ ));

        });

    }
    /**
     * Executes tools based on the provided state.
     *
     * @param state The current state containing necessary information to execute tools.
     * @return A CompletableFuture containing a map with the intermediate steps, if successful. If there is no agent outcome or the tool service execution fails, an appropriate exception will be thrown.
     */
    static CompletableFuture<Map<String, Object>> executeTools(State state, SpringAIToolService toolService, String actionName ) {
        log.trace( "ExecuteTool" );

        var toolCalls = state.toolCallsByName(actionName);

        if( toolCalls.isEmpty() ) {
            return CompletableFuture.failedFuture( new IllegalArgumentException("no tool execution request found!") );
        }

        return toolService.executeFunctions( toolCalls )
                .thenApply(result -> Map.of("tool_execution_results", result));

    }

    /**
     * Determines whether the game should continue based on the current state.
     *
     * @param state The current state of the game.
     * @return "end" if the game should end, otherwise "continue".
     */
    static String shouldContinue(State state) {

        var message = state.lastMessage().orElseThrow();

        var finishReason = message.getMetadata().getOrDefault("finishReason", "");

        if (Objects.equals(finishReason, "STOP")) {
            return "end";
        }

        if (message instanceof AssistantMessage assistantMessage) {
            if (assistantMessage.hasToolCalls()) {
                return "continue";
            }
        }
        return "end";
    }
}

