package com.nestor.retirement;

import com.nestor.common.db.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * Spring Cloud Function bean ({@code retirementFunction}).
 * <p>
 * Receives a Lambda event with {@code job_id} and optional {@code portfolio_data},
 * runs Monte Carlo simulation and Bedrock-backed narrative analysis,
 * saves results to Aurora's {@code retirement_payload} column, and returns a summary.
 * <p>
 * Expected input:
 * <pre>{@code
 * {
 *   "job_id": "uuid",
 *   "portfolio_data": { "accounts": [...] }
 * }
 * }</pre>
 */
public class RetirementFunction implements Function<Map<String, Object>, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(RetirementFunction.class);

    private final RetirementAnalyzer analyzer;
    private final JobRepository jobRepository;

    public RetirementFunction(RetirementAnalyzer analyzer, JobRepository jobRepository) {
        this.analyzer = analyzer;
        this.jobRepository = jobRepository;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(Map<String, Object> event) {
        try {
            String jobId = (String) event.get("job_id");
            if (jobId == null || jobId.isBlank()) {
                return errorResponse(400, "job_id is required");
            }

            Map<String, Object> portfolioData = (Map<String, Object>) event.get("portfolio_data");
            if (portfolioData == null || portfolioData.isEmpty()) {
                log.warn("Retirement: No portfolio_data in event, loading from database is not yet implemented");
                return errorResponse(400, "portfolio_data is required");
            }

            log.info("Retirement: Processing job {}", jobId);

            // Extract user preferences from portfolio_data or use defaults
            int yearsUntilRetirement = getInt(portfolioData, "years_until_retirement", 30);
            double targetIncome = getDouble(portfolioData, "target_retirement_income", 80000.0);
            int currentAge = getInt(portfolioData, "current_age", 40);

            // India localization fields
            String countryCode = getString(portfolioData, "country_code", "IN");
            String currencyCode = getString(portfolioData, "currency_code", "INR");
            boolean isIndia = "IN".equals(countryCode);
            String currencySymbol = isIndia ? "₹" : "$";
            String cityTier = getString(portfolioData, "city_tier", "tier_1");
            String healthcarePref = getString(portfolioData, "healthcare_preference", "mixed");
            double familySupportRatio = getDouble(portfolioData, "expected_family_support_ratio", 0.0);
            double postRetirementObligations = getDouble(portfolioData, "expected_post_retirement_family_obligations", 0.0);

            // Calculate portfolio value and asset allocation
            double portfolioValue = PortfolioCalculator.calculatePortfolioValue(portfolioData);
            Map<String, Double> allocation = PortfolioCalculator.calculateAssetAllocation(portfolioData);

            log.info("Retirement: Portfolio value={}{}, allocation={}", 
                    currencySymbol, String.format("%.0f", portfolioValue), allocation);

            // Build India params for Monte Carlo if applicable
            Map<String, Object> indiaParams = null;
            if (isIndia) {
                indiaParams = new LinkedHashMap<>();
                indiaParams.put("city_tier", cityTier);
                indiaParams.put("healthcare_preference", healthcarePref);
                indiaParams.put("family_support_ratio", familySupportRatio);
                indiaParams.put("post_retirement_obligations", postRetirementObligations);
            }

            // Run Monte Carlo simulation
            Map<String, Object> monteCarlo = MonteCarloSimulator.simulate(
                    portfolioValue, yearsUntilRetirement, targetIncome, allocation, 500, indiaParams);

            // Run India tax comparison if applicable
            Map<String, Object> taxComparison = null;
            if (isIndia) {
                double grossIncome = getDouble(portfolioData, "annual_salary_income", 0)
                        + getDouble(portfolioData, "annual_business_income", 0)
                        + getDouble(portfolioData, "annual_other_income", 0);
                if (grossIncome > 0) {
                    taxComparison = IndiaTaxCalculator.compare(
                            grossIncome,
                            getDouble(portfolioData, "deductions_80c", 0),
                            getDouble(portfolioData, "deductions_80d", 0),
                            getDouble(portfolioData, "nps_80ccd1b", 0),
                            getDouble(portfolioData, "hra_claim", 0),
                            getDouble(portfolioData, "home_loan_interest", 0),
                            getInt(portfolioData, "dependent_parents_count", 0));
                }
            }

            // Generate projections
            List<Map<String, Object>> projections = PortfolioCalculator.generateProjections(
                    portfolioValue, yearsUntilRetirement, allocation, currentAge);

            // Build task prompt for Bedrock
            String taskPrompt = buildTaskPrompt(portfolioValue, allocation, yearsUntilRetirement,
                    targetIncome, currentAge, monteCarlo, projections, currencySymbol,
                    isIndia, cityTier, healthcarePref, familySupportRatio, taxComparison);

            // Generate narrative analysis via Bedrock
            String analysis = analyzer.analyze(taskPrompt);

            // Save retirement payload to database
            Map<String, Object> retirementPayload = new LinkedHashMap<>();
            retirementPayload.put("analysis", analysis);
            retirementPayload.put("generated_at", Instant.now().toString());
            retirementPayload.put("agent", "retirement");

            boolean saved = false;
            try {
                int rows = jobRepository.updateRetirement(jobId, retirementPayload);
                saved = rows > 0;
                log.info("Retirement: Database update returned: {} rows", rows);
            } catch (Exception e) {
                log.error("Retirement: Database error: {}", e.getMessage(), e);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", saved);
            body.put("message", saved
                    ? "Retirement analysis completed"
                    : "Analysis completed but failed to save");
            body.put("final_output", analysis);

            return Map.of("statusCode", 200, "body", body);

        } catch (Exception e) {
            log.error("Retirement function error: {}", e.getMessage(), e);
            return errorResponse(500, e.getMessage());
        }
    }

    private String buildTaskPrompt(double portfolioValue, Map<String, Double> allocation,
                                    int yearsUntilRetirement, double targetIncome, int currentAge,
                                    Map<String, Object> monteCarlo, List<Map<String, Object>> projections,
                                    String currencySymbol, boolean isIndia, String cityTier,
                                    String healthcarePref, double familySupportRatio,
                                    Map<String, Object> taxComparison) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Portfolio Analysis Context\n\n");
        sb.append("## Current Situation\n");
        sb.append(String.format("- Portfolio Value: %s%,.0f%n", currencySymbol, portfolioValue));
        sb.append("- Asset Allocation: ");
        StringJoiner allocationJoiner = new StringJoiner(", ");
        for (var entry : allocation.entrySet()) {
            if (entry.getValue() > 0) {
                allocationJoiner.add(String.format("%s: %.0f%%",
                        capitalize(entry.getKey()), entry.getValue() * 100));
            }
        }
        sb.append(allocationJoiner).append("\n");
        sb.append(String.format("- Years to Retirement: %d%n", yearsUntilRetirement));
        sb.append(String.format("- Target Annual Income: %s%,.0f%n", currencySymbol, targetIncome));
        sb.append(String.format("- Current Age: %d%n", currentAge));

        if (isIndia) {
            sb.append(String.format("- Country: India%n"));
            sb.append(String.format("- City Tier: %s%n", cityTier.replace("_", " ")));
            sb.append(String.format("- Healthcare Preference: %s%n", healthcarePref));
            if (familySupportRatio > 0) {
                sb.append(String.format("- Family Support Ratio: %.0f%% of income%n", familySupportRatio * 100));
            }
        }
        sb.append("\n");

        // Tax comparison section for India
        if (isIndia && taxComparison != null) {
            sb.append("## India Tax Regime Comparison\n");
            sb.append(String.format("- Old Regime Tax: %s%,.0f%n", currencySymbol,
                    ((Number) taxComparison.get("old_regime_tax")).doubleValue()));
            sb.append(String.format("- New Regime Tax: %s%,.0f%n", currencySymbol,
                    ((Number) taxComparison.get("new_regime_tax")).doubleValue()));
            sb.append(String.format("- Recommended Regime: %s%n", taxComparison.get("recommended_regime")));
            sb.append(String.format("- Tax Saving by Switching: %s%,.0f%n%n", currencySymbol,
                    ((Number) taxComparison.get("delta")).doubleValue()));
        }

        sb.append("## Monte Carlo Simulation Results (500 scenarios)\n");
        sb.append(String.format("- Success Rate: %s%% (probability of sustaining retirement income for 30 years)%n",
                monteCarlo.get("success_rate")));
        sb.append(String.format("- Expected Portfolio Value at Retirement: %s%,.0f%n", currencySymbol,
                ((Number) monteCarlo.get("expected_value_at_retirement")).doubleValue()));
        sb.append(String.format("- 10th Percentile Outcome: %s%,.0f (worst case)%n", currencySymbol,
                ((Number) monteCarlo.get("percentile_10")).doubleValue()));
        sb.append(String.format("- Median Final Value: %s%,.0f%n", currencySymbol,
                ((Number) monteCarlo.get("median_final_value")).doubleValue()));
        sb.append(String.format("- 90th Percentile Outcome: %s%,.0f (best case)%n", currencySymbol,
                ((Number) monteCarlo.get("percentile_90")).doubleValue()));
        sb.append(String.format("- Average Years Portfolio Lasts: %s years%n", monteCarlo.get("average_years_lasted")));

        if (isIndia && monteCarlo.containsKey("lifestyle_inflation_rate")) {
            sb.append(String.format("- Lifestyle Inflation Rate: %s%%%n", monteCarlo.get("lifestyle_inflation_rate")));
            sb.append(String.format("- Healthcare Inflation Rate: %s%%%n", monteCarlo.get("healthcare_inflation_rate")));
        }
        sb.append("\n");

        sb.append("## Key Projections (Milestones)\n");
        int count = 0;
        for (Map<String, Object> proj : projections) {
            if (count >= 6) break;
            int age = ((Number) proj.get("age")).intValue();
            double value = ((Number) proj.get("portfolio_value")).doubleValue();
            String phase = (String) proj.get("phase");
            if ("accumulation".equals(phase)) {
                sb.append(String.format("- Age %d: %s%,.0f (building wealth)%n", age, currencySymbol, value));
            } else {
                double income = ((Number) proj.get("annual_income")).doubleValue();
                sb.append(String.format("- Age %d: %s%,.0f (annual income: %s%,.0f)%n", age, currencySymbol, value, currencySymbol, income));
            }
            count++;
        }

        if (isIndia) {
            sb.append("\n## India-Specific Considerations\n");
            sb.append("Please address the following in your analysis:\n");
            sb.append("- Tax regime recommendation (old vs new) based on the comparison above\n");
            sb.append("- Impact of private vs government healthcare on retirement corpus\n");
            sb.append("- Family support obligations and their effect on withdrawal sustainability\n");
            sb.append("- City-tier lifestyle inflation risk (especially for Tier-1 metros)\n");
            sb.append("- EPF/PPF/NPS optimization recommendations\n");
            sb.append("- Gold and fixed-income allocation for Indian risk profile\n");
        }

        return sb.toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return defaultValue;
    }

    private static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return defaultValue;
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        if (val instanceof String s) return s;
        return defaultValue;
    }

    private static Map<String, Object> errorResponse(int statusCode, String message) {
        return Map.of("statusCode", statusCode, "body", Map.of("error", message));
    }
}
