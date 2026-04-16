package com.nestor.common.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rdsdata.RdsDataClient;
import software.amazon.awssdk.services.rdsdata.model.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Wrapper around AWS RDS Data API (Aurora Serverless v2).
 * Mirrors the Python DataAPIClient used in Alex.
 */
public class DataApiClient {

    private static final Logger log = LoggerFactory.getLogger(DataApiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RdsDataClient rdsDataClient;
    private final String clusterArn;
    private final String secretArn;
    private final String database;

    public DataApiClient(String clusterArn, String secretArn, String database, String region) {
        this.clusterArn = Objects.requireNonNull(clusterArn, "AURORA_CLUSTER_ARN is required");
        this.secretArn = Objects.requireNonNull(secretArn, "AURORA_SECRET_ARN is required");
        this.database = Objects.requireNonNull(database, "DATABASE_NAME is required");
        this.rdsDataClient = RdsDataClient.builder()
                .region(Region.of(region))
                .build();
    }

    /** Execute a raw SQL statement and return the full response. */
    public ExecuteStatementResponse execute(String sql, List<SqlParameter> parameters) {
        var builder = ExecuteStatementRequest.builder()
                .resourceArn(clusterArn)
                .secretArn(secretArn)
                .database(database)
                .sql(sql)
                .includeResultMetadata(true);

        if (parameters != null && !parameters.isEmpty()) {
            builder.parameters(parameters);
        }

        log.debug("Executing SQL: {}", sql);
        return rdsDataClient.executeStatement(builder.build());
    }

    /** Execute a SELECT and return rows as List of Maps. */
    public List<Map<String, Object>> query(String sql, List<SqlParameter> parameters) {
        var response = execute(sql, parameters);
        if (!response.hasRecords() || response.records().isEmpty()) {
            return Collections.emptyList();
        }

        List<ColumnMetadata> columns = response.columnMetadata();
        List<Map<String, Object>> results = new ArrayList<>();

        for (List<Field> record : response.records()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                row.put(columns.get(i).name(), extractValue(record.get(i)));
            }
            results.add(row);
        }
        return results;
    }

    /** Execute a SELECT and return the first row, or empty. */
    public Optional<Map<String, Object>> queryOne(String sql, List<SqlParameter> parameters) {
        List<Map<String, Object>> results = query(sql, parameters);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Insert a row. Returns the value of the RETURNING column, or null. */
    public String insert(String table, Map<String, Object> data, String returning) {
        List<String> columns = new ArrayList<>(data.keySet());
        List<String> placeholders = new ArrayList<>();
        List<SqlParameter> params = new ArrayList<>();

        for (String col : columns) {
            Object value = data.get(col);
            if (value instanceof Map<?, ?> || value instanceof List<?>) {
                placeholders.add(":" + col + "::jsonb");
            } else if (value instanceof BigDecimal) {
                placeholders.add(":" + col + "::numeric");
            } else {
                placeholders.add(":" + col);
            }
            params.add(buildParameter(col, value));
        }

        StringBuilder sql = new StringBuilder()
                .append("INSERT INTO ").append(table)
                .append(" (").append(String.join(", ", columns)).append(")")
                .append(" VALUES (").append(String.join(", ", placeholders)).append(")");

        if (returning != null) {
            sql.append(" RETURNING ").append(returning);
        }

        var response = execute(sql.toString(), params);

        if (returning != null && response.hasRecords() && !response.records().isEmpty()) {
            Object val = extractValue(response.records().get(0).get(0));
            return val != null ? val.toString() : null;
        }
        return null;
    }

    /** Update rows matching the WHERE clause. Returns number of rows affected. */
    public int update(String table, Map<String, Object> data, String where, Map<String, Object> whereParams) {
        List<String> setParts = new ArrayList<>();
        List<SqlParameter> params = new ArrayList<>();

        for (var entry : data.entrySet()) {
            String col = entry.getKey();
            Object val = entry.getValue();
            if (val instanceof Map<?, ?> || val instanceof List<?>) {
                setParts.add(col + " = :" + col + "::jsonb");
            } else if (val instanceof BigDecimal) {
                setParts.add(col + " = :" + col + "::numeric");
            } else {
                setParts.add(col + " = :" + col);
            }
            params.add(buildParameter(col, val));
        }

        if (whereParams != null) {
            for (var entry : whereParams.entrySet()) {
                params.add(buildParameter(entry.getKey(), entry.getValue()));
            }
        }

        String sql = "UPDATE " + table + " SET " + String.join(", ", setParts) + " WHERE " + where;
        var response = execute(sql, params);
        return Math.toIntExact(response.numberOfRecordsUpdated());
    }

    /** Delete rows matching the WHERE clause. Returns number of rows affected. */
    public int delete(String table, String where, Map<String, Object> whereParams) {
        List<SqlParameter> params = new ArrayList<>();
        if (whereParams != null) {
            for (var entry : whereParams.entrySet()) {
                params.add(buildParameter(entry.getKey(), entry.getValue()));
            }
        }
        String sql = "DELETE FROM " + table + " WHERE " + where;
        var response = execute(sql, params);
        return Math.toIntExact(response.numberOfRecordsUpdated());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static final java.util.regex.Pattern UUID_PATTERN =
            java.util.regex.Pattern.compile(
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private SqlParameter buildParameter(String name, Object value) {
        var builder = SqlParameter.builder().name(name);

        if (value == null) {
            builder.value(Field.builder().isNull(true).build());
        } else if (value instanceof UUID uuid) {
            builder.value(Field.builder().stringValue(uuid.toString()).build());
            builder.typeHint(TypeHint.UUID);
        } else if (value instanceof String s) {
            if (UUID_PATTERN.matcher(s).matches()) {
                builder.value(Field.builder().stringValue(s).build());
                builder.typeHint(TypeHint.UUID);
            } else {
                builder.value(Field.builder().stringValue(s).build());
            }
        } else if (value instanceof Long l) {
            builder.value(Field.builder().longValue(l).build());
        } else if (value instanceof Integer i) {
            builder.value(Field.builder().longValue(i.longValue()).build());
        } else if (value instanceof Double d) {
            builder.value(Field.builder().doubleValue(d).build());
        } else if (value instanceof BigDecimal bd) {
            builder.value(Field.builder().stringValue(bd.toPlainString()).build());
        } else if (value instanceof Boolean b) {
            builder.value(Field.builder().booleanValue(b).build());
        } else if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                String json = MAPPER.writeValueAsString(value);
                builder.value(Field.builder().stringValue(json).build());
                builder.typeHint(TypeHint.JSON);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize JSON parameter: " + name, e);
            }
        } else {
            builder.value(Field.builder().stringValue(value.toString()).build());
        }

        return builder.build();
    }

    private Object extractValue(Field field) {
        if (Boolean.TRUE.equals(field.isNull())) {
            return null;
        }
        if (field.stringValue() != null) {
            String s = field.stringValue();
            // Attempt to parse JSONB columns
            if (s.length() > 1 && (s.startsWith("{") || s.startsWith("["))) {
                try {
                    return MAPPER.readValue(s, Object.class);
                } catch (JsonProcessingException ignored) {
                    // Not JSON — return as plain string
                }
            }
            return s;
        }
        if (field.longValue() != null) return field.longValue();
        if (field.doubleValue() != null) return field.doubleValue();
        if (field.booleanValue() != null) return field.booleanValue();
        return null;
    }
}
