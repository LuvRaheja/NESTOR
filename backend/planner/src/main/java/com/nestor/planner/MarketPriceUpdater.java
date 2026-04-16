package com.nestor.planner;

import com.nestor.common.db.DataApiClient;
import com.nestor.common.db.InstrumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fetches current market prices from Polygon.io and updates the instruments table.
 * Falls back to random prices if no API key is available.
 */
public class MarketPriceUpdater {

    private static final Logger log = LoggerFactory.getLogger(MarketPriceUpdater.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String polygonApiKey;
    private final InstrumentRepository instrumentRepository;
    private final DataApiClient dataApiClient;

    public MarketPriceUpdater(String polygonApiKey, InstrumentRepository instrumentRepository,
                              DataApiClient dataApiClient) {
        this.polygonApiKey = polygonApiKey;
        this.instrumentRepository = instrumentRepository;
        this.dataApiClient = dataApiClient;
    }

    /**
     * Update prices for all symbols in the portfolio.
     */
    public void updatePrices(Set<String> symbols) {
        if (symbols.isEmpty()) {
            log.info("MarketPriceUpdater: No symbols to update");
            return;
        }

        log.info("MarketPriceUpdater: Updating prices for {} symbols", symbols.size());

        for (String symbol : symbols) {
            try {
                double price = fetchPrice(symbol);
                if (price > 0) {
                    Map<String, Object> data = Map.of("current_price", price);
                    dataApiClient.update("instruments", data,
                            "symbol = :symbol", Map.of("symbol", symbol));
                    log.info("MarketPriceUpdater: Updated {} price to ${}", symbol, String.format("%.2f", price));
                }
            } catch (Exception e) {
                log.warn("MarketPriceUpdater: Error updating price for {}: {}", symbol, e.getMessage());
            }
        }
    }

    /**
     * Fetch the price for a single symbol. Uses Polygon.io if API key is available,
     * otherwise falls back to a random price.
     */
    private double fetchPrice(String symbol) {
        if (polygonApiKey != null && !polygonApiKey.isBlank()) {
            try {
                return fetchPriceFromPolygon(symbol);
            } catch (Exception e) {
                log.warn("MarketPriceUpdater: Polygon failed for {}: {}", symbol, e.getMessage());
            }
        }
        // Fallback: random price between 1 and 500
        return ThreadLocalRandom.current().nextDouble(1.0, 500.0);
    }

    /**
     * Fetch the previous close price from Polygon.io.
     */
    private double fetchPriceFromPolygon(String symbol) throws Exception {
        String url = String.format(
                "https://api.polygon.io/v2/aggs/ticker/%s/prev?adjusted=true&apiKey=%s",
                symbol, polygonApiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Polygon API returned status " + response.statusCode());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode results = root.get("results");
        if (results != null && results.isArray() && !results.isEmpty()) {
            return results.get(0).get("c").asDouble(); // "c" = close price
        }

        throw new RuntimeException("No results for " + symbol);
    }
}
