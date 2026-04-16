package com.nestor.api.controller;

import com.nestor.common.db.AccountRepository;
import com.nestor.common.db.PositionRepository;
import com.nestor.common.db.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    private final AccountRepository accountRepo;
    private final PositionRepository positionRepo;
    private final UserRepository userRepo;

    public AccountController(AccountRepository accountRepo, PositionRepository positionRepo, UserRepository userRepo) {
        this.accountRepo = accountRepo;
        this.positionRepo = positionRepo;
        this.userRepo = userRepo;
    }

    @GetMapping("/accounts")
    public List<Map<String, Object>> listAccounts(@AuthenticationPrincipal Jwt jwt) {
        return accountRepo.findByUser(jwt.getSubject());
    }

    @PostMapping("/accounts")
    public Map<String, Object> createAccount(@AuthenticationPrincipal Jwt jwt,
                                              @RequestBody Map<String, Object> body) {
        String clerkUserId = jwt.getSubject();

        var user = userRepo.findByClerkId(clerkUserId);
        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        String accountName = (String) body.get("account_name");
        String accountPurpose = (String) body.get("account_purpose");
        String accountType = body.containsKey("account_type")
                ? (String) body.get("account_type")
                : "other";
        BigDecimal cashBalance = body.containsKey("cash_balance")
                ? new BigDecimal(body.get("cash_balance").toString())
                : BigDecimal.ZERO;

        String accountId = accountRepo.create(clerkUserId, accountName, accountPurpose, cashBalance, accountType);
        return accountRepo.findById(accountId).orElseThrow();
    }

    @PutMapping("/accounts/{accountId}")
    public Map<String, Object> updateAccount(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable String accountId,
                                              @RequestBody Map<String, Object> updateData) {
        String clerkUserId = jwt.getSubject();

        var account = accountRepo.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!clerkUserId.equals(account.get("clerk_user_id"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized");
        }

        // Convert cash_balance to BigDecimal if present
        if (updateData.containsKey("cash_balance")) {
            updateData.put("cash_balance", new BigDecimal(updateData.get("cash_balance").toString()));
        }

        accountRepo.update(accountId, updateData);
        return accountRepo.findById(accountId).orElseThrow();
    }

    @DeleteMapping("/accounts/{accountId}")
    public Map<String, String> deleteAccount(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable String accountId) {
        String clerkUserId = jwt.getSubject();

        var account = accountRepo.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!clerkUserId.equals(account.get("clerk_user_id"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized");
        }

        // Delete positions first (foreign key constraint)
        List<Map<String, Object>> positions = positionRepo.findByAccount(accountId);
        for (Map<String, Object> pos : positions) {
            positionRepo.delete(pos.get("id").toString());
        }

        accountRepo.delete(accountId);
        return Map.of("message", "Account deleted successfully");
    }
}
