#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import ${groupId}.RunnableConfig;
import ${groupId}.action.AsyncNodeActionWithConfig;
import ${groupId}.action.NodeActionWithConfig;
import ${groupId}.prebuilt.MessagesState;
import ${groupId}.spring.ai.generators.StreamingChatGenerator;
import org.springframework.ai.chat.messages.Message;

import java.util.Map;

import static ${groupId}.action.AsyncNodeActionWithConfig.node_async;

class CallModel<State extends MessagesState<Message>> implements NodeActionWithConfig<State> {

    public static <State extends MessagesState<Message>> AsyncNodeActionWithConfig<State> of(ChatService chatService, boolean streaming ) {
        return node_async(new CallModel<>(chatService, streaming));
    }

    private final ChatService chatService;
    private final boolean streaming;

    protected CallModel(ChatService chatService, boolean streaming) {
        this.chatService = chatService;
        this.streaming = streaming;
    }

    /**
     * Calls a model with the given workflow state.
     *
     * @param state The current state containing input and intermediate steps.
     * @return A map containing the outcome of the agent call, either an action or a finish.
     */
    @Override
    public Map<String, Object> apply(State state, RunnableConfig config) throws Exception {

        var messages = state.messages();

        if (messages.isEmpty()) {
            throw new IllegalArgumentException("no input provided!");
        }

        if (streaming) {
            var flux = chatService.streamingExecute(messages);

            var generator = StreamingChatGenerator.builder()
                    .startingNode("agent")
                    .startingState(state)
                    .mapResult(response -> {
                        var output = response.getResult().getOutput();
                        // Prevent NullPointerException when output is null
                        return Map.of("messages", output != null ? output : new org.springframework.ai.chat.messages.AssistantMessage(""));
                    })
                    .build(flux);

            return Map.of("messages", generator);
        } else {
            var response = chatService.execute(messages);

            var output = response.getResult().getOutput();
            // Prevent NullPointerException when output is null
            if (output == null) {
                output = new org.springframework.ai.chat.messages.AssistantMessage("");
            }

            return Map.of("messages", output);
        }

    }

}
