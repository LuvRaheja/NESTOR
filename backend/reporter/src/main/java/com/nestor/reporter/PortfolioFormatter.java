package com.nestor.reporter;

import java.util.*;

/**
 * Portfolio metrics calculator and text formatter.
 * <p>
 * Ported from Python {@code agent.py}'s {@code calculate_portfolio_metrics}
 * and {@code format_portfolio_for_analysis}.
 */
public final class PortfolioFormatter {

    private PortfolioFormatter() {}

    /**
     * Format portfolio data and user data into a human-readable text summary for the LLM.
     */
    @SuppressWarnings("unchecked")
    public static String format(Map<String, Object> portfolioData, Map<String, Object> userData) {
        List<Map<String, Object>> accounts = (List<Map<String, Object>>)
                portfolioData.getOrDefault("accounts", List.of());

        // Determine currency from user data
        String countryCode = userData.getOrDefault("country_code", "IN").toString();
        String currencySymbol = "IN".equals(countryCode) ? "₹" : "$";

        double totalValue = 0.0;
        double totalCash = 0.0;
        int numPositions = 0;
        Set<String> uniqueSymbols = new HashSet<>();

        // First pass: compute metrics
        for (Map<String, Object> account : accounts) {
            double cash = toDouble(account.get("cash_balance"), 0.0);
            totalCash += cash;
            totalValue += cash;

            List<Map<String, Object>> positions = (List<Map<String, Object>>)
                    account.getOrDefault("positions", List.of());
            numPositions += positions.size();

            for (Map<String, Object> position : positions) {
                String symbol = (String) position.get("symbol");
                if (symbol != null) uniqueSymbols.add(symbol);

                Map<String, Object> instrument = (Map<String, Object>)
                        position.getOrDefault("instrument", Map.of());
                double price = toDouble(instrument.get("current_price"), 0.0);
                double quantity = toDouble(position.get("quantity"), 0.0);
                if (price > 0) totalValue += quantity * price;
            }
        }

        // Build formatted text
        StringBuilder sb = new StringBuilder();
        sb.append("Portfolio Overview:\n");
        sb.append(String.format("- %d accounts%n", accounts.size()));
        sb.append(String.format("- %d total positions%n", numPositions));
        sb.append(String.format("- %d unique holdings%n", uniqueSymbols.size()));
        sb.append(String.format("- %s%,.2f in cash%n", currencySymbol, totalCash));
        if (totalValue > 0) sb.append(String.format("- %s%,.2f total value%n", currencySymbol, totalValue));
        sb.append("\nAccount Details:\n");

        for (Map<String, Object> account : accounts) {
            String name = (String) account.getOrDefault("name", "Unknown");
            String accountType = (String) account.getOrDefault("account_type", "");
            double cash = toDouble(account.get("cash_balance"), 0.0);
            String typeLabel = (accountType != null && !accountType.isBlank() && !"other".equals(accountType))
                    ? " [" + accountType.replace("indian_", "").toUpperCase() + "]" : "";
            sb.append(String.format("%n%s%s (%s%,.2f cash):%n", name, typeLabel, currencySymbol, cash));

            List<Map<String, Object>> positions = (List<Map<String, Object>>)
                    account.getOrDefault("positions", List.of());
            for (Map<String, Object> position : positions) {
                String symbol = (String) position.get("symbol");
                double quantity = toDouble(position.get("quantity"), 0.0);
                Map<String, Object> instrument = (Map<String, Object>)
                        position.getOrDefault("instrument", Map.of());

                // Allocation info
                List<String> allocations = new ArrayList<>();
                Object assetClass = instrument.get("asset_class");
                if (assetClass != null) allocations.add("Asset: " + assetClass);

                String allocStr = allocations.isEmpty() ? "" : " (" + String.join(", ", allocations) + ")";
                sb.append(String.format("  - %s: %,.2f shares%s%n", symbol, quantity, allocStr));
            }
        }

        // User profile
        sb.append("\nUser Profile:\n");
        sb.append(String.format("- Country: %s%n", "IN".equals(countryCode) ? "India" : "United States"));
        sb.append(String.format("- Years to retirement: %s%n",
                userData.getOrDefault("years_until_retirement", "Not specified")));
        Object targetIncome = userData.get("target_retirement_income");
        double income = targetIncome instanceof Number n ? n.doubleValue() : 80000.0;
        sb.append(String.format("- Target retirement income: %s%,.0f/year%n", currencySymbol, income));

        // India-specific profile fields
        if ("IN".equals(countryCode)) {
            String taxRegime = userData.getOrDefault("tax_regime_preference", "new").toString();
            String cityTier = userData.getOrDefault("city_tier", "tier_1").toString();
            String healthcarePref = userData.getOrDefault("healthcare_preference", "mixed").toString();
            sb.append(String.format("- Tax regime preference: %s%n", taxRegime));
            sb.append(String.format("- City tier: %s%n", cityTier.replace("_", " ")));
            sb.append(String.format("- Healthcare preference: %s%n", healthcarePref));

            Object familyRatio = userData.get("expected_family_support_ratio");
            if (familyRatio instanceof Number fr && fr.doubleValue() > 0) {
                sb.append(String.format("- Family support ratio: %.0f%%%n", fr.doubleValue() * 100));
            }
            Object fixedIncomePref = userData.get("fixed_income_preference");
            if (fixedIncomePref instanceof Number fi) {
                sb.append(String.format("- Fixed income preference: %d/100%n", fi.intValue()));
            }
            Object goldPref = userData.get("gold_preference");
            if (goldPref instanceof Number gp) {
                sb.append(String.format("- Gold preference: %d/100%n", gp.intValue()));
            }
        }

        return sb.toString();
    }

    /**
     * Extract unique symbols from portfolio data.
     */
    @SuppressWarnings("unchecked")
    public static List<String> extractSymbols(Map<String, Object> portfolioData) {
        Set<String> symbols = new LinkedHashSet<>();
        List<Map<String, Object>> accounts = (List<Map<String, Object>>)
                portfolioData.getOrDefault("accounts", List.of());
        for (Map<String, Object> account : accounts) {
            List<Map<String, Object>> positions = (List<Map<String, Object>>)
                    account.getOrDefault("positions", List.of());
            for (Map<String, Object> position : positions) {
                String symbol = (String) position.get("symbol");
                if (symbol != null) symbols.add(symbol);
            }
        }
        return new ArrayList<>(symbols);
    }

    private static double toDouble(Object val, double defaultValue) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultValue;
    }
}
