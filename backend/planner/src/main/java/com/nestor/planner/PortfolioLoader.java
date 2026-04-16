package com.nestor.planner;

import com.nestor.common.db.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Loads portfolio data from the database and handles missing instruments.
 */
public class PortfolioLoader {

    private static final Logger log = LoggerFactory.getLogger(PortfolioLoader.class);

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;

    public PortfolioLoader(JobRepository jobRepository, UserRepository userRepository,
                           AccountRepository accountRepository, PositionRepository positionRepository,
                           InstrumentRepository instrumentRepository) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.instrumentRepository = instrumentRepository;
    }

    /**
     * Find instruments in the user's portfolio that lack allocation data and need classification.
     *
     * @param jobId the job UUID
     * @return list of instruments needing classification (each with "symbol" and "name")
     */
    public List<Map<String, String>> findMissingInstruments(String jobId) {
        Map<String, Object> job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        String userId = (String) job.get("clerk_user_id");
        List<Map<String, Object>> accounts = accountRepository.findByUser(userId);

        List<Map<String, String>> missing = new ArrayList<>();
        for (Map<String, Object> account : accounts) {
            String accountId = String.valueOf(account.get("id"));
            List<Map<String, Object>> positions = positionRepository.findByAccount(accountId);

            for (Map<String, Object> position : positions) {
                String symbol = (String) position.get("symbol");
                Optional<Map<String, Object>> instrumentOpt = instrumentRepository.findBySymbol(symbol);

                if (instrumentOpt.isPresent()) {
                    Map<String, Object> instrument = instrumentOpt.get();
                    boolean hasAllocations = hasNonEmptyJson(instrument, "allocation_regions")
                            && hasNonEmptyJson(instrument, "allocation_sectors")
                            && hasNonEmptyJson(instrument, "allocation_asset_class");
                    if (!hasAllocations) {
                        missing.add(Map.of(
                                "symbol", symbol,
                                "name", String.valueOf(instrument.getOrDefault("name", ""))));
                    }
                } else {
                    missing.add(Map.of("symbol", symbol, "name", ""));
                }
            }
        }

        return missing;
    }

    /**
     * Load a portfolio summary with statistics for the orchestrator.
     *
     * @param jobId the job UUID
     * @return summary map with total_value, num_accounts, num_positions, years_until_retirement, etc.
     */
    public Map<String, Object> loadPortfolioSummary(String jobId) {
        Map<String, Object> job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        String userId = (String) job.get("clerk_user_id");
        Map<String, Object> user = userRepository.findByClerkId(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        List<Map<String, Object>> accounts = accountRepository.findByUser(userId);

        double totalValue = 0.0;
        int totalPositions = 0;
        double totalCash = 0.0;

        for (Map<String, Object> account : accounts) {
            totalCash += toDouble(account.getOrDefault("cash_balance", 0));
            String accountId = String.valueOf(account.get("id"));
            List<Map<String, Object>> positions = positionRepository.findByAccount(accountId);
            totalPositions += positions.size();

            for (Map<String, Object> position : positions) {
                String symbol = (String) position.get("symbol");
                Optional<Map<String, Object>> instrumentOpt = instrumentRepository.findBySymbol(symbol);
                if (instrumentOpt.isPresent()) {
                    double price = toDouble(instrumentOpt.get().getOrDefault("current_price", 0));
                    double quantity = toDouble(position.getOrDefault("quantity", 0));
                    totalValue += price * quantity;
                }
            }
        }
        totalValue += totalCash;

        int yearsUntilRetirement = user.get("years_until_retirement") != null
                ? ((Number) user.get("years_until_retirement")).intValue() : 30;
        double targetRetirementIncome = toDouble(user.getOrDefault("target_retirement_income", 80000));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_value", totalValue);
        summary.put("num_accounts", accounts.size());
        summary.put("num_positions", totalPositions);
        summary.put("years_until_retirement", yearsUntilRetirement);
        summary.put("target_retirement_income", targetRetirementIncome);
        return summary;
    }

    /**
     * Load full portfolio data in the nested format that sub-agents expect:
     * { "accounts": [ { "name", "type", "cash_balance", "positions": [ { "symbol", "quantity", "instrument": {...} } ] } ],
     *   "years_until_retirement", "target_retirement_income", "current_age" }
     *
     * @param jobId the job UUID
     * @return full portfolio data map and user data map wrapped together
     */
    public Map<String, Object> loadFullPortfolioData(String jobId) {
        Map<String, Object> job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        String userId = (String) job.get("clerk_user_id");
        Map<String, Object> user = userRepository.findByClerkId(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        List<Map<String, Object>> accounts = accountRepository.findByUser(userId);
        List<Map<String, Object>> accountList = new ArrayList<>();

        for (Map<String, Object> account : accounts) {
            Map<String, Object> accountMap = new LinkedHashMap<>();
            accountMap.put("name", account.getOrDefault("name", "Unknown"));
            accountMap.put("type", account.getOrDefault("type", "unknown"));
            accountMap.put("cash_balance", toDouble(account.getOrDefault("cash_balance", 0)));

            String accountId = String.valueOf(account.get("id"));
            List<Map<String, Object>> positions = positionRepository.findByAccount(accountId);
            List<Map<String, Object>> positionList = new ArrayList<>();

            for (Map<String, Object> position : positions) {
                Map<String, Object> positionMap = new LinkedHashMap<>();
                positionMap.put("symbol", position.get("symbol"));
                positionMap.put("quantity", toDouble(position.getOrDefault("quantity", 0)));

                String symbol = (String) position.get("symbol");
                Optional<Map<String, Object>> instrumentOpt = instrumentRepository.findBySymbol(symbol);
                if (instrumentOpt.isPresent()) {
                    Map<String, Object> inst = instrumentOpt.get();
                    Map<String, Object> instrumentMap = new LinkedHashMap<>();
                    instrumentMap.put("current_price", toDouble(inst.getOrDefault("current_price", 0)));
                    instrumentMap.put("name", inst.getOrDefault("name", ""));
                    instrumentMap.put("asset_class", inst.getOrDefault("asset_class", ""));
                    instrumentMap.put("allocation_asset_class", inst.getOrDefault("allocation_asset_class", Map.of()));
                    instrumentMap.put("allocation_regions", inst.getOrDefault("allocation_regions", Map.of()));
                    instrumentMap.put("allocation_sectors", inst.getOrDefault("allocation_sectors", Map.of()));
                    positionMap.put("instrument", instrumentMap);
                } else {
                    positionMap.put("instrument", Map.of());
                }
                positionList.add(positionMap);
            }
            accountMap.put("positions", positionList);
            accountList.add(accountMap);
        }

        int yearsUntilRetirement = user.get("years_until_retirement") != null
                ? ((Number) user.get("years_until_retirement")).intValue() : 30;
        double targetRetirementIncome = toDouble(user.getOrDefault("target_retirement_income", 80000));
        int currentAge = user.get("current_age") != null
                ? ((Number) user.get("current_age")).intValue() : 40;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accounts", accountList);
        data.put("years_until_retirement", yearsUntilRetirement);
        data.put("target_retirement_income", targetRetirementIncome);
        data.put("current_age", currentAge);

        // India localization fields for sub-agents
        data.put("country_code", user.getOrDefault("country_code", "IN"));
        data.put("currency_code", user.getOrDefault("currency_code", "INR"));
        data.put("city_tier", user.getOrDefault("city_tier", "tier_1"));
        data.put("healthcare_preference", user.getOrDefault("healthcare_preference", "mixed"));
        data.put("expected_family_support_ratio", toDouble(user.getOrDefault("expected_family_support_ratio", 0)));
        data.put("expected_post_retirement_family_obligations", toDouble(user.getOrDefault("expected_post_retirement_family_obligations", 0)));
        data.put("tax_regime_preference", user.getOrDefault("tax_regime_preference", "new"));
        data.put("annual_salary_income", toDouble(user.getOrDefault("annual_salary_income", 0)));
        data.put("deductions_80c", toDouble(user.getOrDefault("deductions_80c", 0)));
        data.put("deductions_80d", toDouble(user.getOrDefault("deductions_80d", 0)));
        data.put("nps_80ccd1b", toDouble(user.getOrDefault("nps_80ccd1b", 0)));
        data.put("fixed_income_preference", toDouble(user.getOrDefault("fixed_income_preference", 30)));
        data.put("gold_preference", toDouble(user.getOrDefault("gold_preference", 10)));

        return data;
    }

    /**
     * Load user profile data for sub-agents.
     *
     * @param jobId the job UUID
     * @return user data map
     */
    public Map<String, Object> loadUserData(String jobId) {
        Map<String, Object> job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        String userId = (String) job.get("clerk_user_id");
        Map<String, Object> user = userRepository.findByClerkId(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("years_until_retirement", user.getOrDefault("years_until_retirement", 30));
        userData.put("target_retirement_income", user.getOrDefault("target_retirement_income", 80000));
        userData.put("current_age", user.getOrDefault("current_age", 40));

        // India localization fields
        userData.put("country_code", user.getOrDefault("country_code", "IN"));
        userData.put("currency_code", user.getOrDefault("currency_code", "INR"));
        userData.put("tax_regime_preference", user.getOrDefault("tax_regime_preference", "new"));
        userData.put("annual_salary_income", toDouble(user.getOrDefault("annual_salary_income", 0)));
        userData.put("annual_business_income", toDouble(user.getOrDefault("annual_business_income", 0)));
        userData.put("annual_other_income", toDouble(user.getOrDefault("annual_other_income", 0)));
        userData.put("deductions_80c", toDouble(user.getOrDefault("deductions_80c", 0)));
        userData.put("deductions_80d", toDouble(user.getOrDefault("deductions_80d", 0)));
        userData.put("nps_80ccd1b", toDouble(user.getOrDefault("nps_80ccd1b", 0)));
        userData.put("hra_claim", toDouble(user.getOrDefault("hra_claim", 0)));
        userData.put("home_loan_interest", toDouble(user.getOrDefault("home_loan_interest", 0)));
        userData.put("city_tier", user.getOrDefault("city_tier", "tier_1"));
        userData.put("healthcare_preference", user.getOrDefault("healthcare_preference", "mixed"));
        userData.put("expected_family_support_ratio", toDouble(user.getOrDefault("expected_family_support_ratio", 0)));
        userData.put("dependent_parents_count", user.getOrDefault("dependent_parents_count", 0));
        userData.put("expected_post_retirement_family_obligations", toDouble(user.getOrDefault("expected_post_retirement_family_obligations", 0)));
        userData.put("fixed_income_preference", toDouble(user.getOrDefault("fixed_income_preference", 30)));
        userData.put("gold_preference", toDouble(user.getOrDefault("gold_preference", 10)));
        userData.put("guaranteed_income_priority", toDouble(user.getOrDefault("guaranteed_income_priority", 50)));

        return userData;
    }

    /**
     * Collect all unique symbols from the user's portfolio for a given job.
     */
    public Set<String> getAllSymbols(String jobId) {
        Map<String, Object> job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        String userId = (String) job.get("clerk_user_id");
        List<Map<String, Object>> accounts = accountRepository.findByUser(userId);
        Set<String> symbols = new LinkedHashSet<>();

        for (Map<String, Object> account : accounts) {
            String accountId = String.valueOf(account.get("id"));
            List<Map<String, Object>> positions = positionRepository.findByAccount(accountId);
            for (Map<String, Object> position : positions) {
                symbols.add((String) position.get("symbol"));
            }
        }
        return symbols;
    }

    @SuppressWarnings("unchecked")
    private boolean hasNonEmptyJson(Map<String, Object> instrument, String key) {
        Object val = instrument.get(key);
        if (val == null) return false;
        if (val instanceof Map<?, ?> map) return !map.isEmpty();
        if (val instanceof String s) return !s.isBlank() && !s.equals("{}") && !s.equals("null");
        return false;
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
