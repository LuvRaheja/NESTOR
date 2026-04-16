package com.nestor.tagger.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/**
 * Structured-output POJO returned by Bedrock via the tool-use trick.
 * Field names use snake_case to match the JSON schema sent to the model.
 * <p>
 * Replaces the Python Pydantic {@code InstrumentClassification} model.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InstrumentClassification {

    private String symbol;
    private String name;
    private String instrumentType;
    private double currentPrice;
    private Map<String, Double> allocationAssetClass;
    private Map<String, Double> allocationRegions;
    private Map<String, Double> allocationSectors;

    public InstrumentClassification() {}

    // ── Getters / Setters ────────────────────────────────────────────────────────

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getInstrumentType() { return instrumentType; }
    public void setInstrumentType(String instrumentType) { this.instrumentType = instrumentType; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public Map<String, Double> getAllocationAssetClass() { return allocationAssetClass; }
    public void setAllocationAssetClass(Map<String, Double> allocationAssetClass) { this.allocationAssetClass = allocationAssetClass; }

    public Map<String, Double> getAllocationRegions() { return allocationRegions; }
    public void setAllocationRegions(Map<String, Double> allocationRegions) { this.allocationRegions = allocationRegions; }

    public Map<String, Double> getAllocationSectors() { return allocationSectors; }
    public void setAllocationSectors(Map<String, Double> allocationSectors) { this.allocationSectors = allocationSectors; }

    // ── Validation ───────────────────────────────────────────────────────────────

    /** Validate that all allocation maps sum to ~100 (±3 tolerance). */
    public void validate() {
        validateAllocationSum("asset_class", allocationAssetClass);
        validateAllocationSum("regions", allocationRegions);
        validateAllocationSum("sectors", allocationSectors);
    }

    private static void validateAllocationSum(String label, Map<String, Double> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            throw new IllegalArgumentException(label + " allocations cannot be empty");
        }
        double total = allocations.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(total - 100.0) > 3.0) {
            throw new IllegalArgumentException(
                    label + " allocations must sum to ~100, got " + total);
        }
    }

    /** Strip zero-valued entries from all allocation maps. */
    public void removeZeroAllocations() {
        if (allocationAssetClass != null) allocationAssetClass.values().removeIf(v -> v == 0.0);
        if (allocationRegions != null)    allocationRegions.values().removeIf(v -> v == 0.0);
        if (allocationSectors != null)    allocationSectors.values().removeIf(v -> v == 0.0);
    }
}
