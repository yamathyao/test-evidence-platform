# Test Evidence Platform

> [English](README.md) | [简体中文](README.zh-CN.md)

An automation-test evidence platform for development and test environments. Configure test data and assertions, run an HTTP case or open a Browser black-box capture window, and let a Java Agent collect request, downstream-call, and JDBC evidence without changing business code.

The console reconstructs the captured execution as per-run topology, service dependency overview, and trace tree.

## Highlights

- **Two test triggers**: direct HTTP execution with assertions, or Browser black-box capture for real product-page operations.
- **Correlation by business data**: match one or more fields from JSON request bodies or HTTP headers, such as `orderNo`.
- **Cross-service evidence**: inbound HTTP, Spring `RestTemplate` context propagation, synchronous Dubbo 2.6/2.7 calls, MySQL JDBC prepared statements and `Statement`, plus synchronous Apache HttpClient 4/5, OkHttp 3/4, Feign, and WebClient `Mono` clients.
- **Trace review**: keep repeated calls as separate Span instances, inspect topology or trace tree, and load captured HTTP request/response payloads or Dubbo arguments/return values on demand.
- **Data protection**: JDBC parameters are stored as setter metadata, length, and SHA-256 prefix only. MySQL scalar assertions use a separate read-only connection.

> This project is for development and test environments only. It must never be deployed in production or connected to production applications or data. The supported client baseline is synchronous only: Apache HttpClient 4/5 Classic, OkHttp 3/4, Feign, and WebClient `Mono`, plus JDBC `Statement`. It still does not support Dubbo 3/Triple, asynchronous or one-way Dubbo, client async callbacks, WebClient `Flux`, streaming responses, WebSocket, or production deployment.

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
sample-dubbo-contract/        Framework-free direct Dubbo sample contract
sample-order-service/         Browser black-box entry, HTTP and Dubbo downstream sample
sample-fulfillment-service/   Direct Dubbo provider and MySQL JDBC evidence sample
```

The Platform uses JDK 17 and Spring Boot 3.3. Both sample services use Spring Boot 2.7. The Agent's Java 8 bytecode can load into JDK 8, 11, 17, and 21 processes; actual compatibility still depends on the target framework and protocol versions.

## Delivery Roadmap

The project remains deliberately scoped to development and test environments. Do not add production-oriented high availability, user access control, durable message queues, or broad protocol support unless the project scope changes.

1. **Phase 1 - reproducible end-to-end baseline**: provide a repeatable environment for PostgreSQL, MySQL, the Platform, both samples, and the Agent. Verify HTTP execution, Browser black-box capture, cross-service propagation, JDBC evidence, Trace review, and payload loading in one smoke workflow.
2. **Phase 2 - evidence completeness visibility**: persist Agent delivery failures and dropped Evidence per Run and service, then expose their aggregated diagnostics in Run results. Diagnostics are best-effort and appear after a later Evidence batch succeeds. Add bounded retry only after defining idempotency and server-side deduplication semantics.
3. **Phase 3 - regression and data lifecycle**: extend integration coverage for evidence ingestion validation, rule refresh, run settlement, and retention cleanup so long-running development/test use remains predictable.
4. **Phase 4 - demand-driven compatibility**: add one framework or protocol at a time with a compatibility sample, instrumentation tests, and an end-to-end acceptance case. The current baseline covers JDBC `Statement`, Apache HttpClient 4/5 Classic, OkHttp 3/4 sync, Feign sync, and WebClient `Mono`; async, Flux, streaming, WebSocket, and Dubbo 3/Triple remain outside scope.
> **Current engineering status:** Phases 1-3 are implemented. The Phase 4 compatibility baseline is implemented and covered by Agent and Platform Maven tests. Compose smoke verification remains an external development/test-machine step because this workspace has no Docker runtime.

## Docker Compose Smoke Environment

This Compose environment is restricted to development and test use. Do not deploy it in production or connect it to production applications or data. PostgreSQL uses the Debian-based `postgres:16-bookworm` image for Docker runtime compatibility.

Follow these steps on the Docker Compose test machine:

### Step 1: Create the test configuration

```sh
cp .env.example .env
```

The Compose network defaults to `10.250.0.0/24` so Docker does not allocate from the common `192.168.0.0/16` LAN range. If this subnet overlaps with a routed network on the test machine, set `TEST_EVIDENCE_NETWORK_SUBNET` in `.env` to an unused private `/24` before starting the stack.

Review `.env` and set a non-shared `TEST_EVIDENCE_AGENT_TOKEN` when the environment is shared with others.

### Step 2: Start the stack

```sh
docker compose --env-file .env up --build -d
docker compose ps
```

Wait until PostgreSQL, MySQL, Platform, fulfillment, and order all report `healthy`. When a service does not become healthy, inspect it before continuing:

```sh
docker compose logs postgres mysql platform fulfillment order
```

### Step 3: Run the Browser black-box smoke workflow

```sh
sh scripts/compose-smoke.sh
```

The script requires `curl`. It prints seven numbered stages. In Stage 3 it waits six seconds after the Platform publishes the correlation rule, allowing the order Agent's five-second refresh loop to receive it before the matching request is sent. Stage 5 calls `POST /sample/dubbo` first, then `POST /sample/protocols/{client}` for `apache4`, `apache5`, `okhttp`, `feign`, and `webclient`; the fulfillment service delegates the direct Dubbo 2.7 request and every protocol echo to a JDBC `Statement` query. Stage 7 requires one DUBBO CLIENT and one DUBBO SERVER Evidence event, captured Dubbo arguments and return value containing the generated `orderNo`, five new HTTP CLIENT Evidence events, and five JDBC Statement events with empty `jdbcParameters`. A successful run ends with `[PASSED]` and a Run ID; a failed HTTP request prints its method and URL.

When a slow test machine needs a longer propagation window, set `AGENT_RULE_REFRESH_SECONDS` to at least `5`:

```sh
AGENT_RULE_REFRESH_SECONDS=8 sh scripts/compose-smoke.sh
```

If Stage 6 reports that a Run has no root traces, inspect the Agent boundaries before retrying:

```sh
docker compose logs --tail=300 order fulfillment platform \
  | grep -E 'blackbox\.rule-refresh|blackbox\.request-match|evidence\.delivery|Unauthorized|Invalid evidence|Run not found'
```

The order service should log `blackbox.rule-refresh.service=sample-order-service count=1`, then `blackbox.request-match.run=...`, followed by an `evidence.delivery.status=202` entry. Missing rule refresh means the Agent did not receive the rule; a missing request match means the correlation data did not match; a delivery failure means the Platform rejected or could not receive evidence. Once a later Evidence batch is accepted, the Run response exposes `evidenceDeliveryDiagnostics` with per-service dropped-event and failed-delivery counts.

### Step 4: Use the configured Agent Token

When `TEST_EVIDENCE_AGENT_TOKEN` in `.env` differs from the example value, use the same token for the smoke script:

```sh
TEST_EVIDENCE_AGENT_TOKEN='<test-agent-token>' sh scripts/compose-smoke.sh
```

### Step 5: Stop or reset the stack

```sh
docker compose down
```

`docker compose down -v` deletes the disposable PostgreSQL and MySQL volumes. Use it only when removing the test database data is intended.

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
sample-dubbo-contract/target/sample-dubbo-contract-0.1.0-SNAPSHOT.jar
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

The samples demonstrate Browser black-box capture, cross-service HTTP propagation, direct Dubbo 2.7 calls, and MySQL JDBC evidence. The fulfillment sample writes only to `test_evidence_sample_fulfillments`.

```sh
export SAMPLE_FULFILLMENT_MYSQL_URL='jdbc:mysql://<host>:3306/<database>'
export SAMPLE_FULFILLMENT_MYSQL_USERNAME='<username>'
export SAMPLE_FULFILLMENT_MYSQL_PASSWORD='<password>'
export SAMPLE_FULFILLMENT_DUBBO_PORT='20880'
export SAMPLE_FULFILLMENT_BASE_URL='http://127.0.0.1:19121'
export SAMPLE_FULFILLMENT_DUBBO_URL='dubbo://127.0.0.1:20880'

# Run each command in its own terminal.
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED '-javaagent:test-monitor-agent/target/test-monitor-agent-0.1.0-SNAPSHOT.jar=collector=http://127.0.0.1:8080,service=sample-fulfillment-service,token=<agent-token>' -jar sample-fulfillment-service/target/sample-fulfillment-service-0.1.0-SNAPSHOT.jar
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED '-javaagent:test-monitor-agent/target/test-monitor-agent-0.1.0-SNAPSHOT.jar=collector=http://127.0.0.1:8080,service=sample-order-service,token=<agent-token>' -jar sample-order-service/target/sample-order-service-0.1.0-SNAPSHOT.jar
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
