package com.nestor.tagger;

import com.nestor.common.bedrock.BedrockConverse;
import com.nestor.common.db.DataApiClient;
import com.nestor.common.db.InstrumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.function.Function;

/**
 * Spring Boot application and bean configuration for the Tagger Lambda.
 * <p>
 * The Lambda handler is
 * {@code org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest},
 * which boots this context and routes to the {@code taggerFunction} bean.
 */
@SpringBootApplication
public class TaggerConfig {

    public static void main(String[] args) {
        SpringApplication.run(TaggerConfig.class, args);
    }

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> taggerFunction(
            InstrumentClassifier classifier,
            InstrumentRepository instrumentRepository) {
        return new TaggerFunction(classifier, instrumentRepository);
    }

    @Bean
    public InstrumentClassifier instrumentClassifier(BedrockConverse bedrockConverse) {
        return new InstrumentClassifier(bedrockConverse);
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
    public InstrumentRepository instrumentRepository(DataApiClient dataApiClient) {
        return new InstrumentRepository(dataApiClient);
    }
}
