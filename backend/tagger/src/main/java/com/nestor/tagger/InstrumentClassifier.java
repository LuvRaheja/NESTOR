package com.nestor.tagger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nestor.common.bedrock.BedrockConverse;
import com.nestor.tagger.model.InstrumentClassification;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Classifies financial instruments via Bedrock Converse API with structured output.
 * <p>
 * Uses the tool-use trick: the model is forced to "call" a tool whose input schema
 * matches {@link InstrumentClassification}, so the response is guaranteed-structured JSON.
 * <p>
 * Includes Resilience4j retry for Bedrock rate-limit (throttle) errors.
 */
public class InstrumentClassifier {

    private static final Logger log = LoggerFactory.getLogger(InstrumentClassifier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TOOL_NAME = "classify_instrument";
    private static final String TOOL_DESC = "Classify and return structured data for a financial instrument";

    private final BedrockConverse bedrock;
    private final Retry retry;

    public InstrumentClassifier(BedrockConverse bedrock) {
        this.bedrock = bedrock;

        // Resilience4j retry: up to 5 attempts with exponential backoff for throttling
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofSeconds(4))
                .retryExceptions(ThrottlingException.class)
                .build();
        this.retry = Retry.of("tagger-bedrock", config);
    }

    /** Classify a single instrument. */
    public InstrumentClassification classify(String symbol, String name, String instrumentType) {
        String userPrompt = String.format(TaggerTemplates.CLASSIFICATION_PROMPT,
                symbol, name, instrumentType);

        Map<String, Object> schema = buildClassificationSchema();

        // Wrap the Bedrock call with retry
        @SuppressWarnings("unchecked")
        Map<String, Object> rawResult = (Map<String, Object>) Retry.decorateSupplier(retry, () ->
                bedrock.converseWithStructuredOutput(
                        TaggerTemplates.TAGGER_INSTRUCTIONS,
                        userPrompt,
                        TOOL_NAME,
                        TOOL_DESC,
                        schema)
        ).get();

        InstrumentClassification classification = MAPPER.convertValue(rawResult, InstrumentClassification.class);
        classification.validate();
        classification.removeZeroAllocations();

        log.info("Classified {} – type={}, price={}", symbol, classification.getInstrumentType(), classification.getCurrentPrice());
        return classification;
    }

    /**
     * Classify multiple instruments sequentially with a small delay between calls
     * to reduce rate-limit pressure.
     */
    public List<InstrumentClassification> classifyAll(List<Map<String, String>> instruments) {
        List<InstrumentClassification> results = new ArrayList<>();

        for (int i = 0; i < instruments.size(); i++) {
            Map<String, String> inst = instruments.get(i);
            String symbol = inst.get("symbol");
            String name = inst.getOrDefault("name", "");
            String type = inst.getOrDefault("instrument_type", "etf");

            // Small delay between requests (skip for the first one)
            if (i > 0) {
                try { TimeUnit.MILLISECONDS.sleep(500); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            try {
                InstrumentClassification c = classify(symbol, name, type);
                results.add(c);
            } catch (Exception e) {
                log.error("Failed to classify {}: {}", symbol, e.getMessage(), e);
                // Continue with remaining instruments (matches Python behaviour)
            }
        }
        return results;
    }

    // ── JSON Schema for the tool-use structured output ──────────────────────────

    private static Map<String, Object> buildClassificationSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("symbol", Map.of("type", "string", "description", "Ticker symbol of the instrument"));
        properties.put("name", Map.of("type", "string", "description", "Name of the instrument"));
        properties.put("instrument_type", Map.of(
                "type", "string",
                "description", "Type: etf, stock, mutual_fund, bond_fund",
                "enum", List.of("etf", "stock", "mutual_fund", "bond_fund", "bond", "commodity", "reit")
        ));
        properties.put("current_price", Map.of("type", "number", "description", "Current price per share in instrument's native currency (USD or INR)"));
        properties.put("allocation_asset_class", buildAllocationObjectSchema(
                "Asset class breakdown – percentages must sum to 100",
                List.of("equity", "fixed_income", "real_estate", "commodities", "cash", "alternatives")
        ));
        properties.put("allocation_regions", buildAllocationObjectSchema(
                "Regional breakdown – percentages must sum to 100",
                List.of("north_america", "europe", "asia", "latin_america", "africa", "middle_east", "oceania", "global", "international")
        ));
        properties.put("allocation_sectors", buildAllocationObjectSchema(
                "Sector breakdown – percentages must sum to 100",
                List.of("technology", "healthcare", "financials", "consumer_discretionary", "consumer_staples",
                        "industrials", "materials", "energy", "utilities", "real_estate", "communication",
                        "treasury", "corporate", "mortgage", "government_related", "commodities",
                        "diversified", "other")
        ));

        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("symbol", "name", "instrument_type", "current_price",
                        "allocation_asset_class", "allocation_regions", "allocation_sectors")
        );
    }

    private static Map<String, Object> buildAllocationObjectSchema(String description, List<String> keys) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (String key : keys) {
            props.put(key, Map.of("type", "number", "description", key + " percentage (0-100)"));
        }
        return Map.of(
                "type", "object",
                "description", description,
                "properties", props,
                "required", keys
        );
    }
}
