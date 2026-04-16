package com.nestor.api.controller;

import com.nestor.common.db.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api")
public class TestDataController {

    private static final Logger log = LoggerFactory.getLogger(TestDataController.class);
    private final UserRepository userRepo;
    private final AccountRepository accountRepo;
    private final PositionRepository positionRepo;
    private final InstrumentRepository instrumentRepo;

    public TestDataController(UserRepository userRepo, AccountRepository accountRepo,
                               PositionRepository positionRepo, InstrumentRepository instrumentRepo) {
        this.userRepo = userRepo;
        this.accountRepo = accountRepo;
        this.positionRepo = positionRepo;
        this.instrumentRepo = instrumentRepo;
    }

    @DeleteMapping("/reset-accounts")
    public Map<String, Object> resetAccounts(@AuthenticationPrincipal Jwt jwt) {
        String clerkUserId = jwt.getSubject();

        var user = userRepo.findByClerkId(clerkUserId);
        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        List<Map<String, Object>> accounts = accountRepo.findByUser(clerkUserId);
        int deleted = 0;
        for (Map<String, Object> account : accounts) {
            String accountId = account.get("id").toString();
            // Positions cascade via DB, but delete explicitly for safety
            for (Map<String, Object> pos : positionRepo.findByAccount(accountId)) {
                positionRepo.delete(pos.get("id").toString());
            }
            accountRepo.delete(accountId);
            deleted++;
        }

        return Map.of("message", "Deleted " + deleted + " account(s)", "accounts_deleted", deleted);
    }

    @PostMapping("/populate-test-data")
    public Map<String, Object> populateTestData(@AuthenticationPrincipal Jwt jwt) {
        String clerkUserId = jwt.getSubject();

        var user = userRepo.findByClerkId(clerkUserId);
        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        // Ensure key instruments exist (Indian and US)
        Map<String, Map<String, Object>> stockDefs = new LinkedHashMap<>();
        // US stocks (backward compatible)
        stockDefs.put("AAPL", Map.of("name", "Apple Inc.", "type", "stock", "price", 195.89,
                "regions", Map.of("north_america", 100), "sectors", Map.of("technology", 100),
                "asset_class", Map.of("equity", 100)));
        stockDefs.put("AMZN", Map.of("name", "Amazon.com Inc.", "type", "stock", "price", 178.35,
                "regions", Map.of("north_america", 100), "sectors", Map.of("consumer_discretionary", 100),
                "asset_class", Map.of("equity", 100)));
        stockDefs.put("NVDA", Map.of("name", "NVIDIA Corporation", "type", "stock", "price", 522.74,
                "regions", Map.of("north_america", 100), "sectors", Map.of("technology", 100),
                "asset_class", Map.of("equity", 100)));
        stockDefs.put("MSFT", Map.of("name", "Microsoft Corporation", "type", "stock", "price", 430.82,
                "regions", Map.of("north_america", 100), "sectors", Map.of("technology", 100),
                "asset_class", Map.of("equity", 100)));
        stockDefs.put("GOOGL", Map.of("name", "Alphabet Inc. Class A", "type", "stock", "price", 173.69,
                "regions", Map.of("north_america", 100), "sectors", Map.of("technology", 100),
                "asset_class", Map.of("equity", 100)));
        // Indian instruments
        stockDefs.put("NIFTYBEES", Map.of("name", "Nippon India Nifty 50 BeES ETF", "type", "etf", "price", 265.0,
                "regions", Map.of("asia", 100), "sectors", Map.of("diversified", 100),
                "asset_class", Map.of("equity", 100)));
        stockDefs.put("JUNIORBEES", Map.of("name", "Nippon India Nifty Next 50 Junior BeES ETF", "type", "etf", "price", 72.0,
                "regions", Map.of("asia", 100), "sectors", Map.of("diversified", 100),
                "asset_class", Map.of("equity", 100)));
        stockDefs.put("LIQUIDBEES", Map.of("name", "Nippon India Liquid BeES ETF", "type", "etf", "price", 1000.0,
                "regions", Map.of("asia", 100), "sectors", Map.of("treasury", 100),
                "asset_class", Map.of("cash", 100)));
        stockDefs.put("GOLDBEES", Map.of("name", "Nippon India Gold BeES ETF", "type", "etf", "price", 62.0,
                "regions", Map.of("global", 100), "sectors", Map.of("commodities", 100),
                "asset_class", Map.of("commodities", 100)));
        stockDefs.put("CPSEETF", Map.of("name", "Nippon India CPSE ETF", "type", "etf", "price", 85.0,
                "regions", Map.of("asia", 100), "sectors", Map.of("energy", 30, "financials", 25, "industrials", 25, "materials", 20),
                "asset_class", Map.of("equity", 100)));
        stockDefs.put("HDFCNIFETF", Map.of("name", "HDFC Nifty 50 ETF", "type", "etf", "price", 252.0,
                "regions", Map.of("asia", 100), "sectors", Map.of("diversified", 100),
                "asset_class", Map.of("equity", 100)));

        for (var entry : stockDefs.entrySet()) {
            String symbol = entry.getKey();
            if (instrumentRepo.findBySymbol(symbol).isEmpty()) {
                Map<String, Object> info = entry.getValue();
                Map<String, Object> instrData = new LinkedHashMap<>();
                instrData.put("symbol", symbol);
                instrData.put("name", info.get("name"));
                instrData.put("instrument_type", info.get("type"));
                instrData.put("current_price", new BigDecimal(info.get("price").toString()));
                instrData.put("allocation_regions", info.get("regions"));
                instrData.put("allocation_sectors", info.get("sectors"));
                instrData.put("allocation_asset_class", info.get("asset_class"));
                instrumentRepo.createInstrument(instrData);
                log.info("Added missing instrument: {}", symbol);
            }
        }

        // Create test accounts (Indian portfolio examples)
        List<Map<String, Object>> accountsDef = List.of(
            Map.of("name", "EPF Account",
                    "type", "indian_epf",
                    "purpose", "Employee Provident Fund – mandatory retirement savings",
                    "cash", 50000.00,
                    "positions", List.of(
                        List.of("NIFTYBEES", 500), List.of("LIQUIDBEES", 20)
                    )),
            Map.of("name", "NPS Tier-1",
                    "type", "indian_nps",
                    "purpose", "National Pension System – long-term retirement corpus",
                    "cash", 25000.00,
                    "positions", List.of(
                        List.of("NIFTYBEES", 300), List.of("JUNIORBEES", 200),
                        List.of("HDFCNIFETF", 150), List.of("CPSEETF", 100)
                    )),
            Map.of("name", "Mutual Fund Portfolio",
                    "type", "indian_mutual_fund",
                    "purpose", "ELSS and diversified equity mutual funds for wealth creation",
                    "cash", 100000.00,
                    "positions", List.of(
                        List.of("NIFTYBEES", 400), List.of("JUNIORBEES", 300),
                        List.of("GOLDBEES", 500), List.of("HDFCNIFETF", 200)
                    )),
            Map.of("name", "Direct Equity",
                    "type", "indian_equity",
                    "purpose", "Direct stock investments in Indian and global markets",
                    "cash", 200000.00,
                    "positions", List.of(
                        List.of("AAPL", 10), List.of("NVDA", 5),
                        List.of("MSFT", 8), List.of("GOOGL", 6)
                    ))
        );

        List<String> createdAccountIds = new ArrayList<>();
        for (Map<String, Object> acctDef : accountsDef) {
            String accountId = accountRepo.create(
                    clerkUserId,
                    (String) acctDef.get("name"),
                    (String) acctDef.get("purpose"),
                    new BigDecimal(acctDef.get("cash").toString()),
                    (String) acctDef.getOrDefault("type", "other")
            );

            @SuppressWarnings("unchecked")
            List<List<Object>> positions = (List<List<Object>>) acctDef.get("positions");
            for (List<Object> posData : positions) {
                String symbol = (String) posData.get(0);
                BigDecimal qty = new BigDecimal(posData.get(1).toString());
                try {
                    positionRepo.add(accountId, symbol, qty);
                } catch (Exception e) {
                    log.warn("Could not add position {}: {}", symbol, e.getMessage());
                }
            }
            createdAccountIds.add(accountId);
        }

        // Build response with account details
        List<Map<String, Object>> allAccounts = new ArrayList<>();
        for (String accountId : createdAccountIds) {
            accountRepo.findById(accountId).ifPresent(account -> {
                account.put("positions", positionRepo.findByAccount(accountId));
                allAccounts.add(account);
            });
        }

        return Map.of(
            "message", "Test data populated successfully",
            "accounts_created", createdAccountIds.size(),
            "accounts", allAccounts
        );
    }
}
