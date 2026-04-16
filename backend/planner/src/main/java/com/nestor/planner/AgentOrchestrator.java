package com.nestor.planner;

import com.nestor.common.bedrock.BedrockConverse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uses Bedrock Converse API with tool-calling to orchestrate child agents.
 * The model decides which agents to invoke based on the portfolio summary.
 * Supports multi-turn conversation (tool calls → results → next decision).
 */
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final int MAX_TURNS = 10;

    private final BedrockRuntimeClient bedrockClient;
    private final String modelId;
    private final LambdaAgentInvoker invoker;

    public AgentOrchestrator(String modelId, String region, LambdaAgentInvoker invoker) {
        this.modelId = modelId;
        this.bedrockClient = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .build();
        this.invoker = invoker;
    }

    /**
     * Run the orchestration loop. The model decides which tools to call,
     * we execute them, and return results until the model finishes.
     *
     * @param jobId         the job UUID
     * @param taskPrompt    the user prompt describing the portfolio
     * @param portfolioData full portfolio data for sub-agents
     * @param userData      user profile data for sub-agents
     * @return summary of what happened
     */
    public String orchestrate(String jobId, String taskPrompt,
                              Map<String, Object> portfolioData, Map<String, Object> userData) {
        List<Tool> tools = buildToolDefinitions();
        ToolConfiguration toolConfig = ToolConfiguration.builder().tools(tools).build();

        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(taskPrompt))
                .build());

        for (int turn = 0; turn < MAX_TURNS; turn++) {
            log.info("Planner orchestration turn {}/{}", turn + 1, MAX_TURNS);

            ConverseResponse response = bedrockClient.converse(ConverseRequest.builder()
                    .modelId(modelId)
                    .system(SystemContentBlock.builder().text(PlannerTemplates.ORCHESTRATOR_SYSTEM).build())
                    .messages(messages)
                    .toolConfig(toolConfig)
                    .build());

            Message assistantMessage = response.output().message();
            messages.add(assistantMessage);

            // Check if the model wants to use tools
            List<ContentBlock> toolUseBlocks = new ArrayList<>();
            String textResponse = null;

            for (ContentBlock block : assistantMessage.content()) {
                if (block.toolUse() != null) {
                    toolUseBlocks.add(block);
                } else if (block.text() != null) {
                    textResponse = block.text();
                }
            }

            if (toolUseBlocks.isEmpty()) {
                // Model is done — return the text response
                log.info("Planner: Orchestration complete. Response: {}", textResponse);
                return textResponse != null ? textResponse : "Done";
            }

            // Execute all tool calls in parallel
            List<ContentBlock> toolResultBlocks = executeToolCalls(jobId, toolUseBlocks, portfolioData, userData);

            // Send tool results back as a user message
            messages.add(Message.builder()
                    .role(ConversationRole.USER)
                    .content(toolResultBlocks)
                    .build());
        }

        log.warn("Planner: Reached max turns ({})", MAX_TURNS);
        return "Orchestration completed (max turns reached)";
    }

    /**
     * Execute tool calls in parallel and collect results.
     */
    private List<ContentBlock> executeToolCalls(String jobId, List<ContentBlock> toolUseBlocks,
                                                Map<String, Object> portfolioData, Map<String, Object> userData) {
        Map<String, CompletableFuture<String>> futures = new ConcurrentHashMap<>();

        for (ContentBlock block : toolUseBlocks) {
            ToolUseBlock toolUse = block.toolUse();
            String toolName = toolUse.name();
            String toolUseId = toolUse.toolUseId();

            log.info("Planner: Executing tool: {}", toolName);

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return executeToolCall(jobId, toolName, portfolioData, userData);
                } catch (Exception e) {
                    log.error("Planner: Tool {} failed: {}", toolName, e.getMessage(), e);
                    return "Error: " + e.getMessage();
                }
            });

            futures.put(toolUseId, future);
        }

        // Wait for all futures and build result blocks
        List<ContentBlock> resultBlocks = new ArrayList<>();
        for (ContentBlock block : toolUseBlocks) {
            String toolUseId = block.toolUse().toolUseId();
            String result;
            try {
                result = futures.get(toolUseId).join();
            } catch (Exception e) {
                result = "Error: " + e.getMessage();
            }

            resultBlocks.add(ContentBlock.fromToolResult(ToolResultBlock.builder()
                    .toolUseId(toolUseId)
                    .content(ToolResultContentBlock.builder().text(result).build())
                    .build()));
        }

        return resultBlocks;
    }

    /**
     * Execute a single tool call by invoking the corresponding child Lambda.
     */
    private String executeToolCall(String jobId, String toolName,
                                   Map<String, Object> portfolioData, Map<String, Object> userData) {
        Map<String, Object> result;
        switch (toolName) {
            case "invoke_reporter":
                result = invoker.invokeReporter(jobId, portfolioData, userData);
                if (result.containsKey("error")) {
                    return "Reporter agent failed: " + result.get("error");
                }
                return "Reporter agent completed successfully. Portfolio analysis narrative has been generated and saved.";

            case "invoke_charter":
                result = invoker.invokeCharter(jobId, portfolioData, userData);
                if (result.containsKey("error")) {
                    return "Charter agent failed: " + result.get("error");
                }
                return "Charter agent completed successfully. Portfolio visualizations have been created and saved.";

            case "invoke_retirement":
                result = invoker.invokeRetirement(jobId, portfolioData, userData);
                if (result.containsKey("error")) {
                    return "Retirement agent failed: " + result.get("error");
                }
                return "Retirement agent completed successfully. Retirement projections have been calculated and saved.";

            default:
                return "Unknown tool: " + toolName;
        }
    }

    /**
     * Build tool definitions for Bedrock Converse API.
     */
    private List<Tool> buildToolDefinitions() {
        return List.of(
                buildTool("invoke_reporter",
                        "Invoke the Report Writer agent to generate portfolio analysis narrative."),
                buildTool("invoke_charter",
                        "Invoke the Chart Maker agent to create portfolio visualizations."),
                buildTool("invoke_retirement",
                        "Invoke the Retirement Specialist agent for retirement projections.")
        );
    }

    private Tool buildTool(String name, String description) {
        // Minimal schema — no input parameters needed
        Document schema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(Map.of(
                        "reason", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("Brief reason for calling this agent")
                        ))
                )),
                "required", Document.fromList(List.of())
        ));

        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name(name)
                        .description(description)
                        .inputSchema(ToolInputSchema.builder().json(schema).build())
                        .build())
                .build();
    }
}
