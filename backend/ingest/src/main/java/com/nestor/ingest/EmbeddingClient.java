package com.nestor.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Client that generates text embeddings via a SageMaker HuggingFace endpoint.
 * <p>
 * Sends {@code {"inputs": text}} and unwraps the nested array response
 * to a flat {@code List<Float>} of 384 dimensions.
 */
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SageMakerRuntimeClient sagemakerClient;
    private final String endpointName;

    public EmbeddingClient(String endpointName, String region) {
        this.endpointName = endpointName;
        this.sagemakerClient = SageMakerRuntimeClient.builder()
                .region(Region.of(region))
                .build();
    }

    /**
     * Generate embedding vector for the given text.
     *
     * @param text input text to embed
     * @return flat list of 384 floats
     * @throws RuntimeException if SageMaker call fails or response cannot be parsed
     */
    public List<Float> getEmbedding(String text) {
        try {
            String body = MAPPER.writeValueAsString(Map.of("inputs", text));

            InvokeEndpointResponse response = sagemakerClient.invokeEndpoint(
                    InvokeEndpointRequest.builder()
                            .endpointName(endpointName)
                            .contentType("application/json")
                            .body(SdkBytes.fromUtf8String(body))
                            .build());

            String responseBody = response.body().asUtf8String();
            Object parsed = MAPPER.readValue(responseBody, Object.class);

            // HuggingFace returns nested arrays: [[[0.1, 0.2, ...]]] or [[0.1, ...]] or [0.1, ...]
            List<Float> embedding = unwrapEmbedding(parsed);
            log.info("Generated embedding with {} dimensions", embedding.size());
            return embedding;

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate embedding via SageMaker: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Float> unwrapEmbedding(Object parsed) {
        if (parsed instanceof List<?> outerList && !outerList.isEmpty()) {
            Object first = outerList.get(0);

            // [[[embedding]]] — triple nested
            if (first instanceof List<?> innerList && !innerList.isEmpty()) {
                Object deep = innerList.get(0);
                if (deep instanceof List<?> deepList) {
                    return deepList.stream()
                            .map(v -> ((Number) v).floatValue())
                            .collect(Collectors.toList());
                }
                // [[embedding]] — double nested
                return innerList.stream()
                        .map(v -> ((Number) v).floatValue())
                        .collect(Collectors.toList());
            }

            // [embedding] — flat
            if (first instanceof Number) {
                return outerList.stream()
                        .map(v -> ((Number) v).floatValue())
                        .collect(Collectors.toList());
            }
        }
        throw new RuntimeException("Unexpected SageMaker response format");
    }
}
