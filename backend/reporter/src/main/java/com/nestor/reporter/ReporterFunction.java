package com.nestor.reporter;

import com.nestor.common.db.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * Spring Cloud Function bean ({@code reporterFunction}).
 * <p>
 * Receives a Lambda event with {@code job_id}, optional {@code portfolio_data} and {@code user_data},
 * fetches market insights, generates a portfolio analysis report via Bedrock,
 * runs a judge guardrail, saves results to Aurora's {@code report_payload} column,
 * and returns a summary.
 * <p>
 * Expected input:
 * <pre>{@code
 * {
 *   "job_id": "uuid",
 *   "portfolio_data": { "accounts": [...] },
 *   "user_data": { "years_until_retirement": 25, "target_retirement_income": 80000 }
 * }
 * }</pre>
 */
public class ReporterFunction implements Function<Map<String, Object>, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(ReporterFunction.class);

    private final ReportGenerator reportGenerator;
    private final ReportJudge judge;
    private final MarketInsightsClient marketInsightsClient;
    private final JobRepository jobRepository;
    private final double guardScore;

    public ReporterFunction(ReportGenerator reportGenerator, ReportJudge judge,
                            MarketInsightsClient marketInsightsClient,
                            JobRepository jobRepository, double guardScore) {
        this.reportGenerator = reportGenerator;
        this.judge = judge;
        this.marketInsightsClient = marketInsightsClient;
        this.jobRepository = jobRepository;
        this.guardScore = guardScore;
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
                log.warn("Reporter: No portfolio_data in event");
                return errorResponse(400, "portfolio_data is required");
            }

            Map<String, Object> userData = (Map<String, Object>) event.getOrDefault("user_data", Map.of());
            if (userData == null) userData = Map.of();

            log.info("Reporter: Processing job {}", jobId);

            // 1. Calculate portfolio metrics and build summary
            String portfolioSummary = PortfolioFormatter.format(portfolioData, userData);
            log.info("Reporter: Portfolio summary length={}", portfolioSummary.length());

            // 2. Extract symbols and fetch market insights
            List<String> symbols = PortfolioFormatter.extractSymbols(portfolioData);
            String marketInsights = fetchMarketInsights(symbols);

            // 3. Build the full task prompt
            String taskPrompt = buildTaskPrompt(portfolioSummary, marketInsights);

            // 4. Generate report via Bedrock
            String report = reportGenerator.generate(taskPrompt);
            log.info("Reporter: Generated report length={}", report.length());

            // 5. Judge evaluation (guardrail)
            String finalReport = runJudgeGuardrail(report, taskPrompt);

            // 6. Save to database
            Map<String, Object> reportPayload = new LinkedHashMap<>();
            reportPayload.put("content", finalReport);
            reportPayload.put("generated_at", Instant.now().toString());
            reportPayload.put("agent", "reporter");

            boolean saved = false;
            try {
                int rows = jobRepository.updateReport(jobId, reportPayload);
                saved = rows > 0;
                log.info("Reporter: Database update returned: {} rows", rows);
            } catch (Exception e) {
                log.error("Reporter: Database error: {}", e.getMessage(), e);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", saved);
            body.put("message", saved
                    ? "Report generated and stored"
                    : "Report generated but failed to save");
            body.put("final_output", finalReport);

            return Map.of("statusCode", 200, "body", body);

        } catch (Exception e) {
            log.error("Reporter function error: {}", e.getMessage(), e);
            return errorResponse(500, e.getMessage());
        }
    }

    private String fetchMarketInsights(List<String> symbols) {
        try {
            return marketInsightsClient.getInsights(symbols);
        } catch (Exception e) {
            log.warn("Reporter: Could not retrieve market insights: {}", e.getMessage());
            return "Market insights unavailable - proceeding with standard analysis.";
        }
    }

    private String runJudgeGuardrail(String report, String taskPrompt) {
        try {
            ReportJudge.Evaluation evaluation = judge.evaluate(
                    ReporterTemplates.REPORTER_INSTRUCTIONS, taskPrompt, report);
            double score = evaluation.score() / 100.0;
            log.info("Reporter: Judge score={}, feedback={}", score, evaluation.feedback());

            if (score < guardScore) {
                log.error("Reporter: Judge score {} is below guard threshold {}", score, guardScore);
                return "I'm sorry, I'm not able to generate a report for you. Please try again later.";
            }
            return report;
        } catch (Exception e) {
            log.warn("Reporter: Judge evaluation failed: {}. Passing report through.", e.getMessage());
            return report; // If judge fails, pass through the report
        }
    }

    private String buildTaskPrompt(String portfolioSummary, String marketInsights) {
        return String.format("""
                Analyze this investment portfolio and write a comprehensive report.

                %s

                Market Context:
                %s

                Your task:
                1. Analyze the portfolio's current state, strengths, and weaknesses
                2. Generate a detailed, professional analysis report in markdown format

                The report should include:
                - Executive Summary
                - Portfolio Composition Analysis
                - Risk Assessment
                - Diversification Analysis
                - Retirement Readiness (based on user goals)
                - Recommendations
                - Market Context (from insights)

                Provide your complete analysis as the final output in clear markdown format.
                Make the report informative yet accessible to a retail investor.""",
                portfolioSummary, marketInsights);
    }

    private static Map<String, Object> errorResponse(int statusCode, String message) {
        return Map.of("statusCode", statusCode, "body", Map.of("error", message));
    }
}
