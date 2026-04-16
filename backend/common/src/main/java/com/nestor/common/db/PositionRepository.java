package com.nestor.common.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.rdsdata.model.SqlParameter;
import software.amazon.awssdk.services.rdsdata.model.Field;

import java.math.BigDecimal;
import java.util.*;

/**
 * Repository for the {@code positions} table.
 */
public class PositionRepository {

    private static final Logger log = LoggerFactory.getLogger(PositionRepository.class);
    private static final String TABLE = "positions";

    private final DataApiClient db;

    public PositionRepository(DataApiClient db) {
        this.db = db;
    }

    /** Find all positions for a given account ID. */
    public List<Map<String, Object>> findByAccount(String accountId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE account_id = :account_id::uuid";
        List<SqlParameter> params = List.of(
                SqlParameter.builder()
                        .name("account_id")
                        .value(Field.builder().stringValue(accountId).build())
                        .build()
        );
        return db.query(sql, params);
    }

    /** Find a position by its UUID. */
    public Optional<Map<String, Object>> findById(String positionId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE id = :id::uuid";
        List<SqlParameter> params = List.of(
                SqlParameter.builder()
                        .name("id")
                        .value(Field.builder().stringValue(positionId).build())
                        .build()
        );
        return db.queryOne(sql, params);
    }

    /** Add a new position. Returns the generated UUID. */
    public String add(String accountId, String symbol, BigDecimal quantity) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("account_id", accountId);
        data.put("symbol", symbol);
        data.put("quantity", quantity);
        return db.insert(TABLE, data, "id");
    }

    /** Update a position by UUID. Returns number of rows affected. */
    public int update(String positionId, Map<String, Object> data) {
        return db.update(TABLE, data, "id = :id::uuid", Map.of("id", positionId));
    }

    /** Delete a position by UUID. Returns number of rows affected. */
    public int delete(String positionId) {
        return db.delete(TABLE, "id = :id::uuid", Map.of("id", positionId));
    }
}
