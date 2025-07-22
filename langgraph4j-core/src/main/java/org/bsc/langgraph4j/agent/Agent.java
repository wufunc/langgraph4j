package org.bsc.langgraph4j.agent;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * Interface representing an Agent Executor (AKA ReACT agent).
 * This implementation make in evidence the tools execution using and action dispatcher node
 * <pre>
 *              ┌─────┐
 *              │start│
 *              └─────┘
 *                 |
 *              ┌─────┐
 *              │model│
 *              └─────┘
 *                 |
 *          ┌─────────────────┐
 *          │ action_executor │
 *          └─────────────────┘
 *                 |
 *               ┌────┐
 *               │stop│
 *               └────┘
 * </pre>
 */
public interface Agent {

    String CONTINUE_LABEL = "continue";
    String END_LABEL = "end";

    static <M, S extends MessagesState<M>> Builder<M,S> builder() {
        return new Builder<>();
    }

    class Builder<M, S extends MessagesState<M>> {

        private StateSerializer<S> stateSerializer;
        private AsyncNodeActionWithConfig<S> callModelAction;
        private AsyncNodeActionWithConfig<S> executeToolsAction;
        private AsyncCommandAction<S> shouldContinueEdge;
        private Map<String, Channel<?>> schema;

        public Builder<M,S> stateSerializer(StateSerializer<S> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return this;
        }

        public Builder<M,S> schema(Map<String, Channel<?>> schema) {
            this.schema = schema;
            return this;
        }

        public Builder<M,S> callModelAction(AsyncNodeActionWithConfig<S> callModelAction) {
            this.callModelAction = callModelAction;
            return this;
        }

        public Builder<M,S> executeToolsAction(AsyncNodeActionWithConfig<S> executeToolsAction) {
            this.executeToolsAction = executeToolsAction;
            return this;
        }

        public Builder<M,S> shouldContinueEdge(AsyncCommandAction<S> shouldContinueEdge) {
            this.shouldContinueEdge = shouldContinueEdge;
            return this;
        }

        public StateGraph<S> build() throws GraphStateException {

            return new StateGraph<>(
                    requireNonNull(schema, "schema is required!"),
                    requireNonNull(stateSerializer, "stateSerializer is required!"))
                    .addEdge(START, "agent")
                    .addNode("agent",  requireNonNull(callModelAction, "callModelAction is required!") )
                    .addNode("action", requireNonNull(executeToolsAction, "executeToolsAction is required!") )
                    .addConditionalEdges(
                            "agent",
                            requireNonNull(shouldContinueEdge, "shouldContinue is required!"),
                            EdgeMappings.builder()
                                    .to("action", CONTINUE_LABEL)
                                    .toEND(END_LABEL)
                                    .build()
                    )
                    .addEdge("action", "agent")
                    ;

        }

    }

}
