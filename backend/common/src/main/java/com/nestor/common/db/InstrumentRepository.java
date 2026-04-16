package com.nestor.common.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.rdsdata.model.SqlParameter;
import software.amazon.awssdk.services.rdsdata.model.Field;

import java.util.*;

/**
 * Repository for the {@code instruments} table.
 * Mirrors the Python {@code Instruments} model in Alex.
 */
public class InstrumentRepository {

    private static final Logger log = LoggerFactory.getLogger(InstrumentRepository.class);
    private static final String TABLE = "instruments";

    private final DataApiClient db;

    public InstrumentRepository(DataApiClient db) {
        this.db = db;
    }

    /** Find an instrument by its ticker symbol. */
    public Optional<Map<String, Object>> findBySymbol(String symbol) {
        String sql = "SELECT * FROM " + TABLE + " WHERE symbol = :symbol";
        List<SqlParameter> params = List.of(
                SqlParameter.builder()
                        .name("symbol")
                        .value(Field.builder().stringValue(symbol).build())
                        .build()
        );
        return db.queryOne(sql, params);
    }

    /**
     * Create a new instrument row.
     *
     * @param data map containing: symbol, name, instrument_type, current_price,
     *             allocation_asset_class, allocation_regions, allocation_sectors
     * @return the inserted symbol
     */
    public String createInstrument(Map<String, Object> data) {
        log.info("Creating instrument: {}", data.get("symbol"));
        return db.insert(TABLE, data, "symbol");
    }

    /**
     * Update an existing instrument by symbol.
     *
     * @param symbol the ticker symbol (WHERE key)
     * @param data   columns to update (should NOT include symbol)
     * @return number of rows affected
     */
    public int updateInstrument(String symbol, Map<String, Object> data) {
        log.info("Updating instrument: {}", symbol);
        return db.update(TABLE, data, "symbol = :symbol", Map.of("symbol", symbol));
    }

    /** Find all instruments. */
    public List<Map<String, Object>> findAll() {
        return db.query("SELECT * FROM " + TABLE + " ORDER BY symbol", null);
    }
}
