package com.nestor.api.controller;

import com.nestor.common.db.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserRepository userRepo;

    public UserController(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping("/user")
    public Map<String, Object> getOrCreateUser(@AuthenticationPrincipal Jwt jwt) {
        String clerkUserId = jwt.getSubject();
        log.info("Authenticated user: {}", clerkUserId);

        var existing = userRepo.findByClerkId(clerkUserId);
        if (existing.isPresent()) {
            return Map.of("user", existing.get(), "created", false);
        }

        // Create new user with defaults
        String displayName = jwt.getClaimAsString("name");
        if (displayName == null || displayName.isBlank()) {
            String email = jwt.getClaimAsString("email");
            displayName = (email != null && email.contains("@")) ? email.split("@")[0] : "New User";
        }

        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("clerk_user_id", clerkUserId);
        userData.put("display_name", displayName);
        userData.put("years_until_retirement", 20);
        userData.put("target_retirement_income", 1200000.0);
        userData.put("asset_class_targets", Map.of("equity", 70, "fixed_income", 30));
        userData.put("region_targets", Map.of("india", 50, "international", 50));
        userData.put("country_code", "IN");
        userData.put("currency_code", "INR");
        userData.put("tax_regime_preference", "new");
        userData.put("city_tier", "tier_1");
        userData.put("healthcare_preference", "mixed");
        userData.put("fixed_income_preference", 50);
        userData.put("gold_preference", 20);
        userData.put("guaranteed_income_priority", 50);

        userRepo.create(userData);
        var created = userRepo.findByClerkId(clerkUserId);
        log.info("Created new user: {}", clerkUserId);

        return Map.of("user", created.orElse(userData), "created", true);
    }

    @PutMapping("/user")
    public Map<String, Object> updateUser(@AuthenticationPrincipal Jwt jwt,
                                           @RequestBody Map<String, Object> updateData) {
        String clerkUserId = jwt.getSubject();

        var user = userRepo.findByClerkId(clerkUserId);
        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        userRepo.update(clerkUserId, updateData);
        return userRepo.findByClerkId(clerkUserId).orElseThrow();
    }
}
