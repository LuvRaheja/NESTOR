package com.nestor.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.function.Function;

/**
 * Spring Boot application and bean configuration for the Ingest Lambda.
 * <p>
 * The Lambda handler is
 * {@code org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest},
 * which boots this context and routes to the {@code ingestFunction} bean.
 */
@SpringBootApplication
public class IngestConfig {

    public static void main(String[] args) {
        SpringApplication.run(IngestConfig.class, args);
    }

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> ingestFunction(
            EmbeddingClient embeddingClient,
            S3VectorsClient s3VectorsClient) {
        return new IngestFunction(embeddingClient, s3VectorsClient);
    }

    @Bean
    public EmbeddingClient embeddingClient(
            @Value("${nestor.sagemaker.endpoint}") String sagemakerEndpoint,
            @Value("${nestor.sagemaker.region}") String region) {
        return new EmbeddingClient(sagemakerEndpoint, region);
    }

    @Bean
    public S3VectorsClient s3VectorsClient(
            @Value("${nestor.vectors.bucket}") String vectorBucket,
            @Value("${nestor.vectors.index-name}") String indexName,
            @Value("${nestor.sagemaker.region}") String region) {
        return new S3VectorsClient(vectorBucket, indexName, region);
    }
}
