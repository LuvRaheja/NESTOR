package com.nestor.charter;

import com.nestor.common.db.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/**
 * Spring Cloud Function bean ({@code charterFunction}).
 * <p>
 * Receives a Lambda event with {@code job_id} and optional {@code portfolio_data},
 * runs the Bedrock-backed chart generator, saves results to Aurora, and returns a summary.
 * <p>
 * Expected input:
 * <pre>{@code
 * {
 *   "job_id": "uuid",
 *   "portfolio_data": { "accounts": [...] }
 * }
 * }</pre>
 */
public class CharterFunction implements Function<Map<String, Object>, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(CharterFunction.class);

    private final ChartGenerator chartGenerator;
    private final JobRepository jobRepository;

    public CharterFunction(ChartGenerator chartGenerator, JobRepository jobRepository) {
        this.chartGenerator = chartGenerator;
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
                log.warn("Charter: No portfolio_data in event, loading from database is not yet implemented");
                return errorResponse(400, "portfolio_data is required");
            }

            log.info("Charter: Processing job {}", jobId);

            // Analyze portfolio
            String analysis = PortfolioAnalyzer.analyze(portfolioData);
            log.info("Charter: Portfolio analysis generated, length: {}", analysis.length());

            // Generate charts via Bedrock
            Map<String, Object> chartsData = chartGenerator.generate(analysis);

            boolean chartsSaved = false;
            if (!chartsData.isEmpty()) {
                try {
                    int rows = jobRepository.updateCharts(jobId, chartsData);
                    chartsSaved = rows > 0;
                    log.info("Charter: Database update returned: {} rows", rows);
                } catch (Exception e) {
                    log.error("Charter: Database error: {}", e.getMessage(), e);
                }
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", chartsSaved);
            body.put("message", chartsSaved
                    ? "Generated " + chartsData.size() + " charts"
                    : "Failed to generate charts");
            body.put("charts_generated", chartsData.size());
            body.put("chart_keys", new ArrayList<>(chartsData.keySet()));

            return Map.of("statusCode", 200, "body", body);

        } catch (Exception e) {
            log.error("Charter function error: {}", e.getMessage(), e);
            return errorResponse(500, e.getMessage());
        }
    }

    private static Map<String, Object> errorResponse(int statusCode, String message) {
        return Map.of("statusCode", statusCode, "body", Map.of("error", message));
    }
}
