package com.nestor.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nestor.common.db.JobRepository;
import com.nestor.common.db.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JobRepository jobRepo;
    private final UserRepository userRepo;
    private final SqsClient sqsClient;
    private final String sqsQueueUrl;

    public AnalysisController(JobRepository jobRepo, UserRepository userRepo, SqsClient sqsClient,
                               @Qualifier("sqsQueueUrl") String sqsQueueUrl) {
        this.jobRepo = jobRepo;
        this.userRepo = userRepo;
        this.sqsClient = sqsClient;
        this.sqsQueueUrl = sqsQueueUrl;
    }

    @PostMapping("/analyze")
    public Map<String, String> triggerAnalysis(@AuthenticationPrincipal Jwt jwt,
                                               @RequestBody(required = false) Map<String, Object> body) {
        String clerkUserId = jwt.getSubject();

        var user = userRepo.findByClerkId(clerkUserId);
        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        String analysisType = "portfolio";
        Map<String, Object> options = Map.of();
        if (body != null) {
            if (body.containsKey("analysis_type")) analysisType = (String) body.get("analysis_type");
            if (body.containsKey("options")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> opts = (Map<String, Object>) body.get("options");
                options = opts;
            }
        }

        Map<String, Object> requestPayload = Map.of("analysis_type", analysisType, "options", options);
        String jobId = jobRepo.createJob(clerkUserId, "portfolio_analysis", requestPayload);

        // Send to SQS
        if (sqsQueueUrl != null && !sqsQueueUrl.isBlank()) {
            try {
                Map<String, Object> message = new LinkedHashMap<>();
                message.put("job_id", jobId);
                message.put("clerk_user_id", clerkUserId);
                message.put("analysis_type", analysisType);
                message.put("options", options);

                sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(sqsQueueUrl)
                        .messageBody(MAPPER.writeValueAsString(message))
                        .build());
                log.info("Sent analysis job to SQS: {}", jobId);
            } catch (Exception e) {
                log.error("Failed to send job to SQS: {}", e.getMessage(), e);
            }
        } else {
            log.warn("SQS_QUEUE_URL not configured, job created but not queued");
        }

        return Map.of("job_id", jobId, "message", "Analysis started. Check job status for results.");
    }

    @GetMapping("/jobs/{jobId}")
    public Map<String, Object> getJobStatus(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable String jobId) {
        String clerkUserId = jwt.getSubject();

        var job = jobRepo.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (!clerkUserId.equals(job.get("clerk_user_id"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized");
        }

        return job;
    }

    @GetMapping("/jobs")
    public Map<String, Object> listJobs(@AuthenticationPrincipal Jwt jwt) {
        String clerkUserId = jwt.getSubject();
        var jobs = jobRepo.findByUser(clerkUserId, 100);
        return Map.of("jobs", jobs);
    }
}
