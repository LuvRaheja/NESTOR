package com.nestor.planner;

import com.nestor.common.bedrock.BedrockConverse;
import com.nestor.common.db.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.function.Function;

/**
 * Spring Boot application and bean configuration for the Planner Lambda.
 * <p>
 * The Lambda handler is
 * {@code org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest},
 * which boots this context and routes to the {@code plannerFunction} bean.
 */
@SpringBootApplication
public class PlannerConfig {

    public static void main(String[] args) {
        SpringApplication.run(PlannerConfig.class, args);
    }

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> plannerFunction(
            JobRepository jobRepository,
            PortfolioLoader portfolioLoader,
            MarketPriceUpdater marketPriceUpdater,
            LambdaAgentInvoker lambdaAgentInvoker,
            AgentOrchestrator agentOrchestrator) {
        return new PlannerFunction(jobRepository, portfolioLoader, marketPriceUpdater,
                lambdaAgentInvoker, agentOrchestrator);
    }

    @Bean
    public AgentOrchestrator agentOrchestrator(
            @Value("${nestor.bedrock.model-id}") String modelId,
            @Value("${nestor.bedrock.region}") String region,
            LambdaAgentInvoker lambdaAgentInvoker) {
        return new AgentOrchestrator(modelId, region, lambdaAgentInvoker);
    }

    @Bean
    public LambdaAgentInvoker lambdaAgentInvoker(
            @Value("${nestor.planner.lambda-region}") String region,
            @Value("${nestor.planner.tagger-function}") String taggerFunction,
            @Value("${nestor.planner.reporter-function}") String reporterFunction,
            @Value("${nestor.planner.charter-function}") String charterFunction,
            @Value("${nestor.planner.retirement-function}") String retirementFunction) {
        return new LambdaAgentInvoker(region, taggerFunction, reporterFunction,
                charterFunction, retirementFunction);
    }

    @Bean
    public PortfolioLoader portfolioLoader(
            JobRepository jobRepository,
            UserRepository userRepository,
            AccountRepository accountRepository,
            PositionRepository positionRepository,
            InstrumentRepository instrumentRepository) {
        return new PortfolioLoader(jobRepository, userRepository, accountRepository,
                positionRepository, instrumentRepository);
    }

    @Bean
    public MarketPriceUpdater marketPriceUpdater(
            @Value("${nestor.planner.polygon-api-key:}") String polygonApiKey,
            InstrumentRepository instrumentRepository,
            DataApiClient dataApiClient) {
        return new MarketPriceUpdater(polygonApiKey, instrumentRepository, dataApiClient);
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

    @Bean
    public UserRepository userRepository(DataApiClient dataApiClient) {
        return new UserRepository(dataApiClient);
    }

    @Bean
    public AccountRepository accountRepository(DataApiClient dataApiClient) {
        return new AccountRepository(dataApiClient);
    }

    @Bean
    public PositionRepository positionRepository(DataApiClient dataApiClient) {
        return new PositionRepository(dataApiClient);
    }

    @Bean
    public InstrumentRepository instrumentRepository(DataApiClient dataApiClient) {
        return new InstrumentRepository(dataApiClient);
    }
}
