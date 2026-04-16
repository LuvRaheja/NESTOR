package com.nestor.charter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nestor.common.bedrock.BedrockConverse;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

import java.time.Duration;
import java.util.*;

/**
 * Calls Bedrock in plain-text mode and extracts Recharts-compatible JSON
 * from the assistant's response.
 */
public class ChartGenerator {

    private static final Logger log = LoggerFactory.getLogger(ChartGenerator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BedrockConverse bedrock;
    private final Retry retry;

    public ChartGenerator(BedrockConverse bedrock) {
        this.bedrock = bedrock;
        this.retry = Retry.of("charter-bedrock", RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofSeconds(4))
                .retryExceptions(ThrottlingException.class)
                .build());
    }

    /**
     * Generate chart data from pre-computed portfolio analysis.
     *
     * @param portfolioAnalysis the human-readable analysis text
     * @return map of chart-key → chart object, or empty map on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generate(String portfolioAnalysis) {
        String task = CharterTemplates.createCharterTask(portfolioAnalysis);

        String output = Retry.decorateSupplier(retry, () ->
                bedrock.converse(CharterTemplates.CHARTER_INSTRUCTIONS, task)
        ).get();

        log.info("Charter: Agent completed, output length: {}", output != null ? output.length() : 0);

        if (output == null || output.isBlank()) {
            log.warn("Charter: Agent returned empty output");
            return Collections.emptyMap();
        }

        // Extract outermost JSON object
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.error("Charter: No JSON structure found in output");
            return Collections.emptyMap();
        }

        String jsonStr = output.substring(start, end + 1);
        log.info("Charter: Extracted JSON substring, length: {}", jsonStr.length());

        try {
            Map<String, Object> parsed = MAPPER.readValue(jsonStr, Map.class);
            List<Map<String, Object>> charts = (List<Map<String, Object>>) parsed.get("charts");

            if (charts == null || charts.isEmpty()) {
                log.warn("Charter: No charts found in parsed JSON");
                return Collections.emptyMap();
            }

            log.info("Charter: Successfully parsed JSON, found {} charts", charts.size());

            // Re-key charts by their "key" field
            Map<String, Object> chartsData = new LinkedHashMap<>();
            for (Map<String, Object> chart : charts) {
                String key = (String) chart.getOrDefault("key", "chart_" + (chartsData.size() + 1));
                Map<String, Object> chartCopy = new LinkedHashMap<>(chart);
                chartCopy.remove("key");
                chartsData.put(key, chartCopy);
            }

            log.info("Charter: Created charts_data with keys: {}", chartsData.keySet());
            return chartsData;

        } catch (JsonProcessingException e) {
            log.error("Charter: Failed to parse JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
