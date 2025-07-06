package org.bsc.langgraph4j.langchain4j.util;

import dev.langchain4j.data.message.*;

import java.util.Optional;

import static java.util.Optional.ofNullable;

public class ChatMessageUtil {

    public static boolean isToolExecutionResultMessage( ChatMessage message  ) {
        return ofNullable(message)
                .map( m -> m.type().equals(ChatMessageType.TOOL_EXECUTION_RESULT))
                .orElse(false) ;
    }

    public static Optional<ToolExecutionResultMessage> asToolExecutionResultMessage(ChatMessage message  ) {
        if( message  instanceof ToolExecutionResultMessage result ) {
            return Optional.of(result);
        }
        return Optional.empty();
    }

    public static boolean isAiMessage( ChatMessage message  ) {
        return ofNullable(message)
                .map( m -> m.type().equals(ChatMessageType.AI))
                .orElse(false) ;
    }

    public static Optional<AiMessage> asAiMessage(ChatMessage message  ) {
        if( message  instanceof AiMessage result ) {
            return Optional.of(result);
        }
        return Optional.empty();
    }

    public static boolean isSystemMessage( ChatMessage message  ) {
        return ofNullable(message)
                .map( m -> m.type().equals(ChatMessageType.SYSTEM))
                .orElse(false) ;
    }

    public static Optional<SystemMessage> asSystemMessage( ChatMessage message  ) {
        if( message  instanceof SystemMessage result ) {
            return Optional.of(result);
        }
        return Optional.empty();
    }

    public static boolean isUserMessage( ChatMessage message ) {
        return ofNullable(message)
                .map( m -> m.type().equals(ChatMessageType.USER))
                .orElse(false) ;
    }

    public static Optional<UserMessage> asUserMessage( ChatMessage message  ) {
        if( message  instanceof UserMessage result ) {
            return Optional.of(result);
        }
        return Optional.empty();
    }

}
