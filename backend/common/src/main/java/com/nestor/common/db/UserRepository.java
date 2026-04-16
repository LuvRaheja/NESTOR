package com.nestor.common.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.rdsdata.model.SqlParameter;
import software.amazon.awssdk.services.rdsdata.model.Field;

import java.util.*;

/**
 * Repository for the {@code users} table.
 */
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);
    private static final String TABLE = "users";

    private final DataApiClient db;

    public UserRepository(DataApiClient db) {
        this.db = db;
    }

    /** Create a new user. Returns the clerk_user_id. */
    public String create(Map<String, Object> data) {
        log.info("Creating user: {}", data.get("clerk_user_id"));
        return db.insert(TABLE, data, "clerk_user_id");
    }

    /** Update a user by clerk_user_id. Returns number of rows affected. */
    public int update(String clerkUserId, Map<String, Object> data) {
        log.info("Updating user: {}", clerkUserId);
        return db.update(TABLE, data, "clerk_user_id = :clerk_user_id", Map.of("clerk_user_id", clerkUserId));
    }

    /** Find a user by their Clerk user ID. */
    public Optional<Map<String, Object>> findByClerkId(String clerkUserId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE clerk_user_id = :clerk_user_id";
        List<SqlParameter> params = List.of(
                SqlParameter.builder()
                        .name("clerk_user_id")
                        .value(Field.builder().stringValue(clerkUserId).build())
                        .build()
        );
        return db.queryOne(sql, params);
    }
}
