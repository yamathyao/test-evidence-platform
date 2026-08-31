FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY platform-app/pom.xml platform-app/pom.xml
COPY test-monitor-agent/pom.xml test-monitor-agent/pom.xml
COPY sample-dubbo-contract/pom.xml sample-dubbo-contract/pom.xml
COPY sample-order-service/pom.xml sample-order-service/pom.xml
COPY sample-fulfillment-service/pom.xml sample-fulfillment-service/pom.xml
COPY platform-app platform-app
COPY test-monitor-agent test-monitor-agent
COPY sample-dubbo-contract sample-dubbo-contract
COPY sample-order-service sample-order-service
COPY sample-fulfillment-service sample-fulfillment-service
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime-base
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/test-evidence
COPY --from=build /workspace/test-monitor-agent/target/test-monitor-agent-0.1.0-SNAPSHOT.jar test-monitor-agent.jar

FROM runtime-base AS platform
COPY --from=build /workspace/platform-app/target/platform-app-0.1.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["sh", "-c", "exec java -jar /opt/test-evidence/app.jar"]

FROM runtime-base AS fulfillment
COPY --from=build /workspace/sample-fulfillment-service/target/sample-fulfillment-service-0.1.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["sh", "-c", "exec java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED -javaagent:/opt/test-evidence/test-monitor-agent.jar=collector=${TEST_EVIDENCE_COLLECTOR},service=sample-fulfillment-service,token=${TEST_EVIDENCE_AGENT_TOKEN} -jar /opt/test-evidence/app.jar"]

FROM runtime-base AS order
COPY --from=build /workspace/sample-order-service/target/sample-order-service-0.1.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["sh", "-c", "exec java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED -javaagent:/opt/test-evidence/test-monitor-agent.jar=collector=${TEST_EVIDENCE_COLLECTOR},service=sample-order-service,token=${TEST_EVIDENCE_AGENT_TOKEN} -jar /opt/test-evidence/app.jar"]
