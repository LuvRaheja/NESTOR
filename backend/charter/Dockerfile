# ===========================================================================
# Stage 1: Unpack Spring Boot fat JAR
# ===========================================================================
FROM amazoncorretto:21 AS builder
WORKDIR /opt/app

# Copy the pre-built fat JAR from the host
COPY target/nestor-charter-*.jar app.jar

# Unpack the fat JAR to get BOOT-INF/lib/ and BOOT-INF/classes/
RUN mkdir -p unpacked && cd unpacked && jar xf ../app.jar

# ===========================================================================
# Stage 2: Lambda Runtime
# ===========================================================================
FROM public.ecr.aws/lambda/java:21

# Copy dependency JARs to lib/ (Lambda adds /var/task/lib/*.jar to classpath)
COPY --from=builder /opt/app/unpacked/BOOT-INF/lib/ ${LAMBDA_TASK_ROOT}/lib/

# Copy application classes to task root (on classpath directly)
COPY --from=builder /opt/app/unpacked/BOOT-INF/classes/ ${LAMBDA_TASK_ROOT}/

# Copy META-INF for Spring autoconfiguration
COPY --from=builder /opt/app/unpacked/META-INF/ ${LAMBDA_TASK_ROOT}/META-INF/

# Spring Cloud Function routing
ENV SPRING_CLOUD_FUNCTION_DEFINITION=charterFunction

CMD ["org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"]
