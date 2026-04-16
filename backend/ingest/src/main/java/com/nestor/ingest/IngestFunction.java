package com.nestor.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * Spring Cloud Function bean ({@code ingestFunction}).
 * <p>
 * Receives a Lambda event (API Gateway proxy or direct invocation) with text to ingest,
 * generates an embedding via SageMaker, and stores the vector in S3 Vectors.
 * <p>
 * Expected input:
 * <pre>{@code
 * {
 *   "text": "Text to ingest",
 *   "metadata": {
 *     "source": "optional source",
 *     "category": "optional category"
 *   }
 * }
 * }</pre>
 * <p>
 * Or via API Gateway proxy:
 * <pre>{@code
 * {
 *   "body": "{\"text\": \"...\", \"metadata\": {...}}"
 * }
 * }</pre>
 */
public class IngestFunction implements Function<Map<String, Object>, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(IngestFunction.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EmbeddingClient embeddingClient;
    private final S3VectorsClient s3VectorsClient;

    public IngestFunction(EmbeddingClient embeddingClient, S3VectorsClient s3VectorsClient) {
        this.embeddingClient = embeddingClient;
        this.s3VectorsClient = s3VectorsClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(Map<String, Object> event) {
        try {
            // Parse the request body — handle API Gateway proxy event or direct invocation
            Map<String, Object> body = parseBody(event);

            String text = (String) body.get("text");
            if (text == null || text.isBlank()) {
                return errorResponse(400, "Missing required field: text");
            }

            Map<String, Object> metadata = (Map<String, Object>) body.getOrDefault("metadata", Map.of());

            // Generate embedding via SageMaker
            log.info("Getting embedding for text: {}...", text.substring(0, Math.min(100, text.length())));
            List<Float> embedding = embeddingClient.getEmbedding(text);

            // Generate unique document ID
            String documentId = UUID.randomUUID().toString();

            // Build vector metadata
            Map<String, String> vectorMetadata = new LinkedHashMap<>();
            vectorMetadata.put("text", text);
            vectorMetadata.put("timestamp", Instant.now().toString());
            if (metadata != null) {
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    vectorMetadata.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }

            // Store in S3 Vectors
            s3VectorsClient.putVector(documentId, embedding, vectorMetadata);

            return Map.of(
                    "statusCode", 200,
                    "body", Map.of(
                            "message", "Document indexed successfully",
                            "document_id", documentId
                    )
            );

        } catch (Exception e) {
            log.error("Ingest function error: {}", e.getMessage(), e);
            return errorResponse(500, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(Map<String, Object> event) {
        Object rawBody = event.get("body");
        if (rawBody instanceof String bodyStr) {
            try {
                return MAPPER.readValue(bodyStr, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to parse body as JSON string, treating event as body");
            }
        } else if (rawBody instanceof Map) {
            return (Map<String, Object>) rawBody;
        }
        // Direct invocation: event IS the body
        return event;
    }

    private static Map<String, Object> errorResponse(int statusCode, String message) {
        return Map.of("statusCode", statusCode, "body", Map.of("error", message));
    }
}
