package com.nestor.common.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.rdsdata.model.SqlParameter;
import software.amazon.awssdk.services.rdsdata.model.Field;
import software.amazon.awssdk.services.rdsdata.model.TypeHint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Repository for the {@code jobs} table.
 */
public class JobRepository {

    private static final Logger log = LoggerFactory.getLogger(JobRepository.class);
    private static final String TABLE = "jobs";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataApiClient db;

    public JobRepository(DataApiClient db) {
        this.db = db;
    }

    /** Get the underlying DataApiClient. */
    public DataApiClient getDb() {
        return db;
    }

    /** Find a job by its UUID. */
    public Optional<Map<String, Object>> findById(String jobId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE id = :id::uuid";
        List<SqlParameter> params = List.of(
                SqlParameter.builder()
                        .name("id")
                        .value(Field.builder().stringValue(jobId).build())
                        .build()
        );
        return db.queryOne(sql, params);
    }

    /** Create a new job. Returns the generated UUID. */
    public String createJob(String clerkUserId, String jobType, Map<String, Object> requestPayload) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("clerk_user_id", clerkUserId);
        data.put("job_type", jobType);
        data.put("status", "pending");
        if (requestPayload != null) {
            data.put("request_payload", requestPayload);
        }
        return db.insert(TABLE, data, "id");
    }

    /** Find jobs by user, ordered by created_at DESC, limited. */
    public List<Map<String, Object>> findByUser(String clerkUserId, int limit) {
        String sql = "SELECT * FROM " + TABLE + " WHERE clerk_user_id = :clerk_user_id ORDER BY created_at DESC LIMIT " + limit;
        List<SqlParameter> params = List.of(
                SqlParameter.builder()
                        .name("clerk_user_id")
                        .value(Field.builder().stringValue(clerkUserId).build())
                        .build()
        );
        return db.query(sql, params);
    }

    /**
     * Update the charts_payload JSONB column for a job.
     *
     * @param jobId         the job UUID
     * @param chartsPayload the charts data map
     * @return number of rows affected
     */
    public int updateCharts(String jobId, Map<String, Object> chartsPayload) {
        log.info("Updating charts for job: {}", jobId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("charts_payload", chartsPayload);
        return db.update(TABLE, data, "id = :id::uuid", Map.of("id", jobId));
    }

    /**
     * Update the retirement_payload JSONB column for a job.
     *
     * @param jobId             the job UUID
     * @param retirementPayload the retirement analysis data map
     * @return number of rows affected
     */
    public int updateRetirement(String jobId, Map<String, Object> retirementPayload) {
        log.info("Updating retirement payload for job: {}", jobId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("retirement_payload", retirementPayload);
        return db.update(TABLE, data, "id = :id::uuid", Map.of("id", jobId));
    }

    /**
     * Update the report_payload JSONB column for a job.
     *
     * @param jobId         the job UUID
     * @param reportPayload the report data map
     * @return number of rows affected
     */
    public int updateReport(String jobId, Map<String, Object> reportPayload) {
        log.info("Updating report payload for job: {}", jobId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("report_payload", reportPayload);
        return db.update(TABLE, data, "id = :id::uuid", Map.of("id", jobId));
    }

    /**
     * Update the status of a job.
     *
     * @param jobId  the job UUID
     * @param status the new status (pending, running, completed, failed)
     * @return number of rows affected
     */
    public int updateStatus(String jobId, String status) {
        log.info("Updating job {} status to: {}", jobId, status);
        String timestampClause = "";
        if ("running".equals(status)) {
            timestampClause = ", started_at = NOW()";
        } else if ("completed".equals(status) || "failed".equals(status)) {
            timestampClause = ", completed_at = NOW()";
        }
        String sql = "UPDATE " + TABLE + " SET status = :status" + timestampClause
                + " WHERE id = :id::uuid";
        List<SqlParameter> params = List.of(
                SqlParameter.builder().name("status")
                        .value(Field.builder().stringValue(status).build()).build(),
                SqlParameter.builder().name("id")
                        .value(Field.builder().stringValue(jobId).build()).build()
        );
        var response = db.execute(sql, params);
        return Math.toIntExact(response.numberOfRecordsUpdated());
    }

    /**
     * Update the status of a job with an error message.
     *
     * @param jobId        the job UUID
     * @param status       the new status
     * @param errorMessage the error message
     * @return number of rows affected
     */
    public int updateStatus(String jobId, String status, String errorMessage) {
        log.info("Updating job {} status to: {} with error: {}", jobId, status, errorMessage);
        String sql = "UPDATE " + TABLE + " SET status = :status, error_message = :error_message, completed_at = NOW()"
                + " WHERE id = :id::uuid";
        List<SqlParameter> params = List.of(
                SqlParameter.builder().name("status")
                        .value(Field.builder().stringValue(status).build()).build(),
                SqlParameter.builder().name("error_message")
                        .value(Field.builder().stringValue(errorMessage).build()).build(),
                SqlParameter.builder().name("id")
                        .value(Field.builder().stringValue(jobId).build()).build()
        );
        var response = db.execute(sql, params);
        return Math.toIntExact(response.numberOfRecordsUpdated());
    }
}
