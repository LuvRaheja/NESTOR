package com.nestor.api.controller;

import com.nestor.common.db.AccountRepository;
import com.nestor.common.db.InstrumentRepository;
import com.nestor.common.db.PositionRepository;
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
public class PositionController {

    private static final Logger log = LoggerFactory.getLogger(PositionController.class);
    private final PositionRepository positionRepo;
    private final AccountRepository accountRepo;
    private final InstrumentRepository instrumentRepo;

    public PositionController(PositionRepository positionRepo, AccountRepository accountRepo,
                               InstrumentRepository instrumentRepo) {
        this.positionRepo = positionRepo;
        this.accountRepo = accountRepo;
        this.instrumentRepo = instrumentRepo;
    }

    @GetMapping("/accounts/{accountId}/positions")
    public Map<String, Object> listPositions(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable String accountId) {
        String clerkUserId = jwt.getSubject();

        var account = accountRepo.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!clerkUserId.equals(account.get("clerk_user_id"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized");
        }

        List<Map<String, Object>> positions = positionRepo.findByAccount(accountId);
        List<Map<String, Object>> formatted = new ArrayList<>();
        for (Map<String, Object> pos : positions) {
            Map<String, Object> enriched = new LinkedHashMap<>(pos);
            instrumentRepo.findBySymbol((String) pos.get("symbol"))
                    .ifPresent(inst -> enriched.put("instrument", inst));
            formatted.add(enriched);
        }

        return Map.of("positions", formatted);
    }

    @PostMapping("/positions")
    public Map<String, Object> createPosition(@AuthenticationPrincipal Jwt jwt,
                                               @RequestBody Map<String, Object> body) {
        String clerkUserId = jwt.getSubject();
        String accountId = (String) body.get("account_id");
        String symbol = ((String) body.get("symbol")).toUpperCase();
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());

        var account = accountRepo.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!clerkUserId.equals(account.get("clerk_user_id"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized");
        }

        // Ensure instrument exists
        if (instrumentRepo.findBySymbol(symbol).isEmpty()) {
            log.info("Creating new instrument: {}", symbol);
            String instrumentType = (symbol.length() <= 5 && symbol.chars().allMatch(Character::isLetter))
                    ? "stock" : "etf";
            Map<String, Object> instrData = new LinkedHashMap<>();
            instrData.put("symbol", symbol);
            instrData.put("name", symbol + " - User Added");
            instrData.put("instrument_type", instrumentType);
            instrData.put("current_price", BigDecimal.ZERO);
            instrData.put("allocation_regions", Map.of("north_america", 100.0));
            instrData.put("allocation_sectors", Map.of("other", 100.0));
            instrData.put("allocation_asset_class",
                    "stock".equals(instrumentType) ? Map.of("equity", 100.0) : Map.of("fixed_income", 100.0));
            instrumentRepo.createInstrument(instrData);
        }

        String positionId = positionRepo.add(accountId, symbol, quantity);
        return positionRepo.findById(positionId).orElseThrow();
    }

    @PutMapping("/positions/{positionId}")
    public Map<String, Object> updatePosition(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable String positionId,
                                               @RequestBody Map<String, Object> updateData) {
        String clerkUserId = jwt.getSubject();

        var position = positionRepo.findById(positionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Position not found"));

        var account = accountRepo.findById(position.get("account_id").toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!clerkUserId.equals(account.get("clerk_user_id"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized");
        }

        // Convert quantity to BigDecimal if present
        if (updateData.containsKey("quantity")) {
            updateData.put("quantity", new BigDecimal(updateData.get("quantity").toString()));
        }

        positionRepo.update(positionId, updateData);
        return positionRepo.findById(positionId).orElseThrow();
    }

    @DeleteMapping("/positions/{positionId}")
    public Map<String, String> deletePosition(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable String positionId) {
        String clerkUserId = jwt.getSubject();

        var position = positionRepo.findById(positionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Position not found"));

        var account = accountRepo.findById(position.get("account_id").toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!clerkUserId.equals(account.get("clerk_user_id"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized");
        }

        positionRepo.delete(positionId);
        return Map.of("message", "Position deleted");
    }
}
