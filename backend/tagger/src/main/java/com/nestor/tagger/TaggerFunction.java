package com.nestor.tagger;

import com.nestor.common.db.InstrumentRepository;
import com.nestor.tagger.model.InstrumentClassification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/**
 * Spring Cloud Function bean ({@code taggerFunction}).
 * <p>
 * Receives a Lambda event with instruments to classify, runs the Bedrock-backed
 * classifier, upserts results to Aurora, and returns a summary.
 * <p>
 * Expected input:
 * <pre>{@code
 * {
 *   "instruments": [
 *     { "symbol": "VTI", "name": "Vanguard Total Stock Market ETF" },
 *     ...
 *   ]
 * }
 * }</pre>
 */
public class TaggerFunction implements Function<Map<String, Object>, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(TaggerFunction.class);

    private final InstrumentClassifier classifier;
    private final InstrumentRepository instrumentRepo;

    public TaggerFunction(InstrumentClassifier classifier, InstrumentRepository instrumentRepo) {
        this.classifier = classifier;
        this.instrumentRepo = instrumentRepo;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(Map<String, Object> event) {
        try {
            List<Map<String, String>> instruments = (List<Map<String, String>>) event.get("instruments");
            if (instruments == null || instruments.isEmpty()) {
                return errorResponse(400, "No instruments provided");
            }

            log.info("Classifying {} instruments", instruments.size());
            List<InstrumentClassification> classifications = classifier.classifyAll(instruments);

            // Upsert each classification to the database
            List<String> updated = new ArrayList<>();
            List<Map<String, Object>> errors = new ArrayList<>();

            for (InstrumentClassification c : classifications) {
                try {
                    upsertInstrument(c);
                    updated.add(c.getSymbol());
                } catch (Exception e) {
                    log.error("Error upserting {}: {}", c.getSymbol(), e.getMessage(), e);
                    errors.add(Map.of("symbol", c.getSymbol(), "error", e.getMessage()));
                }
            }

            // Build response (matches Python output format)
            List<Map<String, Object>> classificationMaps = new ArrayList<>();
            for (InstrumentClassification c : classifications) {
                Map<String, Object> cMap = new LinkedHashMap<>();
                cMap.put("symbol", c.getSymbol());
                cMap.put("name", c.getName());
                cMap.put("type", c.getInstrumentType());
                cMap.put("current_price", c.getCurrentPrice());
                cMap.put("asset_class", c.getAllocationAssetClass());
                cMap.put("regions", c.getAllocationRegions());
                cMap.put("sectors", c.getAllocationSectors());
                classificationMaps.add(cMap);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tagged", classifications.size());
            body.put("updated", updated);
            body.put("errors", errors);
            body.put("classifications", classificationMaps);

            return Map.of("statusCode", 200, "body", body);

        } catch (Exception e) {
            log.error("Tagger function error: {}", e.getMessage(), e);
            return errorResponse(500, e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void upsertInstrument(InstrumentClassification c) {
        Optional<Map<String, Object>> existing = instrumentRepo.findBySymbol(c.getSymbol());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", c.getName());
        data.put("instrument_type", c.getInstrumentType());
        data.put("current_price", BigDecimal.valueOf(c.getCurrentPrice()));
        data.put("allocation_asset_class", c.getAllocationAssetClass());
        data.put("allocation_regions", c.getAllocationRegions());
        data.put("allocation_sectors", c.getAllocationSectors());

        if (existing.isPresent()) {
            int rows = instrumentRepo.updateInstrument(c.getSymbol(), data);
            log.info("Updated {} in database ({} rows)", c.getSymbol(), rows);
        } else {
            data.put("symbol", c.getSymbol());
            instrumentRepo.createInstrument(data);
            log.info("Created {} in database", c.getSymbol());
        }
    }

    private static Map<String, Object> errorResponse(int statusCode, String message) {
        return Map.of("statusCode", statusCode, "body", Map.of("error", message));
    }
}
