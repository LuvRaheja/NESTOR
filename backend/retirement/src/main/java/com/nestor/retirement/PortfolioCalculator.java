package com.nestor.retirement;

import java.util.*;

/**
 * Portfolio value and asset allocation calculator.
 * <p>
 * Ported from Python {@code agent.py}'s {@code calculate_portfolio_value},
 * {@code calculate_asset_allocation}, and {@code generate_projections}.
 */
public final class PortfolioCalculator {

    private PortfolioCalculator() {}

    /**
     * Calculate the total current portfolio value from all accounts.
     */
    @SuppressWarnings("unchecked")
    public static double calculatePortfolioValue(Map<String, Object> portfolioData) {
        double totalValue = 0.0;

        List<Map<String, Object>> accounts = (List<Map<String, Object>>) portfolioData.getOrDefault("accounts", List.of());
        for (Map<String, Object> account : accounts) {
            totalValue += toDouble(account.get("cash_balance"), 0.0);

            List<Map<String, Object>> positions = (List<Map<String, Object>>) account.getOrDefault("positions", List.of());
            for (Map<String, Object> position : positions) {
                double quantity = toDouble(position.get("quantity"), 0.0);
                Map<String, Object> instrument = (Map<String, Object>) position.getOrDefault("instrument", Map.of());
                double price = toDouble(instrument.get("current_price"), 100.0);
                totalValue += quantity * price;
            }
        }
        return totalValue;
    }

    /**
     * Calculate weighted asset allocation percentages across the portfolio.
     * Returns allocation fractions (0.0–1.0), not percentages.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Double> calculateAssetAllocation(Map<String, Object> portfolioData) {
        double totalEquity = 0.0, totalBonds = 0.0, totalRealEstate = 0.0;
        double totalCommodities = 0.0, totalCash = 0.0, totalValue = 0.0;

        List<Map<String, Object>> accounts = (List<Map<String, Object>>) portfolioData.getOrDefault("accounts", List.of());
        for (Map<String, Object> account : accounts) {
            double cash = toDouble(account.get("cash_balance"), 0.0);
            totalCash += cash;
            totalValue += cash;

            List<Map<String, Object>> positions = (List<Map<String, Object>>) account.getOrDefault("positions", List.of());
            for (Map<String, Object> position : positions) {
                double quantity = toDouble(position.get("quantity"), 0.0);
                Map<String, Object> instrument = (Map<String, Object>) position.getOrDefault("instrument", Map.of());
                double price = toDouble(instrument.get("current_price"), 100.0);
                double value = quantity * price;
                totalValue += value;

                Map<String, Object> assetAllocation = (Map<String, Object>) instrument.getOrDefault("allocation_asset_class", Map.of());
                if (!assetAllocation.isEmpty()) {
                    totalEquity += value * toDouble(assetAllocation.get("equity"), 0.0) / 100.0;
                    totalBonds += value * toDouble(assetAllocation.get("fixed_income"), 0.0) / 100.0;
                    totalRealEstate += value * toDouble(assetAllocation.get("real_estate"), 0.0) / 100.0;
                    totalCommodities += value * toDouble(assetAllocation.get("commodities"), 0.0) / 100.0;
                }
            }
        }

        if (totalValue == 0.0) {
            return Map.of("equity", 0.0, "bonds", 0.0, "real_estate", 0.0, "commodities", 0.0, "cash", 0.0);
        }

        Map<String, Double> result = new LinkedHashMap<>();
        result.put("equity", totalEquity / totalValue);
        result.put("bonds", totalBonds / totalValue);
        result.put("real_estate", totalRealEstate / totalValue);
        result.put("commodities", totalCommodities / totalValue);
        result.put("cash", totalCash / totalValue);
        return result;
    }

    /**
     * Generate simplified retirement projections at 5-year milestones.
     */
    public static List<Map<String, Object>> generateProjections(
            double currentValue, int yearsUntilRetirement,
            Map<String, Double> allocation, int currentAge) {

        double expectedReturn = allocation.getOrDefault("equity", 0.0) * 0.07
                + allocation.getOrDefault("bonds", 0.0) * 0.04
                + allocation.getOrDefault("real_estate", 0.0) * 0.06
                + allocation.getOrDefault("cash", 0.0) * 0.02;

        List<Map<String, Object>> projections = new ArrayList<>();
        double portfolioValue = currentValue;

        // Milestone years (every 5 years)
        List<Integer> milestones = new ArrayList<>();
        for (int y = 0; y <= yearsUntilRetirement + 30; y += 5) {
            milestones.add(y);
        }

        for (int year : milestones) {
            int age = currentAge + year;

            if (year <= yearsUntilRetirement) {
                int steps = Math.min(5, year);
                for (int i = 0; i < steps; i++) {
                    portfolioValue = portfolioValue * (1.0 + expectedReturn) + 10_000.0;
                }
                if (portfolioValue > 0) {
                    Map<String, Object> proj = new LinkedHashMap<>();
                    proj.put("year", year);
                    proj.put("age", age);
                    proj.put("portfolio_value", Math.round(portfolioValue * 100.0) / 100.0);
                    proj.put("annual_income", 0.0);
                    proj.put("phase", "accumulation");
                    projections.add(proj);
                }
            } else {
                double withdrawalRate = 0.04;
                double annualIncome = portfolioValue * withdrawalRate;
                int yearsInRetirement = Math.min(5, year - yearsUntilRetirement);
                for (int i = 0; i < yearsInRetirement; i++) {
                    portfolioValue = portfolioValue * (1.0 + expectedReturn) - annualIncome;
                }
                if (portfolioValue > 0) {
                    Map<String, Object> proj = new LinkedHashMap<>();
                    proj.put("year", year);
                    proj.put("age", age);
                    proj.put("portfolio_value", Math.round(portfolioValue * 100.0) / 100.0);
                    proj.put("annual_income", Math.round(annualIncome * 100.0) / 100.0);
                    proj.put("phase", "retirement");
                    projections.add(proj);
                }
            }
        }

        return projections;
    }

    private static double toDouble(Object val, double defaultValue) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultValue;
    }
}
