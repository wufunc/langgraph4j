package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.service.tool.ToolExecutor;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.langchain4j.serializer.jackson.LC4jJacksonStateSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;

import java.util.*;

/**
 * Interface representing an Agent Executor (AKA ReACT agent).
 */
public interface AgentExecutor {
    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentExecutor.class);

    /**
     * Represents the state of an agent.
     */
    class State extends MessagesState<ChatMessage> {

        public static final String FINAL_RESPONSE = "agent_response";

        /**
         * Constructs a new State with the given initialization data.
         *
         * @param initData the initialization data
         */
        public State(Map<String, Object> initData) {
            super(initData);
        }

        /**
         * Retrieves the agent final response.
         *
         * @return an Optional containing the agent final response if present
         */
        public Optional<String> finalResponse() {
            return value(FINAL_RESPONSE);
        }


    }

    /**
     * Enum representing different serializers for the agent state.
     */
    enum Serializers {

        STD(new LC4jStateSerializer<>(State::new) ),
        JSON(new LC4jJacksonStateSerializer<>(State::new));

        private final StateSerializer<State> serializer;

        /**
         * Constructs a new Serializers enum with the specified serializer.
         *
         * @param serializer the state serializer
         */
        Serializers(StateSerializer<State> serializer) {
            this.serializer = serializer;
        }

        /**
         * Retrieves the state serializer.
         *
         * @return the state serializer
         */
        public StateSerializer<State> object() {
            return serializer;
        }
    }


    static AsyncNodeActionWithConfig<AgentExecutor.State> executeTooL( LC4jToolService toolService ) {

        return AsyncNodeActionWithConfig.node_async((state, config) -> {
            log.trace("executeTools");

            var toolExecutionRequests = state.lastMessage()
                    .filter(m -> ChatMessageType.AI == m.type())
                    .map(m -> (AiMessage) m)
                    .filter(AiMessage::hasToolExecutionRequests)
                    .map(AiMessage::toolExecutionRequests);

            if (toolExecutionRequests.isEmpty()) {
                return Map.of("agent_response", "no tool execution request found!");
            }

            var result = toolExecutionRequests.get().stream()
                    .map(toolService::execute)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();

            return Map.of("messages", result);

        });
    }

    private static AsyncCommandAction<State> shouldContinue() {
        return AsyncCommandAction.command_async( (state, config ) ->
            state.finalResponse()
                .map(res -> new Command(Agent.END_LABEL))
                .orElse(new Command(Agent.CONTINUE_LABEL) ));
    }

    /**
     * Builder class for constructing a graph of agent execution.
     */
    class Builder extends AgentExecutorBuilder<State,Builder> {

        /**
         * Sets the tool specification for the graph builder.
         *
         * @param objectsWithTools the tool specification
         * @return the updated GraphBuilder instance
         */
        @Deprecated
        public Builder toolSpecification(Object objectsWithTools) {
            super.toolsFromObject( objectsWithTools );
            return this;
        }

        @Deprecated
        public Builder toolSpecification(ToolSpecification spec, ToolExecutor executor) {
            super.tool(spec, executor);
            return this;
        }

        /**
         * Sets the tool specification for the graph builder.
         *
         * @param toolSpecification the tool specifications
         * @return the updated GraphBuilder instance
         */
        @Deprecated
        public Builder toolSpecification(LC4jToolService.Specification toolSpecification) {
            super.tool(toolSpecification.value(), toolSpecification.executor());
            return this;
        }


        /**
         * Sets the state serializer for the graph builder.
         *
         * @param stateSerializer the state serializer
         * @return the updated GraphBuilder instance
         */
        public Builder stateSerializer(StateSerializer<State> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return this;
        }

        /**
         * Builds the state graph.
         *
         * @return the constructed StateGraph
         * @throws GraphStateException if there is an error in the graph state
         */
        public StateGraph<State> build() throws GraphStateException {

            if (streamingChatModel != null && chatModel != null) {
                throw new IllegalArgumentException("chatLanguageModel and streamingChatLanguageModel are mutually exclusive!");
            }
            if (streamingChatModel == null && chatModel == null) {
                throw new IllegalArgumentException("a chatLanguageModel or streamingChatLanguageModel is required!");
            }

            if (stateSerializer == null) {
                stateSerializer = Serializers.STD.object();
            }

            final LC4jToolService toolService = new LC4jToolService(toolMap());

            return Agent.<ChatMessage,State>builder()
                    .stateSerializer(stateSerializer)
                    .schema( State.SCHEMA )
                    .callModelAction( new CallModel<>( this ) )
                    .executeToolsAction( executeTooL( toolService ) )
                    .shouldContinueEdge(shouldContinue())
                    .build();

        }
    }

    /**
     * Creates a new Builder instance.
     *
     * @return a new Builder
     */
    static Builder builder() {
        return new Builder();
    }

}
