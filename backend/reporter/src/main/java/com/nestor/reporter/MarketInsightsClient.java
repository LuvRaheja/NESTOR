package com.nestor.reporter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;
import software.amazon.awssdk.services.sts.StsClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

/**
 * Client for fetching market insights from S3 Vectors via SageMaker embeddings.
 * <p>
 * Flow:
 * 1. Get account ID via STS
 * 2. Generate embedding for query via SageMaker endpoint
 * 3. Query S3 Vectors for semantically similar documents
 * 4. Format results as text insights
 */
public class MarketInsightsClient {

    private static final Logger log = LoggerFactory.getLogger(MarketInsightsClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String sagemakerEndpoint;
    private final String region;
    private final SageMakerRuntimeClient sagemakerClient;

    public MarketInsightsClient(String sagemakerEndpoint, String region) {
        this.sagemakerEndpoint = sagemakerEndpoint;
        this.region = region;
        this.sagemakerClient = SageMakerRuntimeClient.builder()
                .region(Region.of(region))
                .build();
    }

    /**
     * Retrieve market insights for the given symbols.
     *
     * @param symbols list of stock/ETF symbols
     * @return formatted market insights text
     */
    public String getInsights(List<String> symbols) {
        try {
            // Build query
            List<String> topSymbols = symbols.stream().limit(5).collect(Collectors.toList());
            String query = topSymbols.isEmpty()
                    ? "market outlook"
                    : "market analysis " + String.join(" ", topSymbols);

            // Get embedding from SageMaker
            List<Double> embedding = getEmbedding(query);
            if (embedding.isEmpty()) {
                return "Market insights unavailable - embedding generation failed.";
            }

            // Get account ID for bucket name
            String accountId = getAccountId();
            String bucket = "alex-vectors-" + accountId;

            // Query S3 Vectors
            List<Map<String, Object>> results = queryS3Vectors(bucket, embedding);

            // Format insights
            if (results.isEmpty()) {
                return "Market insights unavailable - no matching documents found.";
            }

            StringBuilder sb = new StringBuilder("Market Insights:\n");
            for (Map<String, Object> result : results) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) result.getOrDefault("metadata", Map.of());
                String text = (String) metadata.getOrDefault("text", "");
                if (text.length() > 200) text = text.substring(0, 200);
                if (!text.isEmpty()) {
                    String company = (String) metadata.getOrDefault("company_name", "");
                    String prefix = company.isEmpty() ? "- " : company + ": ";
                    sb.append(prefix).append(text).append("...\n");
                }
            }
            return sb.toString();

        } catch (Exception e) {
            log.warn("Could not retrieve market insights: {}", e.getMessage());
            return "Market insights unavailable - proceeding with standard analysis.";
        }
    }

    private List<Double> getEmbedding(String query) {
        try {
            String body = MAPPER.writeValueAsString(Map.of("inputs", query));

            InvokeEndpointResponse response = sagemakerClient.invokeEndpoint(
                    InvokeEndpointRequest.builder()
                            .endpointName(sagemakerEndpoint)
                            .contentType("application/json")
                            .body(SdkBytes.fromUtf8String(body))
                            .build());

            String responseBody = response.body().asUtf8String();
            // Parse: could be [[0.1, 0.2, ...]] or [0.1, 0.2, ...]
            Object parsed = MAPPER.readValue(responseBody, Object.class);

            if (parsed instanceof List<?> outerList && !outerList.isEmpty()) {
                Object first = outerList.get(0);
                if (first instanceof List<?> innerList) {
                    // [[embedding]]
                    if (!innerList.isEmpty() && innerList.get(0) instanceof List<?> deepList) {
                        return deepList.stream()
                                .map(v -> ((Number) v).doubleValue())
                                .collect(Collectors.toList());
                    }
                    return innerList.stream()
                            .map(v -> ((Number) v).doubleValue())
                            .collect(Collectors.toList());
                }
                if (first instanceof Number) {
                    return outerList.stream()
                            .map(v -> ((Number) v).doubleValue())
                            .collect(Collectors.toList());
                }
            }
            log.warn("Unexpected embedding response format");
            return List.of();
        } catch (Exception e) {
            log.warn("SageMaker embedding failed: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryS3Vectors(String bucket, List<Double> embedding) {
        try {
            // S3 Vectors uses a JSON API via the s3vectors endpoint
            // We use the AWS SDK's generic HTTP signing or direct REST call
            // Since there's no dedicated S3 Vectors client in SDK v2 yet,
            // we use a signed HTTP request via the s3vectors endpoint

            List<Float> floatEmbedding = embedding.stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("vectorBucketName", bucket);
            requestBody.put("indexName", "financial-research");
            requestBody.put("queryVector", Map.of("float32", floatEmbedding));
            requestBody.put("topK", 3);
            requestBody.put("returnMetadata", true);

            // Use the boto3-equivalent approach: call the s3vectors endpoint
            // For now, we use AWS CLI-style approach via ProcessBuilder
            String payload = MAPPER.writeValueAsString(requestBody);

            ProcessBuilder pb = new ProcessBuilder(
                    "aws", "s3vectors", "query-vectors",
                    "--vector-bucket-name", bucket,
                    "--index-name", "financial-research",
                    "--query-vector", MAPPER.writeValueAsString(Map.of("float32", floatEmbedding)),
                    "--top-k", "3",
                    "--return-metadata",
                    "--region", region,
                    "--output", "json"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.warn("S3 Vectors query failed (exit {}): {}", exitCode, output.substring(0, Math.min(500, output.length())));
                return List.of();
            }

            Map<String, Object> result = MAPPER.readValue(output, new TypeReference<>() {});
            Object vectors = result.get("vectors");
            if (vectors instanceof List<?> list) {
                return (List<Map<String, Object>>) vectors;
            }
            return List.of();

        } catch (Exception e) {
            log.warn("S3 Vectors query failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String getAccountId() {
        try (StsClient sts = StsClient.builder().region(Region.of(region)).build()) {
            return sts.getCallerIdentity().account();
        }
    }
}
