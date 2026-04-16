package com.nestor.common.bedrock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkNumber;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Bedrock Converse API wrapper.
 * <p>
 * Supports <b>structured output</b> via the tool-use trick: we define a
 * {@link ToolSpecification} whose input schema matches the desired output POJO,
 * then force the model to "call" the tool. The tool-use arguments are the
 * structured JSON we want.
 */
public class BedrockConverse {

    private static final Logger log = LoggerFactory.getLogger(BedrockConverse.class);

    private final BedrockRuntimeClient client;
    private final String modelId;

    public BedrockConverse(String modelId, String region) {
        this.modelId = modelId;
        this.client = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .build();
    }

    /**
     * Call Bedrock Converse API with a tool specification to obtain structured output.
     *
     * @param systemPrompt    system-level instructions
     * @param userPrompt      user message (the classification request)
     * @param toolName        name of the pseudo-tool
     * @param toolDescription description of what the tool "does"
     * @param toolSchema      JSON-Schema as a nested {@code Map<String,Object>} tree
     * @return the structured result as a {@code Map<String,Object>}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> converseWithStructuredOutput(
            String systemPrompt,
            String userPrompt,
            String toolName,
            String toolDescription,
            Map<String, Object> toolSchema) {

        Document schemaDoc = toDocument(toolSchema);

        ToolSpecification toolSpec = ToolSpecification.builder()
                .name(toolName)
                .description(toolDescription)
                .inputSchema(ToolInputSchema.builder().json(schemaDoc).build())
                .build();

        ToolConfiguration toolConfig = ToolConfiguration.builder()
                .tools(Tool.builder().toolSpec(toolSpec).build())
                .toolChoice(ToolChoice.builder()
                        .tool(SpecificToolChoice.builder().name(toolName).build())
                        .build())
                .build();

        Message userMessage = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(userPrompt))
                .build();

        ConverseRequest.Builder requestBuilder = ConverseRequest.builder()
                .modelId(modelId)
                .messages(userMessage)
                .toolConfig(toolConfig);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            requestBuilder.system(SystemContentBlock.builder().text(systemPrompt).build());
        }

        log.info("Calling Bedrock Converse API – model={}, tool={}", modelId, toolName);
        ConverseResponse response = client.converse(requestBuilder.build());

        // Extract the tool-use block from the response
        for (ContentBlock block : response.output().message().content()) {
            if (block.toolUse() != null) {
                Document toolInput = block.toolUse().input();
                return (Map<String, Object>) fromDocument(toolInput);
            }
        }

        throw new RuntimeException("Bedrock did not return a tool-use response for tool: " + toolName);
    }

    /**
     * Call Bedrock Converse API in plain text mode (no tools, no structured output).
     *
     * @param systemPrompt system-level instructions
     * @param userPrompt   user message
     * @return the assistant's text response
     */
    public String converse(String systemPrompt, String userPrompt) {
        Message userMessage = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(userPrompt))
                .build();

        ConverseRequest.Builder requestBuilder = ConverseRequest.builder()
                .modelId(modelId)
                .messages(userMessage);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            requestBuilder.system(SystemContentBlock.builder().text(systemPrompt).build());
        }

        log.info("Calling Bedrock Converse API (plain) – model={}", modelId);
        ConverseResponse response = client.converse(requestBuilder.build());

        for (ContentBlock block : response.output().message().content()) {
            if (block.text() != null) {
                return block.text();
            }
        }

        throw new RuntimeException("Bedrock did not return a text response");
    }

    // ── Document ↔ Java conversion helpers ──────────────────────────────────────

    /** Recursively convert a Java object tree (Map / List / primitives) to an AWS Document. */
    public static Document toDocument(Object obj) {
        if (obj == null) {
            return Document.fromNull();
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, Document> docMap = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                docMap.put(String.valueOf(entry.getKey()), toDocument(entry.getValue()));
            }
            return Document.fromMap(docMap);
        }
        if (obj instanceof List<?> list) {
            List<Document> docs = list.stream()
                    .map(BedrockConverse::toDocument)
                    .collect(Collectors.toList());
            return Document.fromList(docs);
        }
        if (obj instanceof String s)  return Document.fromString(s);
        if (obj instanceof Number n)  return Document.fromNumber(SdkNumber.fromDouble(n.doubleValue()));
        if (obj instanceof Boolean b) return Document.fromBoolean(b);
        return Document.fromString(obj.toString());
    }

    /** Recursively convert an AWS Document back to plain Java objects. */
    public static Object fromDocument(Document doc) {
        if (doc.isNull())    return null;
        if (doc.isMap()) {
            Map<String, Object> result = new LinkedHashMap<>();
            doc.asMap().forEach((k, v) -> result.put(k, fromDocument(v)));
            return result;
        }
        if (doc.isList()) {
            return doc.asList().stream()
                    .map(BedrockConverse::fromDocument)
                    .collect(Collectors.toList());
        }
        if (doc.isString())  return doc.asString();
        if (doc.isNumber())  return doc.asNumber().doubleValue();
        if (doc.isBoolean()) return doc.asBoolean();
        return null;
    }
}
