# Test Evidence Platform

> [English](README.md) | [简体中文](README.zh-CN.md)

面向开发与测试环境的自动化测试链路采集平台。测试人员配置测试数据和断言后，可直接触发 HTTP 用例，或启动 Browser 黑盒采集窗口；Java Agent 无需改动业务代码即可采集请求、下游调用和 JDBC Evidence。

控制台将单次运行结果还原为调用拓扑、服务依赖概览和 Trace 树形明细。

## 核心能力

- **两种触发方式**：支持带断言的 HTTP 直接执行，以及面向真实产品页面操作的 Browser 黑盒采集。
- **业务数据关联**：按 JSON 请求体或 HTTP Header 中的一个或多个字段匹配，例如 `orderNo`。
- **跨服务 Evidence**：采集 HTTP 入站、Spring `RestTemplate` 上下文传播、Dubbo 2.6/2.7 同步调用，以及 MySQL JDBC PreparedStatement。
- **链路审阅**：重复调用保留为独立 Span 实例；可查看拓扑、树形明细，并按需加载 HTTP 入参/出参原文。
- **数据保护**：JDBC 参数只保存 setter 元数据、长度和 SHA-256 前缀摘要；MySQL 标量断言使用独立只读连接。

> 本项目仅用于开发/测试环境；不支持 Dubbo 3、Triple、异步或单向 Dubbo、WebClient、Feign、Apache HttpClient、OkHttp 和 JDBC `Statement`。

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
sample-order-service/         Browser 黑盒入口与 HTTP 下游调用样例
sample-fulfillment-service/   MySQL JDBC Evidence 样例
```

Platform 使用 JDK 17 / Spring Boot 3.3；两个样例服务使用 Spring Boot 2.7。Agent 的 Java 8 字节码可加载到 JDK 8、11、17 和 21 进程中，实际兼容性仍取决于目标框架和协议版本。

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

样例用于验证 Browser 黑盒、跨服务 HTTP 传播和 MySQL JDBC Evidence。履约服务只操作 `test_evidence_sample_fulfillments` 表。

```sh
export SAMPLE_FULFILLMENT_MYSQL_URL='jdbc:mysql://<host>:3306/<database>'
export SAMPLE_FULFILLMENT_MYSQL_USERNAME='<username>'
export SAMPLE_FULFILLMENT_MYSQL_PASSWORD='<password>'
export SAMPLE_FULFILLMENT_BASE_URL='http://127.0.0.1:19121'

# 分别在两个终端执行以下命令
java '-javaagent:test-monitor-agent/target/test-monitor-agent-0.1.0-SNAPSHOT.jar=collector=http://127.0.0.1:8080,service=sample-fulfillment-service,token=<agent-token>' -jar sample-fulfillment-service/target/sample-fulfillment-service-0.1.0-SNAPSHOT.jar
java '-javaagent:test-monitor-agent/target/test-monitor-agent-0.1.0-SNAPSHOT.jar=collector=http://127.0.0.1:8080,service=sample-order-service,token=<agent-token>' -jar sample-order-service/target/sample-order-service-0.1.0-SNAPSHOT.jar
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
