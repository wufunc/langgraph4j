package org.bsc.langgraph4j.spring.ai.agentexecutor;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Objects;

import static java.lang.String.format;

/**
 * Demonstrates the use of Spring Boot CLI to execute a task using an agent executor.
 */
@Controller
public class DemoConsoleController implements CommandLineRunner {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DemoConsoleController.class);

    private final ChatModel chatModel;

    public DemoConsoleController( ChatModel chatModel ) {

        this.chatModel = chatModel;
    }

    /**
     * Executes the command-line interface to demonstrate a Spring Boot application.
     * This method logs a welcome message, constructs a graph using an agent executor,
     * compiles it into a workflow, invokes the workflow with a specific input,
     * and then logs the final result.
     *
     * @param args Command line arguments (Unused in this context)
     * @throws Exception If any error occurs during execution
     */
    @Override
    public void run(String... args) throws Exception {

        log.info("Welcome to the Spring Boot CLI application!");

        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                                .checkpointSaver(saver)
                                .build();

        var agent = AgentExecutorEx.builder()
                        //.streamingChatModel(chatModel)
                        .chatModel(chatModel)
                        .toolsFromObject( new TestTool()) // Support without providing tools
                        .approvalOn( "execTest", AgentExecutorEx.ApprovalNodeAction.builder()
                                                        .resumePropertyName("resume")
                                                        .interruptionMetadataProvider( ( nodeId, state ) ->
                                                            InterruptionMetadata.builder( nodeId, state )
                                                                    .addMetadata( "label", "confirm execution of test?")
                                                                    .build()
                                                        )
                                                        .build() )
                        .build()
                        .compile(compileConfig);

        log.info( "{}", agent.getGraph( GraphRepresentation.Type.MERMAID, "ReAct Agent", false));

        Map<String,Object> input = Map.of("messages", new UserMessage("perform test") );
        var runnableConfig = RunnableConfig.builder().build();

        var console = System.console();

        while( true ) {
            var result = agent.stream(input, runnableConfig );

            var output = result.stream()
                    .peek(s -> {
                        if (s instanceof StreamingOutput<?> out) {
                            System.out.printf("%s: (%s)\n", out.node(), out.chunk());
                        } else {
                            System.out.println(s.node());
                        }
                    })
                    .reduce((a, b) -> b)
                    .orElseThrow();

            if (output.isEND()) {
                console.format( "result: %s\n",
                        output.state().lastMessage()
                        .map(AssistantMessage.class::cast)
                        .map(AssistantMessage::getText)
                        .orElseThrow());
                break;

            } else {

                var returnValue = AsyncGenerator.resultValue(result);

                if( returnValue.isPresent() ) {

                    log.info("interrupted: {}", returnValue.orElse("NO RESULT FOUND!"));

                    if (returnValue.get() instanceof InterruptionMetadata<?> interruption) {

                        var answer = console.readLine(format("%s : (N\\y) \t\n", interruption.metadata("label").orElse("Approve action ?")));

                        if (Objects.equals(answer, "Y") || Objects.equals(answer, "y")) {
                            runnableConfig = agent.updateState(runnableConfig, Map.of("resume", AgentExecutorEx.ApprovalState.APPROVED.name()));
                        } else {
                            runnableConfig = agent.updateState(runnableConfig, Map.of("resume", AgentExecutorEx.ApprovalState.REJECTED.name()));
                        }
                    }
                    input = null;
                }

            }

        }
    }
}