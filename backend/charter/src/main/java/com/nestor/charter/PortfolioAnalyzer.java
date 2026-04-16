package com.nestor.charter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Pure-computation portfolio analyzer.
 * Mirrors the Python {@code analyze_portfolio()} function in Alex's charter agent.
 */
public final class PortfolioAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(PortfolioAnalyzer.class);

    private PortfolioAnalyzer() {}

    /**
     * Analyze portfolio data and return a human-readable analysis string
     * suitable for inclusion in the LLM prompt.
     *
     * @param portfolioData the portfolio map (accounts → positions → instruments)
     * @return multi-line analysis text
     */
    @SuppressWarnings("unchecked")
    public static String analyze(Map<String, Object> portfolioData) {
        List<String> result = new ArrayList<>();
        double totalValue = 0.0;
        Map<String, Double> positionValues = new LinkedHashMap<>();
        Map<String, AccountTotal> accountTotals = new LinkedHashMap<>();

        List<Map<String, Object>> accounts = (List<Map<String, Object>>) portfolioData.getOrDefault("accounts", List.of());

        // First pass: calculate position values and totals
        for (Map<String, Object> account : accounts) {
            String accountName = (String) account.getOrDefault("name", "Unknown");
            String accountType = (String) account.getOrDefault("type", "unknown");
            double cash = toDouble(account.get("cash_balance"), 0.0);

            AccountTotal at = accountTotals.computeIfAbsent(accountName,
                    k -> new AccountTotal(accountType));
            at.value += cash;
            totalValue += cash;

            List<Map<String, Object>> positions = (List<Map<String, Object>>) account.getOrDefault("positions", List.of());
            for (Map<String, Object> position : positions) {
                String symbol = (String) position.get("symbol");
                double quantity = toDouble(position.get("quantity"), 0.0);
                Map<String, Object> instrument = (Map<String, Object>) position.getOrDefault("instrument", Map.of());
                double price = toDouble(instrument.get("current_price"), 1.0);
                if (price == 0.0) {
                    log.warn("Charter: No price for {}, using default of 1.0", symbol);
                    price = 1.0;
                }
                double value = quantity * price;

                positionValues.merge(symbol, value, Double::sum);
                at.value += value;
                totalValue += value;
            }
        }

        // Build analysis summary
        result.add("Portfolio Analysis:");
        result.add(String.format("Total Value: $%,.2f", totalValue));
        result.add(String.format("Number of Accounts: %d", accountTotals.size()));
        result.add(String.format("Number of Positions: %d", positionValues.size()));

        result.add("\nAccount Breakdown:");
        double tv = totalValue;
        accountTotals.forEach((name, at) -> {
            double pct = tv > 0 ? at.value / tv * 100 : 0;
            result.add(String.format("  %s (%s): $%,.2f (%.1f%%)", name, at.type, at.value, pct));
        });

        result.add("\nTop Holdings by Value:");
        positionValues.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> {
                    double pct = tv > 0 ? e.getValue() / tv * 100 : 0;
                    result.add(String.format("  %s: $%,.2f (%.1f%%)", e.getKey(), e.getValue(), pct));
                });

        // Aggregated allocations
        Map<String, Double> assetClasses = new LinkedHashMap<>();
        Map<String, Double> regions = new LinkedHashMap<>();
        Map<String, Double> sectors = new LinkedHashMap<>();

        for (Map<String, Object> account : accounts) {
            List<Map<String, Object>> positions = (List<Map<String, Object>>) account.getOrDefault("positions", List.of());
            for (Map<String, Object> position : positions) {
                double quantity = toDouble(position.get("quantity"), 0.0);
                Map<String, Object> instrument = (Map<String, Object>) position.getOrDefault("instrument", Map.of());
                double price = toDouble(instrument.get("current_price"), 1.0);
                if (price == 0.0) price = 1.0;
                double value = quantity * price;

                accumulateAllocation(assetClasses, instrument, "allocation_asset_class", value);
                accumulateAllocation(regions, instrument, "allocation_regions", value);
                accumulateAllocation(sectors, instrument, "allocation_sectors", value);
            }
        }

        // Add cash to asset classes
        double totalCash = accounts.stream()
                .mapToDouble(a -> toDouble(a.get("cash_balance"), 0.0))
                .sum();
        if (totalCash > 0) {
            assetClasses.merge("cash", totalCash, Double::sum);
        }

        result.add("\nCalculated Allocations:");
        result.add("\nAsset Classes:");
        assetClasses.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> result.add(String.format("  %s: $%,.2f", e.getKey(), e.getValue())));

        result.add("\nGeographic Regions:");
        regions.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> result.add(String.format("  %s: $%,.2f", e.getKey(), e.getValue())));

        result.add("\nSectors:");
        sectors.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> result.add(String.format("  %s: $%,.2f", e.getKey(), e.getValue())));

        return String.join("\n", result);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void accumulateAllocation(Map<String, Double> target,
                                              Map<String, Object> instrument,
                                              String key, double positionValue) {
        Object raw = instrument.get(key);
        if (raw instanceof Map<?, ?> alloc) {
            for (Map.Entry<?, ?> entry : alloc.entrySet()) {
                double pct = toDouble(entry.getValue(), 0.0);
                double weighted = positionValue * (pct / 100.0);
                target.merge(String.valueOf(entry.getKey()), weighted, Double::sum);
            }
        }
    }

    private static double toDouble(Object value, double defaultVal) {
        if (value == null) return defaultVal;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static class AccountTotal {
        String type;
        double value;
        AccountTotal(String type) { this.type = type; }
    }
}
