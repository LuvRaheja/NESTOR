package com.nestor.reporter;

import com.nestor.common.bedrock.BedrockConverse;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

import java.time.Duration;

/**
 * Report generation engine — uses Bedrock Converse (plain text mode) to
 * generate a comprehensive markdown portfolio analysis report.
 */
public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);

    private final BedrockConverse bedrock;
    private final Retry retry;

    public ReportGenerator(BedrockConverse bedrock) {
        this.bedrock = bedrock;

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofSeconds(4))
                .retryExceptions(ThrottlingException.class)
                .build();
        this.retry = Retry.of("reporter-bedrock", config);
    }

    /**
     * Generate a portfolio analysis report.
     *
     * @param taskPrompt the full prompt including portfolio summary and market insights
     * @return the AI-generated markdown report
     */
    public String generate(String taskPrompt) {
        log.info("Generating portfolio report via Bedrock");
        return Retry.decorateSupplier(retry, () ->
                bedrock.converse(ReporterTemplates.REPORTER_INSTRUCTIONS, taskPrompt)
        ).get();
    }
}
