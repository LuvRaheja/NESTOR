package com.nestor.retirement;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Monte Carlo retirement simulation engine.
 * <p>
 * Runs 500 (configurable) simulations of portfolio growth during accumulation,
 * then 30 years of retirement withdrawals. Returns success rate and distribution statistics.
 * <p>
 * Supports India-specific inflation buckets (general CPI, lifestyle/city-tier, healthcare)
 * and family support obligations.
 */
public final class MonteCarloSimulator {

    // Historical return parameters (annualized)
    private static final double EQUITY_RETURN_MEAN = 0.07;
    private static final double EQUITY_RETURN_STD = 0.18;
    private static final double BOND_RETURN_MEAN = 0.04;
    private static final double BOND_RETURN_STD = 0.05;
    private static final double REAL_ESTATE_RETURN_MEAN = 0.06;
    private static final double REAL_ESTATE_RETURN_STD = 0.12;
    private static final double CASH_RETURN = 0.02;
    private static final double ANNUAL_CONTRIBUTION = 10_000.0;
    private static final int RETIREMENT_YEARS = 30;
    private static final double INFLATION_RATE = 1.03;

    // India-specific defaults
    private static final double INDIA_GENERAL_CPI_INFLATION = 1.06;       // 6% CPI
    private static final double INDIA_LIFESTYLE_INFLATION_TIER1 = 1.08;   // 8% metro lifestyle
    private static final double INDIA_LIFESTYLE_INFLATION_TIER2 = 1.065;  // 6.5%
    private static final double INDIA_LIFESTYLE_INFLATION_TIER3 = 1.055;  // 5.5%
    private static final double INDIA_HEALTHCARE_INFLATION_PRIVATE = 1.14;  // 14% private
    private static final double INDIA_HEALTHCARE_INFLATION_PUBLIC = 1.08;   // 8% public
    private static final double INDIA_HEALTHCARE_INFLATION_MIXED = 1.11;    // 11% mixed

    private MonteCarloSimulator() {}

    /**
     * Run Monte Carlo simulation for retirement planning (original US-oriented API).
     */
    public static Map<String, Object> simulate(
            double currentValue,
            int yearsUntilRetirement,
            double targetAnnualIncome,
            Map<String, Double> allocation,
            int numSimulations) {
        return simulate(currentValue, yearsUntilRetirement, targetAnnualIncome, allocation,
                numSimulations, null);
    }

    /**
     * Run Monte Carlo simulation with optional India-specific parameters.
     *
     * @param indiaParams optional map with keys: city_tier, healthcare_preference,
     *                    family_support_ratio, post_retirement_obligations, annual_contribution
     */
    public static Map<String, Object> simulate(
            double currentValue,
            int yearsUntilRetirement,
            double targetAnnualIncome,
            Map<String, Double> allocation,
            int numSimulations,
            Map<String, Object> indiaParams) {

        double equityWeight = allocation.getOrDefault("equity", 0.0);
        double bondWeight = allocation.getOrDefault("bonds", 0.0);
        double realEstateWeight = allocation.getOrDefault("real_estate", 0.0);
        double cashWeight = allocation.getOrDefault("cash", 0.0);

        // Determine inflation rates based on India params
        double lifestyleInflation;
        double healthcareInflation;
        double familySupportRatio;
        double postRetirementObligations;
        double annualContribution;
        boolean isIndia = indiaParams != null;

        if (isIndia) {
            String cityTier = (String) indiaParams.getOrDefault("city_tier", "tier_1");
            String healthcarePref = (String) indiaParams.getOrDefault("healthcare_preference", "mixed");
            familySupportRatio = toDouble(indiaParams.getOrDefault("family_support_ratio", 0.0));
            postRetirementObligations = toDouble(indiaParams.getOrDefault("post_retirement_obligations", 0.0));
            annualContribution = toDouble(indiaParams.getOrDefault("annual_contribution", ANNUAL_CONTRIBUTION));

            lifestyleInflation = switch (cityTier) {
                case "tier_2" -> INDIA_LIFESTYLE_INFLATION_TIER2;
                case "tier_3" -> INDIA_LIFESTYLE_INFLATION_TIER3;
                default -> INDIA_LIFESTYLE_INFLATION_TIER1;
            };
            healthcareInflation = switch (healthcarePref) {
                case "private" -> INDIA_HEALTHCARE_INFLATION_PRIVATE;
                case "government" -> INDIA_HEALTHCARE_INFLATION_PUBLIC;
                default -> INDIA_HEALTHCARE_INFLATION_MIXED;
            };
        } else {
            lifestyleInflation = INFLATION_RATE;
            healthcareInflation = INFLATION_RATE;
            familySupportRatio = 0.0;
            postRetirementObligations = 0.0;
            annualContribution = ANNUAL_CONTRIBUTION;
        }

        int successfulScenarios = 0;
        double[] finalValues = new double[numSimulations];
        int[] yearsLasted = new int[numSimulations];
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int sim = 0; sim < numSimulations; sim++) {
            double portfolioValue = currentValue;

            // Accumulation phase
            for (int y = 0; y < yearsUntilRetirement; y++) {
                double equityReturn = rng.nextGaussian() * EQUITY_RETURN_STD + EQUITY_RETURN_MEAN;
                double bondReturn = rng.nextGaussian() * BOND_RETURN_STD + BOND_RETURN_MEAN;
                double realEstateReturn = rng.nextGaussian() * REAL_ESTATE_RETURN_STD + REAL_ESTATE_RETURN_MEAN;

                double portfolioReturn = equityWeight * equityReturn
                        + bondWeight * bondReturn
                        + realEstateWeight * realEstateReturn
                        + cashWeight * CASH_RETURN;

                portfolioValue = portfolioValue * (1.0 + portfolioReturn) + annualContribution;
            }

            // Retirement phase
            double annualWithdrawal = targetAnnualIncome;
            double healthcareExpense = isIndia ? targetAnnualIncome * 0.15 : 0; // 15% of income for healthcare
            double familyObligations = postRetirementObligations;
            int yearsIncomeLasted = 0;

            for (int y = 0; y < RETIREMENT_YEARS; y++) {
                if (portfolioValue <= 0) break;

                // Apply different inflation rates for India
                if (isIndia) {
                    annualWithdrawal *= lifestyleInflation;
                    healthcareExpense *= healthcareInflation;
                    familyObligations *= INDIA_GENERAL_CPI_INFLATION;
                } else {
                    annualWithdrawal *= INFLATION_RATE;
                }

                double totalWithdrawal = annualWithdrawal
                        + (isIndia ? healthcareExpense : 0)
                        + familyObligations
                        + (annualWithdrawal * familySupportRatio);

                double equityReturn = rng.nextGaussian() * EQUITY_RETURN_STD + EQUITY_RETURN_MEAN;
                double bondReturn = rng.nextGaussian() * BOND_RETURN_STD + BOND_RETURN_MEAN;
                double realEstateReturn = rng.nextGaussian() * REAL_ESTATE_RETURN_STD + REAL_ESTATE_RETURN_MEAN;

                double portfolioReturn = equityWeight * equityReturn
                        + bondWeight * bondReturn
                        + realEstateWeight * realEstateReturn
                        + cashWeight * CASH_RETURN;

                portfolioValue = portfolioValue * (1.0 + portfolioReturn) - totalWithdrawal;

                if (portfolioValue > 0) {
                    yearsIncomeLasted++;
                }
            }

            finalValues[sim] = Math.max(0, portfolioValue);
            yearsLasted[sim] = yearsIncomeLasted;

            if (yearsIncomeLasted >= RETIREMENT_YEARS) {
                successfulScenarios++;
            }
        }

        // Sort for percentile calculations
        Arrays.sort(finalValues);

        double successRate = (successfulScenarios / (double) numSimulations) * 100.0;
        double avgYearsLasted = Arrays.stream(yearsLasted).average().orElse(0.0);

        // Expected value at retirement (deterministic calculation)
        double expectedReturn = equityWeight * EQUITY_RETURN_MEAN
                + bondWeight * BOND_RETURN_MEAN
                + realEstateWeight * REAL_ESTATE_RETURN_MEAN
                + cashWeight * CASH_RETURN;
        double expectedValueAtRetirement = currentValue;
        for (int y = 0; y < yearsUntilRetirement; y++) {
            expectedValueAtRetirement = expectedValueAtRetirement * (1.0 + expectedReturn) + annualContribution;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success_rate", round1(successRate));
        result.put("median_final_value", round2(finalValues[numSimulations / 2]));
        result.put("percentile_10", round2(finalValues[numSimulations / 10]));
        result.put("percentile_90", round2(finalValues[9 * numSimulations / 10]));
        result.put("average_years_lasted", round1(avgYearsLasted));
        result.put("expected_value_at_retirement", round2(expectedValueAtRetirement));

        if (isIndia) {
            result.put("lifestyle_inflation_rate", round1((lifestyleInflation - 1.0) * 100));
            result.put("healthcare_inflation_rate", round1((healthcareInflation - 1.0) * 100));
        }

        return result;
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private static double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0.0; }
        }
        return 0.0;
    }
}
