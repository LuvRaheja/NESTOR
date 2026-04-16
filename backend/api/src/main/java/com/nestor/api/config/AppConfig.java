package com.nestor.api.config;

import com.nestor.common.db.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AppConfig {

    @Value("${AURORA_CLUSTER_ARN}")
    private String clusterArn;

    @Value("${AURORA_SECRET_ARN}")
    private String secretArn;

    @Value("${DATABASE_NAME:alex}")
    private String databaseName;

    @Value("${DEFAULT_AWS_REGION:us-east-1}")
    private String awsRegion;

    @Value("${SQS_QUEUE_URL:}")
    private String sqsQueueUrl;

    @Bean
    public DataApiClient dataApiClient() {
        return new DataApiClient(clusterArn, secretArn, databaseName, awsRegion);
    }

    @Bean
    public UserRepository userRepository(DataApiClient db) {
        return new UserRepository(db);
    }

    @Bean
    public AccountRepository accountRepository(DataApiClient db) {
        return new AccountRepository(db);
    }

    @Bean
    public PositionRepository positionRepository(DataApiClient db) {
        return new PositionRepository(db);
    }

    @Bean
    public JobRepository jobRepository(DataApiClient db) {
        return new JobRepository(db);
    }

    @Bean
    public InstrumentRepository instrumentRepository(DataApiClient db) {
        return new InstrumentRepository(db);
    }

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Bean
    public String sqsQueueUrl() {
        return sqsQueueUrl;
    }
}
