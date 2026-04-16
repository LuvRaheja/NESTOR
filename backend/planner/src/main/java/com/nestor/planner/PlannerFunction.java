package com.nestor.planner;

import com.nestor.common.db.JobRepository;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;

/**
 * Spring Cloud Function bean ({@code plannerFunction}).
 * <p>
 * Orchestrator that coordinates the multi-agent pipeline:
 * 1. Receives SQS message (or direct invocation) with job_id
 * 2. Updates job status to running
 * 3. Handles missing instrument classifications (invokes tagger)
 * 4. Updates market prices
 * 5. Uses Bedrock tool-calling to orchestrate child agents
 * 6. Marks job as completed
 * <p>
 * Expected input (SQS event):
 * <pre>{@code
 * {
 *   "Records": [{ "body": "job-uuid" }]
 * }
 * }</pre>
 * Or direct invocation:
 * <pre>{@code
 * { "job_id": "job-uuid" }
 * }</pre>
 */
public class PlannerFunction implements Function<Map<String, Object>, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(PlannerFunction.class);

    private final JobRepository jobRepository;
    private final PortfolioLoader portfolioLoader;
    private final MarketPriceUpdater marketPriceUpdater;
    private final LambdaAgentInvoker lambdaAgentInvoker;
    private final AgentOrchestrator orchestrator;
    private final Retry retry;

    public PlannerFunction(JobRepository jobRepository, PortfolioLoader portfolioLoader,
                           MarketPriceUpdater marketPriceUpdater, LambdaAgentInvoker lambdaAgentInvoker,
                           AgentOrchestrator orchestrator) {
        this.jobRepository = jobRepository;
        this.portfolioLoader = portfolioLoader;
        this.marketPriceUpdater = marketPriceUpdater;
        this.lambdaAgentInvoker = lambdaAgentInvoker;
        this.orchestrator = orchestrator;

        // Resilience4j retry for Bedrock throttling
        this.retry = Retry.of("planner-bedrock", RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofSeconds(4))
                .retryExceptions(ThrottlingException.class)
                .build());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(Map<String, Object> event) {
        String jobId = null;
        try {
            jobId = extractJobId(event);
            if (jobId == null || jobId.isBlank()) {
                return errorResponse(400, "No job_id provided");
            }

            log.info("Planner: Starting orchestration for job {}", jobId);

            // 1. Update job status to running
            jobRepository.updateStatus(jobId, "running");

            // 2. Handle missing instruments (invoke tagger if needed)
            handleMissingInstruments(jobId);

            // 3. Update market prices
            updateMarketPrices(jobId);

            // 4. Load portfolio summary
            Map<String, Object> summary = portfolioLoader.loadPortfolioSummary(jobId);
            int numPositions = ((Number) summary.getOrDefault("num_positions", 0)).intValue();
            int yearsUntilRetirement = ((Number) summary.getOrDefault("years_until_retirement", 30)).intValue();

            log.info("Planner: Portfolio has {} positions, {} years to retirement",
                    numPositions, yearsUntilRetirement);

            // 4b. Load full portfolio data for sub-agents
            Map<String, Object> portfolioData = portfolioLoader.loadFullPortfolioData(jobId);
            Map<String, Object> userData = portfolioLoader.loadUserData(jobId);

            // 5. Run orchestration with Bedrock tool-calling (with retry)
            String taskPrompt = PlannerTemplates.buildTaskPrompt(jobId, numPositions, yearsUntilRetirement);
            final String fJobId = jobId;
            String orchestrationResult = Retry.decorateSupplier(retry,
                    () -> orchestrator.orchestrate(fJobId, taskPrompt, portfolioData, userData)).get();

            // 6. Mark job as completed
            jobRepository.updateStatus(jobId, "completed");
            log.info("Planner: Job {} completed successfully", jobId);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("message", "Analysis completed for job " + jobId);
            body.put("orchestration_result", orchestrationResult);

            return Map.of("statusCode", 200, "body", body);

        } catch (Exception e) {
            log.error("Planner: Error in orchestration: {}", e.getMessage(), e);
            if (jobId != null) {
                try {
                    jobRepository.updateStatus(jobId, "failed", e.getMessage());
                } catch (Exception dbErr) {
                    log.error("Planner: Failed to update job status: {}", dbErr.getMessage());
                }
            }
            return errorResponse(500, e.getMessage());
        }
    }

    /**
     * Extract job_id from SQS event or direct invocation payload.
     */
    @SuppressWarnings("unchecked")
    private String extractJobId(Map<String, Object> event) {
        // SQS trigger: { "Records": [{ "body": "job_id" }] }
        if (event.containsKey("Records")) {
            List<Map<String, Object>> records = (List<Map<String, Object>>) event.get("Records");
            if (records != null && !records.isEmpty()) {
                Object body = records.get(0).get("body");
                if (body instanceof String bodyStr) {
                    // Body might be JSON
                    if (bodyStr.startsWith("{")) {
                        try {
                            Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readValue(bodyStr, Map.class);
                            return (String) parsed.getOrDefault("job_id", bodyStr);
                        } catch (Exception e) {
                            // Not JSON, use as-is
                        }
                    }
                    return bodyStr;
                }
            }
        }

        // Direct invocation: { "job_id": "uuid" }
        if (event.containsKey("job_id")) {
            return (String) event.get("job_id");
        }

        return null;
    }

    /**
     * Check for instruments without allocation data and invoke tagger.
     */
    private void handleMissingInstruments(String jobId) {
        try {
            List<Map<String, String>> missing = portfolioLoader.findMissingInstruments(jobId);
            if (missing.isEmpty()) {
                log.info("Planner: All instruments have allocation data");
                return;
            }

            log.info("Planner: Found {} instruments needing classification: {}",
                    missing.size(), missing.stream().map(m -> m.get("symbol")).toList());

            Map<String, Object> result = lambdaAgentInvoker.invokeTagger(missing);
            if (result.containsKey("error")) {
                log.error("Planner: Tagger failed: {}", result.get("error"));
            } else {
                log.info("Planner: Tagger completed - classified {} instruments", missing.size());
            }
        } catch (Exception e) {
            log.error("Planner: Error handling missing instruments: {}", e.getMessage(), e);
            // Non-critical - continue with analysis
        }
    }

    /**
     * Update market prices for all instruments in the portfolio.
     */
    private void updateMarketPrices(String jobId) {
        try {
            Set<String> symbols = portfolioLoader.getAllSymbols(jobId);
            marketPriceUpdater.updatePrices(symbols);
        } catch (Exception e) {
            log.error("Planner: Error updating market prices: {}", e.getMessage(), e);
            // Non-critical - continue with analysis
        }
    }

    private Map<String, Object> errorResponse(int statusCode, String message) {
        return Map.of(
                "statusCode", statusCode,
                "body", Map.of("success", false, "error", message));
    }
}
