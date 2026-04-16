package com.nestor.reporter;

import com.nestor.common.bedrock.BedrockConverse;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

import java.time.Duration;
import java.util.*;

/**
 * Judge evaluation guardrail — uses Bedrock structured output (tool-use trick)
 * to score a generated financial report on quality.
 * <p>
 * Ported from Python {@code judge.py}.
 */
public class ReportJudge {

    private static final Logger log = LoggerFactory.getLogger(ReportJudge.class);

    private static final String TOOL_NAME = "evaluate_report";
    private static final String TOOL_DESC = "Evaluate the quality of a financial report and provide a score";

    private final BedrockConverse bedrock;
    private final Retry retry;

    public ReportJudge(BedrockConverse bedrock) {
        this.bedrock = bedrock;

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(4))
                .retryExceptions(ThrottlingException.class)
                .build();
        this.retry = Retry.of("reporter-judge", config);
    }

    /**
     * Evaluate a generated report.
     *
     * @param originalInstructions the system prompt given to the reporter
     * @param originalTask         the user task prompt
     * @param reportOutput         the generated report
     * @return evaluation with score (0-100) and feedback
     */
    public Evaluation evaluate(String originalInstructions, String originalTask, String reportOutput) {
        String systemPrompt = """
                You are an Evaluation Agent that evaluates the quality of a financial report from a financial planning agent.
                You will be provided with the instructions that were sent to the analyst, and its output, and you must evaluate the quality of the output.
                """;

        String userPrompt = String.format("""
                The financial planning agent was given the following instructions:

                %s

                And it was assigned this task:

                %s

                The financial planning agent's output was:

                %s

                Evaluate this output and respond with your comments and score.""",
                originalInstructions, originalTask, reportOutput);

        Map<String, Object> schema = buildEvaluationSchema();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) Retry.decorateSupplier(retry, () ->
                    bedrock.converseWithStructuredOutput(systemPrompt, userPrompt, TOOL_NAME, TOOL_DESC, schema)
            ).get();

            String feedback = (String) result.getOrDefault("feedback", "No feedback provided");
            double score = result.get("score") instanceof Number n ? n.doubleValue() : 80.0;

            log.info("Judge evaluation: score={}, feedback={}", score, feedback);
            return new Evaluation(feedback, score);
        } catch (Exception e) {
            log.error("Judge evaluation failed: {}", e.getMessage(), e);
            return new Evaluation("Error evaluating financial report: " + e.getMessage(), 80.0);
        }
    }

    private static Map<String, Object> buildEvaluationSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("feedback", "score"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("feedback", Map.of(
                "type", "string",
                "description", "Your feedback on the financial report and rationale for your score"
        ));
        properties.put("score", Map.of(
                "type", "number",
                "description", "Score from 0 to 100 where 0 represents a terrible quality financial report and 100 represents an outstanding financial report"
        ));

        schema.put("properties", properties);
        return schema;
    }

    /**
     * Judge evaluation result.
     */
    public record Evaluation(String feedback, double score) {}
}
