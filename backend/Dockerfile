# ===========================================================================
# Unified Dockerfile for all NESTOR Lambda functions
#
# Usage (from backend/ directory):
#   docker build --target tagger    -t nestor-tagger    .
#   docker build --target charter   -t nestor-charter   .
#   docker build --target reporter  -t nestor-reporter  .
#   docker build --target retirement -t nestor-retirement .
#   docker build --platform linux/amd64 --provenance=false -t nestor-ingest .
#   docker build --target planner   -t nestor-planner   .
#   docker build --target scheduler -t nestor-scheduler .
#   docker build --target api       -t nestor-api       .
# ===========================================================================

# ===========================================================================
# Stage 1: Maven build — compiles all modules in one pass
# ===========================================================================
FROM amazoncorretto:21 AS builder

RUN yum install -y maven && yum clean all

WORKDIR /build
COPY pom.xml .
COPY common/pom.xml   common/pom.xml
COPY tagger/pom.xml   tagger/pom.xml
COPY charter/pom.xml  charter/pom.xml
COPY reporter/pom.xml reporter/pom.xml
COPY retirement/pom.xml retirement/pom.xml
COPY planner/pom.xml  planner/pom.xml
COPY scheduler/pom.xml scheduler/pom.xml
COPY api/pom.xml      api/pom.xml

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B -q || true

# Copy all source code and build
COPY . .
RUN mvn package -B -q -DskipTests

# ===========================================================================
# Stage 2: Unpack each fat JAR
# ===========================================================================
FROM amazoncorretto:21 AS unpack-tagger
WORKDIR /opt/app
COPY --from=builder /build/tagger/target/nestor-tagger-*.jar app.jar
RUN mkdir -p unpacked && cd unpacked && jar xf ../app.jar

FROM amazoncorretto:21 AS unpack-charter
WORKDIR /opt/app
COPY --from=builder /build/charter/target/nestor-charter-*.jar app.jar
RUN mkdir -p unpacked && cd unpacked && jar xf ../app.jar

FROM amazoncorretto:21 AS unpack-reporter
WORKDIR /opt/app
COPY --from=builder /build/reporter/target/nestor-reporter-*.jar app.jar
RUN mkdir -p unpacked && cd unpacked && jar xf ../app.jar

FROM amazoncorretto:21 AS unpack-retirement
WORKDIR /opt/app
COPY --from=builder /build/retirement/target/nestor-retirement-*.jar app.jar
RUN mkdir -p unpacked && cd unpacked && jar xf ../app.jar

FROM amazoncorretto:21 AS unpack-planner
WORKDIR /opt/app
COPY --from=builder /build/planner/target/nestor-planner-*.jar app.jar
RUN mkdir -p unpacked && cd unpacked && jar xf ../app.jar

FROM amazoncorretto:21 AS unpack-scheduler
WORKDIR /opt/app
COPY --from=builder /build/scheduler/target/nestor-scheduler-*.jar app.jar
RUN mkdir -p unpacked && cd unpacked && jar xf ../app.jar

FROM amazoncorretto:21 AS unpack-api
WORKDIR /opt/app
COPY --from=builder /build/api/target/nestor-api-*.jar app.jar
RUN mkdir -p unpacked && cd unpacked && jar xf ../app.jar

# ===========================================================================
# Stage 3: Lambda runtime images (one target per Lambda)
# ===========================================================================

# ---- Tagger ----
FROM public.ecr.aws/lambda/java:21 AS tagger
COPY --from=unpack-tagger /opt/app/unpacked/BOOT-INF/lib/     ${LAMBDA_TASK_ROOT}/lib/
COPY --from=unpack-tagger /opt/app/unpacked/BOOT-INF/classes/  ${LAMBDA_TASK_ROOT}/
COPY --from=unpack-tagger /opt/app/unpacked/META-INF/          ${LAMBDA_TASK_ROOT}/META-INF/
ENV SPRING_CLOUD_FUNCTION_DEFINITION=taggerFunction
CMD ["org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"]

# ---- Charter ----
FROM public.ecr.aws/lambda/java:21 AS charter
COPY --from=unpack-charter /opt/app/unpacked/BOOT-INF/lib/     ${LAMBDA_TASK_ROOT}/lib/
COPY --from=unpack-charter /opt/app/unpacked/BOOT-INF/classes/  ${LAMBDA_TASK_ROOT}/
COPY --from=unpack-charter /opt/app/unpacked/META-INF/          ${LAMBDA_TASK_ROOT}/META-INF/
ENV SPRING_CLOUD_FUNCTION_DEFINITION=charterFunction
CMD ["org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"]

# ---- Reporter ----
FROM public.ecr.aws/lambda/java:21 AS reporter
COPY --from=unpack-reporter /opt/app/unpacked/BOOT-INF/lib/     ${LAMBDA_TASK_ROOT}/lib/
COPY --from=unpack-reporter /opt/app/unpacked/BOOT-INF/classes/  ${LAMBDA_TASK_ROOT}/
COPY --from=unpack-reporter /opt/app/unpacked/META-INF/          ${LAMBDA_TASK_ROOT}/META-INF/
ENV SPRING_CLOUD_FUNCTION_DEFINITION=reporterFunction
CMD ["org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"]

# ---- Retirement ----
FROM public.ecr.aws/lambda/java:21 AS retirement
COPY --from=unpack-retirement /opt/app/unpacked/BOOT-INF/lib/     ${LAMBDA_TASK_ROOT}/lib/
COPY --from=unpack-retirement /opt/app/unpacked/BOOT-INF/classes/  ${LAMBDA_TASK_ROOT}/
COPY --from=unpack-retirement /opt/app/unpacked/META-INF/          ${LAMBDA_TASK_ROOT}/META-INF/
ENV SPRING_CLOUD_FUNCTION_DEFINITION=retirementFunction
CMD ["org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"]

# ---- Planner ----
FROM public.ecr.aws/lambda/java:21 AS planner
COPY --from=unpack-planner /opt/app/unpacked/BOOT-INF/lib/     ${LAMBDA_TASK_ROOT}/lib/
COPY --from=unpack-planner /opt/app/unpacked/BOOT-INF/classes/  ${LAMBDA_TASK_ROOT}/
COPY --from=unpack-planner /opt/app/unpacked/META-INF/          ${LAMBDA_TASK_ROOT}/META-INF/
ENV SPRING_CLOUD_FUNCTION_DEFINITION=plannerFunction
CMD ["org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"]

# ---- Scheduler ----
FROM public.ecr.aws/lambda/java:21 AS scheduler
COPY --from=unpack-scheduler /opt/app/unpacked/BOOT-INF/lib/     ${LAMBDA_TASK_ROOT}/lib/
COPY --from=unpack-scheduler /opt/app/unpacked/BOOT-INF/classes/  ${LAMBDA_TASK_ROOT}/
COPY --from=unpack-scheduler /opt/app/unpacked/META-INF/          ${LAMBDA_TASK_ROOT}/META-INF/
ENV SPRING_CLOUD_FUNCTION_DEFINITION=schedulerFunction
CMD ["org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"]

# ---- API ----
FROM public.ecr.aws/lambda/java:21 AS api
COPY --from=unpack-api /opt/app/unpacked/BOOT-INF/lib/     ${LAMBDA_TASK_ROOT}/lib/
COPY --from=unpack-api /opt/app/unpacked/BOOT-INF/classes/  ${LAMBDA_TASK_ROOT}/
COPY --from=unpack-api /opt/app/unpacked/META-INF/          ${LAMBDA_TASK_ROOT}/META-INF/
CMD ["com.nestor.api.StreamLambdaHandler::handleRequest"]
