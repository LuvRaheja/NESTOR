package com.nestor.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.function.Function;

/**
 * Spring Boot application and bean configuration for the Scheduler Lambda.
 * <p>
 * The Lambda handler is
 * {@code org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest},
 * which boots this context and routes to the {@code schedulerFunction} bean.
 */
@SpringBootApplication
public class SchedulerConfig {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerConfig.class, args);
    }

    @Bean
    public Function<Map<String, Object>, Map<String, Object>> schedulerFunction(
            @Value("${nestor.scheduler.app-runner-url}") String appRunnerUrl) {
        return new SchedulerFunction(appRunnerUrl);
    }
}
