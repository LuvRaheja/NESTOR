package com.nestor.reporter;

import com.nestor.common.bedrock.BedrockConverse;
import com.nestor.common.db.DataApiClient;
import com.nestor.common.db.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.function.Function;

/**
 * Spring Boot application and bean configuration for the Reporter Lambda.
 * <p>
 * The Lambda handler is
 * {@code org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest},
 * which boots this context and routes to the {@code reporterFunction} bean.
 */
@SpringBootApplication
public class ReporterConfig {

    public static void main(String[] args) {
        SpringApplication.run(ReporterConfig.class, args);
    }

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> reporterFunction(
            ReportGenerator reportGenerator,
            ReportJudge reportJudge,
            MarketInsightsClient marketInsightsClient,
            JobRepository jobRepository,
            @Value("${nestor.reporter.guard-score:0.3}") double guardScore) {
        return new ReporterFunction(reportGenerator, reportJudge, marketInsightsClient,
                jobRepository, guardScore);
    }

    @Bean
    public ReportGenerator reportGenerator(BedrockConverse bedrockConverse) {
        return new ReportGenerator(bedrockConverse);
    }

    @Bean
    public ReportJudge reportJudge(BedrockConverse bedrockConverse) {
        return new ReportJudge(bedrockConverse);
    }

    @Bean
    public MarketInsightsClient marketInsightsClient(
            @Value("${nestor.sagemaker.endpoint}") String sagemakerEndpoint,
            @Value("${nestor.sagemaker.region}") String sagemakerRegion) {
        return new MarketInsightsClient(sagemakerEndpoint, sagemakerRegion);
    }

    @Bean
    public BedrockConverse bedrockConverse(
            @Value("${nestor.bedrock.model-id}") String modelId,
            @Value("${nestor.bedrock.region}") String region) {
        return new BedrockConverse(modelId, region);
    }

    @Bean
    public DataApiClient dataApiClient(
            @Value("${nestor.aurora.cluster-arn}") String clusterArn,
            @Value("${nestor.aurora.secret-arn}") String secretArn,
            @Value("${nestor.aurora.database}") String database,
            @Value("${nestor.aurora.region}") String region) {
        return new DataApiClient(clusterArn, secretArn, database, region);
    }

    @Bean
    public JobRepository jobRepository(DataApiClient dataApiClient) {
        return new JobRepository(dataApiClient);
    }
}
