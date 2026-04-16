package com.nestor.charter;

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
 * Spring Boot application and bean configuration for the Charter Lambda.
 * <p>
 * The Lambda handler is
 * {@code org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest},
 * which boots this context and routes to the {@code charterFunction} bean.
 */
@SpringBootApplication
public class CharterConfig {

    public static void main(String[] args) {
        SpringApplication.run(CharterConfig.class, args);
    }

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> charterFunction(
            ChartGenerator chartGenerator,
            JobRepository jobRepository) {
        return new CharterFunction(chartGenerator, jobRepository);
    }

    @Bean
    public ChartGenerator chartGenerator(BedrockConverse bedrockConverse) {
        return new ChartGenerator(bedrockConverse);
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
