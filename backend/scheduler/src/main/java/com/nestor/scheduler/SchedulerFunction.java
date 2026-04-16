package com.nestor.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * Lightweight bridge between EventBridge and the App Runner Researcher service.
 * <p>
 * Reads the App Runner URL, normalizes it, and POSTs an empty JSON body
 * to the {@code /research} endpoint. The Researcher agent picks a trending
 * topic autonomously.
 */
public class SchedulerFunction implements Function<Map<String, Object>, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(SchedulerFunction.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(180);

    private final String appRunnerUrl;
    private final HttpClient httpClient;

    public SchedulerFunction(String appRunnerUrl) {
        this.appRunnerUrl = appRunnerUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    @Override
    public Map<String, Object> apply(Map<String, Object> event) {
        if (appRunnerUrl == null || appRunnerUrl.isBlank()) {
            log.error("APP_RUNNER_URL environment variable not set");
            return Map.of(
                    "statusCode", 500,
                    "body", Map.of("error", "APP_RUNNER_URL environment variable not set")
            );
        }

        String url = normalizeUrl(appRunnerUrl);
        log.info("Triggering research at: {}", url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(HTTP_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String result = response.body();
            log.info("Research triggered successfully: {}", result);

            return Map.of(
                    "statusCode", 200,
                    "body", Map.of(
                            "message", "Research triggered successfully",
                            "result", result
                    )
            );
        } catch (Exception e) {
            log.error("Error triggering research: {}", e.getMessage(), e);
            return Map.of(
                    "statusCode", 500,
                    "body", Map.of("error", e.getMessage())
            );
        }
    }

    /**
     * Strips any protocol prefix, then builds {@code https://{host}/research}.
     */
    static String normalizeUrl(String raw) {
        String host = raw;
        if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        } else if (host.startsWith("http://")) {
            host = host.substring("http://".length());
        }
        // Remove trailing slash if present
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return "https://" + host + "/research";
    }
}
