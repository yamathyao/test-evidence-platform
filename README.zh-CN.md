# Test Evidence Platform

> [English](README.md) | [简体中文](README.zh-CN.md)

面向开发与测试环境的自动化测试链路采集平台。测试人员配置测试数据和断言后，可直接触发 HTTP 用例，或启动 Browser 黑盒采集窗口；Java Agent 无需改动业务代码即可采集请求、下游调用和 JDBC Evidence。

控制台将单次运行结果还原为调用拓扑、服务依赖概览和 Trace 树形明细。

## 核心能力

- **两种触发方式**：支持带断言的 HTTP 直接执行，以及面向真实产品页面操作的 Browser 黑盒采集。
- **业务数据关联**：按 JSON 请求体或 HTTP Header 中的一个或多个字段匹配，例如 `orderNo`。
- **跨服务 Evidence**：采集 HTTP 入站、Spring `RestTemplate` 上下文传播、Dubbo 2.6/2.7 同步调用、MySQL JDBC PreparedStatement 与 `Statement`，以及 Apache HttpClient 4/5、OkHttp 3/4、Feign、WebClient `Mono` 同步客户端调用。
- **链路审阅**：重复调用保留为独立 Span 实例；可查看拓扑、树形明细，并按需加载 HTTP 入参/出参原文或 Dubbo 方法参数/返回值。
- **数据保护**：JDBC 参数只保存 setter 元数据、长度和 SHA-256 前缀摘要；MySQL 标量断言使用独立只读连接。

> 本项目仅用于开发/测试环境，禁止部署到生产环境，也禁止接入生产应用或生产数据。当前仅支持同步客户端基线：Apache HttpClient 4/5 Classic、OkHttp 3/4、Feign、WebClient `Mono` 和 JDBC `Statement`；仍不支持 Dubbo 3/Triple、异步或单向 Dubbo、客户端异步回调、WebClient `Flux`、流式响应、WebSocket，也不面向生产部署。

## 架构概览

```text
测试人员 / 产品页面
            |
            v
Platform Console ---- 创建运行并下发关联规则
            |                                  |
            v                                  v
PostgreSQL <---------------- 各业务 JVM 中的 Java Agent
                                      |
                           HTTP / Dubbo / MySQL JDBC Evidence
```

## 工程模块

```text
platform-app/                 Platform 服务、Web 控制台、Flyway 迁移
test-monitor-agent/           Java Agent，编译为 Java 8 字节码
sample-dubbo-contract/        无框架依赖的直连 Dubbo 样例契约
sample-order-service/         Browser 黑盒入口、HTTP 与 Dubbo 下游调用样例
sample-fulfillment-service/   直连 Dubbo Provider 与 MySQL JDBC Evidence 样例
```

Platform 使用 JDK 17 / Spring Boot 3.3；两个样例服务使用 Spring Boot 2.7。Agent 的 Java 8 字节码可加载到 JDK 8、11、17 和 21 进程中，实际兼容性仍取决于目标框架和协议版本。

## 后续工作路线

项目将持续限定在开发/测试环境。不在当前范围内引入生产级高可用、用户权限体系、持久化消息队列或大范围协议兼容；除非项目使用边界发生变化。

1. **阶段 1：可重复的端到端基线**：提供 PostgreSQL、MySQL、Platform、两个样例服务和 Agent 的可重复启动环境；用一条 Smoke 流程验证 HTTP 触发、Browser 黑盒采集、跨服务传播、JDBC Evidence、Trace 审阅与 Payload 加载。
2. **阶段 2：Evidence 完整性可见**：按 Run 与服务持久化 Agent 投递失败和丢弃 Evidence，并在 Run 结果中返回汇总诊断。诊断采用最佳努力上报，会在后续 Evidence 批次成功时出现。只有在明确幂等键和服务端去重语义后，才增加有界重试。
3. **阶段 3：回归与数据生命周期**：扩展 Evidence 入库校验、规则刷新、Run 结算和保留清理的集成测试，确保长期开发/测试使用时行为可预测。
4. **阶段 4：按需求扩展兼容性**：一次增加一项框架或协议，并配套兼容性样例、插桩测试和端到端验收用例。本阶段已覆盖 JDBC `Statement`、Apache HttpClient 4/5 Classic、OkHttp 3/4 同步、Feign 同步和 WebClient `Mono`；异步、Flux、流式响应、WebSocket、Dubbo 3/Triple 仍在范围之外。
> **当前工程状态：** 阶段 1-3 已实现，阶段 4 的兼容性基线也已完成并通过 Agent 与 Platform Maven 测试。Compose 冒烟仍需在外部开发/测试机执行，因为当前工作区没有 Docker 运行时。

## Docker Compose 冒烟环境

该 Compose 环境仅限开发/测试使用，禁止部署到生产环境，也禁止接入生产应用或生产数据。PostgreSQL 使用基于 Debian 的 `postgres:16-bookworm` 镜像，以兼容已验证的 Docker 运行环境。

在安装 Docker Compose 的测试机上，按以下步骤执行：

### 第 1 步：创建测试配置

```sh
cp .env.example .env
```

Compose 网络默认使用 `10.250.0.0/24`，避免 Docker 从常见的 `192.168.0.0/16` 局域网地址段分配子网。若测试机的路由网络与该子网冲突，请在启动前将 `.env` 中的 `TEST_EVIDENCE_NETWORK_SUBNET` 改为未使用的私网 `/24` 网段。

检查 `.env`。多人共享测试环境时，应设置未共享的 `TEST_EVIDENCE_AGENT_TOKEN`。

### 第 2 步：启动环境

```sh
docker compose --env-file .env up --build -d
docker compose ps
```

等待 PostgreSQL、MySQL、Platform、fulfillment 和 order 都显示为 `healthy`。若任一服务未变为健康状态，先查看日志再继续：

```sh
docker compose logs postgres mysql platform fulfillment order
```

### 第 3 步：执行 Browser 黑盒冒烟流程

```sh
sh scripts/compose-smoke.sh
```

脚本依赖 `curl`，会输出七个带编号的阶段。第 3 阶段中，Platform 发布关联规则后，脚本会额外等待 6 秒，让订单服务 Agent 完成其 5 秒一次的规则刷新，再发送匹配请求。第 5 阶段先调用 `POST /sample/dubbo`，再依次调用 `POST /sample/protocols/{client}`，其中 `client` 为 `apache4`、`apache5`、`okhttp`、`feign` 或 `webclient`；fulfillment 会将直连 Dubbo 2.7 请求和各协议回显都委托给 JDBC `Statement` 查询。第 7 阶段断言一条 DUBBO CLIENT、一条 DUBBO SERVER、已采集且包含本次 `orderNo` 的 Dubbo 方法参数和返回值、五条新增 HTTP CLIENT Evidence 与五条空 `jdbcParameters` 的 JDBC Statement Evidence。成功时末尾显示 `[PASSED]` 和 Run ID；HTTP 请求失败时会显示请求方法与 URL。

测试机较慢时，可将 `AGENT_RULE_REFRESH_SECONDS` 设为不少于 `5` 的值，以延长规则传播等待时间：

```sh
AGENT_RULE_REFRESH_SECONDS=8 sh scripts/compose-smoke.sh
```

如果第 6 阶段报告 Run 没有根 Trace，请先检查 Agent 的三个边界日志，再决定是否重试：

```sh
docker compose logs --tail=300 order fulfillment platform \
  | grep -E 'blackbox\.rule-refresh|blackbox\.request-match|evidence\.delivery|Unauthorized|Invalid evidence|Run not found'
```

订单服务应依次出现 `blackbox.rule-refresh.service=sample-order-service count=1`、`blackbox.request-match.run=...` 和 `evidence.delivery.status=202`。缺少规则刷新表示 Agent 未拿到规则；缺少请求匹配表示关联数据未命中；存在投递失败表示 Platform 拒绝或未收到 Evidence。后续任一 Evidence 批次被接收后，Run 响应中的 `evidenceDeliveryDiagnostics` 会给出按服务汇总的丢弃事件数和投递失败数。

### 第 4 步：使用已配置的 Agent Token

如果 `.env` 中的 `TEST_EVIDENCE_AGENT_TOKEN` 与示例值不同，运行脚本时也要使用相同 Token：

```sh
TEST_EVIDENCE_AGENT_TOKEN='<测试 Agent Token>' sh scripts/compose-smoke.sh
```

### 第 5 步：停止或重置环境

```sh
docker compose down
```

`docker compose down -v` 会同时删除可丢弃的 PostgreSQL 和 MySQL 数据卷，只有明确需要清除测试数据库数据时才可执行。

## 快速开始

### 1. 构建

需要 JDK 17 与 Maven 3.9+：

```sh
export JAVA_HOME='<JDK 17 目录>'
'<Maven 目录>/bin/mvn' '-Dmaven.repo.local=<本地 Maven 仓库>' clean test package
```

构建产物：

```text
platform-app/target/platform-app-0.1.0-SNAPSHOT.jar
test-monitor-agent/target/test-monitor-agent-0.1.0-SNAPSHOT.jar
sample-dubbo-contract/target/sample-dubbo-contract-0.1.0-SNAPSHOT.jar
sample-order-service/target/sample-order-service-0.1.0-SNAPSHOT.jar
sample-fulfillment-service/target/sample-fulfillment-service-0.1.0-SNAPSHOT.jar
```

### 2. 启动 Platform

Platform 使用 PostgreSQL 16 保存测试用例、运行记录和 Evidence。请在当前终端设置配置，不要将实际地址、账号、密码或 Agent Token 写入源码。

```sh
export TEST_PLATFORM_DB_URL='jdbc:postgresql://<host>:5432/<database>'
export TEST_PLATFORM_DB_USERNAME='<username>'
export TEST_PLATFORM_DB_PASSWORD='<password>'
export TEST_EVIDENCE_AGENT_TOKEN='<long-random-token>'

# 可选：MySQL 标量断言使用独立只读账号
export TEST_ASSERTION_MYSQL_URL='jdbc:mysql://<host>:3306/<database>'
export TEST_ASSERTION_MYSQL_USERNAME='<readonly-username>'
export TEST_ASSERTION_MYSQL_PASSWORD='<readonly-password>'

java -jar platform-app/target/platform-app-0.1.0-SNAPSHOT.jar
```

默认访问地址：`http://localhost:8080/`。数据库初始化由 Flyway 自动执行；健康检查：`GET /actuator/health`。

### 3. 接入 Agent

每个被测 JVM 使用同一 Platform Collector 和 Token 启动。`service` 应为稳定、可辨识的服务名。

```text
-javaagent:/path/to/test-monitor-agent-0.1.0-SNAPSHOT.jar=collector=http://<platform-host>:8080,service=<service-name>,token=<agent-token>
```

调试 Agent 时额外增加：

```text
-Dtest.monitor.agent.debug=true
```

### 4. 启动两个样例服务

样例用于验证 Browser 黑盒、跨服务 HTTP 传播、直连 Dubbo 2.7 调用和 MySQL JDBC Evidence。履约服务只操作 `test_evidence_sample_fulfillments` 表。

```sh
export SAMPLE_FULFILLMENT_MYSQL_URL='jdbc:mysql://<host>:3306/<database>'
export SAMPLE_FULFILLMENT_MYSQL_USERNAME='<username>'
export SAMPLE_FULFILLMENT_MYSQL_PASSWORD='<password>'
export SAMPLE_FULFILLMENT_DUBBO_PORT='20880'
export SAMPLE_FULFILLMENT_BASE_URL='http://127.0.0.1:19121'
export SAMPLE_FULFILLMENT_DUBBO_URL='dubbo://127.0.0.1:20880'

# 分别在两个终端执行以下命令
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED '-javaagent:test-monitor-agent/target/test-monitor-agent-0.1.0-SNAPSHOT.jar=collector=http://127.0.0.1:8080,service=sample-fulfillment-service,token=<agent-token>' -jar sample-fulfillment-service/target/sample-fulfillment-service-0.1.0-SNAPSHOT.jar
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED '-javaagent:test-monitor-agent/target/test-monitor-agent-0.1.0-SNAPSHOT.jar=collector=http://127.0.0.1:8080,service=sample-order-service,token=<agent-token>' -jar sample-order-service/target/sample-order-service-0.1.0-SNAPSHOT.jar
```

## Browser 黑盒流程

1. 在 **配置** 中创建采集规则，添加一个或多个关联字段及其位置；服务范围为可选项。
2. 创建 `BROWSER` 测试用例并选择该规则。仅在需要时开启 HTTP 入参/出参原文采集。
3. 在 **运行** 中填写本次唯一业务标识，例如 `orderNo`，启动运行并等待规则下发完成。
4. 回到产品页面执行真实的查询、修改、提交或确认操作。采集窗口内每条匹配请求都会形成对应根 Trace。
5. 点击 **结束采集**，等待在途调用排空；随后在 **结果** 中查看拓扑、服务概览和树形明细。

拓扑最多绘制 500 个 Span 实例；树形明细和服务依赖概览保留完整 Evidence。重复服务调用不会合并，例如 `A -> B -> A -> B` 会展示为四个实例节点。

完整操作界面和 Browser 黑盒流程，请参阅 [Platform Console 图文流程指南](docs/platform-console-guide.html)。

## 数据保留

- 已完成测试用例的相关 Evidence 默认保留 7 天；未完成用例默认保留 30 天。
- Run 结果和拓扑元数据默认保留 30 天。
- 清理任务每日 00:00 执行。
- 样例服务只应在开发/测试库拥有写权限；MySQL 断言账号必须只授予 `SELECT`。

## 发布到 GitHub 前

```sh
rg -n -i '(192\.168\.|password:\s*[^$\s]|password=\S|mysql://|postgresql://.*192\.168)' . --glob '!**/target/**'
'<Maven 目录>/bin/mvn' '-Dmaven.repo.local=<本地 Maven 仓库>' clean test package
```

`.gitignore` 已排除构建产物、本地开发配置、IDE 文件和工作流临时文件。提交前应检查 `git status --ignored`，确认凭证、日志、数据库导出和运行 Jar 没有被暂存。

```sh
git init
git add .
git status --short
git remote add origin https://github.com/<owner>/<repository>.git
git branch -M main
git push -u origin main
```
