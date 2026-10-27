# 002 Application 本地基础设施、数据基线与集成契约任务

## T1 Spec 评审与契约冻结

- 状态：已完成
- 对应 design：§总体架构、§本地服务设计、§核心数据模型、§MQ 契约设计、§HTTP 契约设计
- 产物：评审通过的 `requirements.md`、`design.md`、`tasks.md`
- 完成条件：确认本地服务范围、七张表结构、两条 Topic、消费者组、HTTP 路径、请求头和评审决策项；若存在变更，先同步修订三份 Spec

## T2 本地 Docker 编排

- 状态：已完成
- 对应 design：§改动模块、§本地服务设计
- 产物：`deploy/docker-compose.yml`、`deploy/.env.example`、RocketMQ Broker 配置文件、`scripts/docker-start.sh`、`scripts/docker-stop.sh`
- 完成条件：`scripts/docker-start.sh` 可优先读取未提交的 `deploy/.env`，缺失时读取 `deploy/.env.example`，并启动 MySQL、Redis、RocketMQ NameServer、Broker、Dashboard，待服务健康后初始化两个 Topic；`scripts/docker-stop.sh` 可停止本项目容器和网络且保留 MySQL、Redis 数据卷；镜像版本、时区、MySQL 字符集和 Redis ACL 账号与 design 一致

## T3 MySQL 初始化 SQL

- 状态：已完成
- 对应 design：§核心数据模型、§建库与建表 SQL
- 产物：`deploy/mysql/init/01-create-database.sql`、`deploy/mysql/init/02-application-schema.sql`
- 完成条件：空数据卷初始化后存在 `claim_application_db`、`claim_app` 账号和七张基础表；Material 同时包含初始文件 URL 与上传后 URL，Issue 包含原因与审核人，InteractionLog 包含交互编号、交互类型、动作和事件发生时间，并能以交互编号查询同一行为的多条事件；所有唯一约束、生成列和索引可通过 `SHOW CREATE TABLE` 验证

## T4 RocketMQ Topic 初始化

- 状态：已完成
- 对应 design：§本地服务设计、§MQ 契约设计
- 产物：`scripts/docker-start.sh` 中的 Topic 创建命令
- 完成条件：Broker 禁用自动建 Topic 仍可成功创建 `claim_application_material_process_v1` 和 `claim_application_audit_delivery_v1`；两个 Topic 均配置 8 个读写队列

## T5 Application 本地连接配置

- 状态：已完成
- 对应 design：§改动模块、§对现有功能的影响
- 产物：更新后的 `backend/claim-application-service/src/main/resources/application.yml`
- 完成条件：未设置环境变量时使用 Docker 默认地址和 `claim_app` 账号；设置环境变量后 MySQL、Redis、RocketMQ 地址与账号被正确覆盖；Nacos local 配置保持关闭

## T6 集成契约文档

- 状态：已完成
- 对应 design：§MQ 契约设计、§HTTP 契约设计、§事务、幂等与异常处理
- 产物：`docs/claim-application-integration-contract.md`、`deploy/README.md`、`deploy/health-check.md`
- 完成条件：文档包含启动与清理命令、服务端口、数据卷说明、Topic 与消费者组、消息信封、HTTP 路径、请求头、幂等关联字段、敏感信息限制及每项本地服务的检查方式

## T7 验证与回归

- 状态：已完成
- 对应 design：§本地服务设计、§核心数据模型、§MQ 契约设计、§对现有功能的影响
- 产物：验证记录
- 完成条件：Docker Compose 配置校验通过；空卷启动后服务健康、数据库结构和 Topic 配置符合设计；Application 服务以 local profile 启动且能连接 MySQL、Redis、RocketMQ；`mvn -B test` 与 `mvn -B -DskipTests package` 通过

## 验证记录

- `docker compose --env-file deploy/.env.example -f deploy/docker-compose.yml config` 通过。
- MySQL、Redis、RocketMQ NameServer、Broker 均以默认端口启动并通过健康检查；`scripts/docker-start.sh` 在 Broker 健康后成功创建两个 Topic，Docker Desktop 不保留额外初始化容器。
- MySQL 已验证存在 `application`、`material`、`issue`、`case_relation`、`interaction_log`、`outbox_event`、`consumed_event` 七张基础表；Material、Issue、InteractionLog 的字段、生成列和索引符合 design。
- Redis 使用 `claim_app` ACL 账号认证后返回 `PONG`。
- 已验证 `claim_application_material_process_v1` 与 `claim_application_audit_delivery_v1` 存在；创建日志确认两个 Topic 均为 8 个读队列、8 个写队列。
- 使用 Corretto 21 执行 `mvn -B clean test` 通过，9 个现有测试全部通过；执行 `mvn -B -DskipTests package` 通过。
- 使用 Corretto 21 启动 `claim-application-service` 可执行 Jar，`http://127.0.0.1:8081/actuator/health` 返回 `{"status":"UP"}`；验证后已停止该 Java 进程。
- `scripts/docker-start.sh` 与 `scripts/docker-stop.sh` 均通过 Shell 语法检查，并完成一次停止后重启验证；停止过程保留命名数据卷，重启后四项常驻服务恢复健康。
- RocketMQ Dashboard 已启动并通过 HTTP 健康检查，可通过 `http://127.0.0.1:8082` 使用本地环境文件中的账号登录；`deploy/health-check.md` 已列出 MySQL、Redis、RocketMQ、Dashboard 和 Application 的检查命令与预期结果。
- 已确认并实际运行 `mysql:8.4.13`、`redis:7.4.11-alpine`、`apache/rocketmq:5.5.0`、`apacherocketmq/rocketmq-dashboard:2.1.0` 的 `arm64/linux` 变体；Compose 已移除 Dashboard 的 AMD64 强制平台配置，Dashboard HTTP 健康检查通过。
