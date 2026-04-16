package com.nestor.common.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.rdsdata.model.SqlParameter;
import software.amazon.awssdk.services.rdsdata.model.Field;

import java.math.BigDecimal;
import java.util.*;

/**
 * Repository for the {@code accounts} table.
 */
public class AccountRepository {

    private static final Logger log = LoggerFactory.getLogger(AccountRepository.class);
    private static final String TABLE = "accounts";

    private final DataApiClient db;

    public AccountRepository(DataApiClient db) {
        this.db = db;
    }

    /** Find all accounts for a given Clerk user ID. */
    public List<Map<String, Object>> findByUser(String clerkUserId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE clerk_user_id = :clerk_user_id";
        List<SqlParameter> params = List.of(
                SqlParameter.builder()
                        .name("clerk_user_id")
                        .value(Field.builder().stringValue(clerkUserId).build())
                        .build()
        );
        return db.query(sql, params);
    }

    /** Find an account by its UUID. */
    public Optional<Map<String, Object>> findById(String accountId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE id = :id::uuid";
        List<SqlParameter> params = List.of(
                SqlParameter.builder()
                        .name("id")
                        .value(Field.builder().stringValue(accountId).build())
                        .build()
        );
        return db.queryOne(sql, params);
    }

    /** Create a new account. Returns the generated UUID. */
    public String create(String clerkUserId, String accountName, String accountPurpose, BigDecimal cashBalance) {
        return create(clerkUserId, accountName, accountPurpose, cashBalance, "other");
    }

    /** Create a new account with account type. Returns the generated UUID. */
    public String create(String clerkUserId, String accountName, String accountPurpose, BigDecimal cashBalance, String accountType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("clerk_user_id", clerkUserId);
        data.put("account_name", accountName);
        data.put("account_purpose", accountPurpose);
        data.put("cash_balance", cashBalance != null ? cashBalance : BigDecimal.ZERO);
        data.put("account_type", accountType != null ? accountType : "other");
        return db.insert(TABLE, data, "id");
    }

    /** Update an account by UUID. Returns number of rows affected. */
    public int update(String accountId, Map<String, Object> data) {
        return db.update(TABLE, data, "id = :id::uuid", Map.of("id", accountId));
    }

    /** Delete an account by UUID. Returns number of rows affected. */
    public int delete(String accountId) {
        return db.delete(TABLE, "id = :id::uuid", Map.of("id", accountId));
    }
}
