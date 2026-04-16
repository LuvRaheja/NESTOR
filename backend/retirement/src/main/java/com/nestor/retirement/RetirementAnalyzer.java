package com.nestor.retirement;

import com.nestor.common.bedrock.BedrockConverse;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

import java.time.Duration;

/**
 * Retirement analysis engine — uses Bedrock Converse (plain text mode) to
 * generate a narrative retirement analysis given pre-computed Monte Carlo
 * results and portfolio statistics.
 * <p>
 * This mirrors the Python retirement agent's approach: all heavy computation
 * (Monte Carlo, projections) is done in Java; Bedrock only produces the
 * narrative summary.
 */
public class RetirementAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(RetirementAnalyzer.class);

    private final BedrockConverse bedrock;
    private final Retry retry;

    public RetirementAnalyzer(BedrockConverse bedrock) {
        this.bedrock = bedrock;

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofSeconds(4))
                .retryExceptions(ThrottlingException.class)
                .build();
        this.retry = Retry.of("retirement-bedrock", config);
    }

    /**
     * Generate a narrative retirement analysis using Bedrock.
     *
     * @param taskPrompt the pre-formatted portfolio context with Monte Carlo results
     * @return the AI-generated narrative analysis
     */
    public String analyze(String taskPrompt) {
        log.info("Generating retirement analysis via Bedrock");

        return Retry.decorateSupplier(retry, () ->
                bedrock.converse(RetirementTemplates.RETIREMENT_INSTRUCTIONS, taskPrompt)
        ).get();
    }
}
