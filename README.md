# Test Evidence Platform

> [English](README.md) | [简体中文](README.zh-CN.md)

An automation-test evidence platform for development and test environments. Configure test data and assertions, run an HTTP case or open a Browser black-box capture window, and let a Java Agent collect request, downstream-call, and JDBC evidence without changing business code.

The console reconstructs the captured execution as per-run topology, service dependency overview, and trace tree.

## Highlights

- **Two test triggers**: direct HTTP execution with assertions, or Browser black-box capture for real product-page operations.
- **Correlation by business data**: match one or more fields from JSON request bodies or HTTP headers, such as `orderNo`.
- **Cross-service evidence**: inbound HTTP, Spring `RestTemplate` context propagation, synchronous Dubbo 2.6/2.7 calls, and MySQL JDBC prepared statements.
- **Trace review**: keep repeated calls as separate Span instances, inspect topology or trace tree, and load captured HTTP request/response payloads on demand.
- **Data protection**: JDBC parameters are stored as setter metadata, length, and SHA-256 prefix only. MySQL scalar assertions use a separate read-only connection.

> This project is for development and test environments only. It does not support Dubbo 3, Triple, asynchronous or one-way Dubbo, WebClient, Feign, Apache HttpClient, OkHttp, or JDBC `Statement`.

## Architecture

```text
Test operator / product page
            |
            v
Platform Console ---- creates a run and publishes correlation rules
            |                                  |
            v                                  v
PostgreSQL <---------------- Java Agent in each JVM
                                      |
                           HTTP / Dubbo / MySQL JDBC evidence
```

## Modules

```text
platform-app/                 Platform service, web console, Flyway migrations
test-monitor-agent/           Java Agent, compiled to Java 8 bytecode
sample-order-service/         Browser black-box entry and HTTP downstream sample
sample-fulfillment-service/   MySQL JDBC evidence sample
```

The Platform uses JDK 17 and Spring Boot 3.3. Both sample services use Spring Boot 2.7. The Agent's Java 8 bytecode can load into JDK 8, 11, 17, and 21 processes; actual compatibility still depends on the target framework and protocol versions.

## Quick Start

### 1. Build

JDK 17 and Maven 3.9+ are required.

```sh
export JAVA_HOME='<JDK 17 directory>'
'<Maven directory>/bin/mvn' '-Dmaven.repo.local=<local Maven repository>' clean test package
```

Artifacts:

```text
platform-app/target/platform-app-0.1.0-SNAPSHOT.jar
test-monitor-agent/target/test-monitor-agent-0.1.0-SNAPSHOT.jar
sample-order-service/target/sample-order-service-0.1.0-SNAPSHOT.jar
sample-fulfillment-service/target/sample-fulfillment-service-0.1.0-SNAPSHOT.jar
```

### 2. Start the Platform

The Platform persists test cases, runs, and evidence in PostgreSQL 16. Keep actual addresses, credentials, and the Agent token outside source control.

```sh
export TEST_PLATFORM_DB_URL='jdbc:postgresql://<host>:5432/<database>'
export TEST_PLATFORM_DB_USERNAME='<username>'
export TEST_PLATFORM_DB_PASSWORD='<password>'
export TEST_EVIDENCE_AGENT_TOKEN='<long-random-token>'

# Optional: read-only connection for MySQL scalar assertions
export TEST_ASSERTION_MYSQL_URL='jdbc:mysql://<host>:3306/<database>'
export TEST_ASSERTION_MYSQL_USERNAME='<readonly-username>'
export TEST_ASSERTION_MYSQL_PASSWORD='<readonly-password>'

java -jar platform-app/target/platform-app-0.1.0-SNAPSHOT.jar
```

Open `http://localhost:8080/`. Flyway initializes the Platform schema automatically. Health endpoint: `GET /actuator/health`.

### 3. Attach the Agent

Each JVM under test must use the same Platform Collector and token. Choose a stable and recognizable `service` name.

```text
-javaagent:/path/to/test-monitor-agent-0.1.0-SNAPSHOT.jar=collector=http://<platform-host>:8080,service=<service-name>,token=<agent-token>
```

For Agent diagnostics, add:

```text
-Dtest.monitor.agent.debug=true
```

### 4. Run the Samples

The samples demonstrate Browser black-box capture, cross-service HTTP propagation, and MySQL JDBC evidence. The fulfillment sample writes only to `test_evidence_sample_fulfillments`.

```sh
export SAMPLE_FULFILLMENT_MYSQL_URL='jdbc:mysql://<host>:3306/<database>'
export SAMPLE_FULFILLMENT_MYSQL_USERNAME='<username>'
export SAMPLE_FULFILLMENT_MYSQL_PASSWORD='<password>'
export SAMPLE_FULFILLMENT_BASE_URL='http://127.0.0.1:19121'

# Run each command in its own terminal.
java '-javaagent:test-monitor-agent/target/test-monitor-agent-0.1.0-SNAPSHOT.jar=collector=http://127.0.0.1:8080,service=sample-fulfillment-service,token=<agent-token>' -jar sample-fulfillment-service/target/sample-fulfillment-service-0.1.0-SNAPSHOT.jar
java '-javaagent:test-monitor-agent/target/test-monitor-agent-0.1.0-SNAPSHOT.jar=collector=http://127.0.0.1:8080,service=sample-order-service,token=<agent-token>' -jar sample-order-service/target/sample-order-service-0.1.0-SNAPSHOT.jar
```

## Browser Black-Box Workflow

1. Create a capture profile in **Configuration**. Add one or more correlation fields and their locations; the service scope is optional.
2. Create a `BROWSER` test case and select that profile. Enable HTTP request/response payload capture only when it is needed.
3. In **Run**, enter a unique business value such as `orderNo`, start the run, and wait until rule propagation finishes.
4. Return to the product page and perform the real query, update, submit, or confirmation flow. Every matching request during the capture window produces a root Trace.
5. Click **Stop capture**, wait for in-flight calls to drain, then inspect the topology, service overview, and trace tree in **Results**.

Topology renders up to 500 Span instances. The trace tree and service overview retain all evidence. Repeated calls are never merged: `A -> B -> A -> B` remains four instances.

See the [illustrated console workflow guide](docs/platform-console-guide.html) for real UI screenshots and a step-by-step Browser black-box walkthrough.

## Data Retention

- Evidence for completed test cases is retained for 7 days by default; incomplete cases for 30 days.
- Run results and topology metadata are retained for 30 days by default.
- Cleanup runs every day at 00:00.
- Grant write access to sample services only on development/test databases. The MySQL assertion account must have `SELECT` only.

## Before Publishing to GitHub

```sh
rg -n -i '(192\.168\.|password:\s*[^$\s]|password=\S|mysql://|postgresql://.*192\.168)' . --glob '!**/target/**'
'<Maven directory>/bin/mvn' '-Dmaven.repo.local=<local Maven repository>' clean test package
```

`.gitignore` excludes build output, local development configuration, IDE files, and local workflow artifacts. Review `git status --ignored` before committing; credentials, logs, database exports, and runtime JARs must not be staged.

```sh
git init
git add .
git status --short
git remote add origin https://github.com/<owner>/<repository>.git
git branch -M main
git push -u origin main
```
