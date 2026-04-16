package com.nestor.planner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.LinkedHashMap;

/**
 * Invokes child Lambda agents (tagger, reporter, charter, retirement).
 */
public class LambdaAgentInvoker {

    private static final Logger log = LoggerFactory.getLogger(LambdaAgentInvoker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LambdaClient lambdaClient;
    private final String taggerFunction;
    private final String reporterFunction;
    private final String charterFunction;
    private final String retirementFunction;

    public LambdaAgentInvoker(String region, String taggerFunction, String reporterFunction,
                              String charterFunction, String retirementFunction) {
        this.lambdaClient = LambdaClient.builder()
                .region(Region.of(region))
                .build();
        this.taggerFunction = taggerFunction;
        this.reporterFunction = reporterFunction;
        this.charterFunction = charterFunction;
        this.retirementFunction = retirementFunction;
    }

    /**
     * Invoke a Lambda function synchronously and return the parsed response.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> invokeLambda(String functionName, Map<String, Object> payload) {
        try {
            String payloadJson = MAPPER.writeValueAsString(payload);
            log.info("Invoking Lambda: {} with payload : {}", functionName, payloadJson);

            InvokeResponse response = lambdaClient.invoke(InvokeRequest.builder()
                    .functionName(functionName)
                    .payload(SdkBytes.fromUtf8String(payloadJson))
                    .build());

            String responsePayload = response.payload().asString(StandardCharsets.UTF_8);
            log.info("Lambda {} returned status={}, payload : {}",
                    functionName, response.statusCode(), responsePayload);

            Map<String, Object> result = MAPPER.readValue(responsePayload, Map.class);

            // Unwrap Lambda response if it has the standard format
            if (result.containsKey("statusCode") && result.containsKey("body")) {
                Object body = result.get("body");
                if (body instanceof String bodyStr) {
                    try {
                        result = MAPPER.readValue(bodyStr, Map.class);
                    } catch (JsonProcessingException e) {
                        result = Map.of("message", bodyStr);
                    }
                } else if (body instanceof Map) {
                    result = (Map<String, Object>) body;
                }
            }

            return result;

        } catch (Exception e) {
            log.error("Error invoking Lambda {}: {}", functionName, e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /** Invoke the tagger Lambda with instruments to classify. */
    public Map<String, Object> invokeTagger(List<Map<String, String>> instruments) {
        return invokeLambda(taggerFunction, Map.of("instruments", instruments));
    }

    /** Invoke the reporter Lambda with portfolio and user data. */
    public Map<String, Object> invokeReporter(String jobId, Map<String, Object> portfolioData, Map<String, Object> userData) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("job_id", jobId);
        payload.put("portfolio_data", portfolioData);
        payload.put("user_data", userData);
        return invokeLambda(reporterFunction, payload);
    }

    /** Invoke the charter Lambda with portfolio data and user data. */
    public Map<String, Object> invokeCharter(String jobId, Map<String, Object> portfolioData, Map<String, Object> userData) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("job_id", jobId);
        payload.put("portfolio_data", portfolioData);
        payload.put("user_data", userData);
        return invokeLambda(charterFunction, payload);
    }

    /** Invoke the retirement Lambda with portfolio data and user data. */
    public Map<String, Object> invokeRetirement(String jobId, Map<String, Object> portfolioData, Map<String, Object> userData) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("job_id", jobId);
        payload.put("portfolio_data", portfolioData);
        payload.put("user_data", userData);
        return invokeLambda(retirementFunction, payload);
    }

    public String getTaggerFunction() { return taggerFunction; }
    public String getReporterFunction() { return reporterFunction; }
    public String getCharterFunction() { return charterFunction; }
    public String getRetirementFunction() { return retirementFunction; }
}
