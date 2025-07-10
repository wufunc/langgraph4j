package org.bsc.langgraph4j.spring.ai.util;

import org.springframework.ai.chat.messages.*;

import java.util.Optional;

import static java.util.Optional.ofNullable;

public class MessageUtil {

    public static boolean isToolResponseMessage(Message message  ) {
        return ofNullable(message)
                .map( m -> m.getMessageType().equals(MessageType.TOOL))
                .orElse(false) ;
    }

    public static Optional<ToolResponseMessage> asToolResponseMessage(Message message  ) {
        if( message  instanceof ToolResponseMessage result ) {
            return Optional.of(result);
        }
        return Optional.empty();
    }

    public static boolean isAssistantMessage( Message message  ) {
        return ofNullable(message)
                .map( m -> m.getMessageType().equals(MessageType.ASSISTANT))
                .orElse(false) ;
    }

    public static Optional<AssistantMessage> asAssistantMessage( Message message  ) {
        if( message  instanceof AssistantMessage result ) {
            return Optional.of(result);
        }
        return Optional.empty();
    }

    public static boolean isSystemMessage( Message message  ) {
        return ofNullable(message)
                .map( m -> m.getMessageType().equals(MessageType.SYSTEM))
                .orElse(false) ;
    }

    public static Optional<SystemMessage> asSystemMessage(Message message  ) {
        if( message  instanceof SystemMessage result ) {
            return Optional.of(result);
        }
        return Optional.empty();
    }

    public static boolean isUserMessage( Message message  ) {
        return ofNullable(message)
                .map( m -> m.getMessageType().equals(MessageType.USER))
                .orElse(false) ;
    }

    public static Optional<UserMessage> asUserMessage(Message message  ) {
        if( message  instanceof UserMessage result ) {
            return Optional.of(result);
        }
        return Optional.empty();
    }

}
