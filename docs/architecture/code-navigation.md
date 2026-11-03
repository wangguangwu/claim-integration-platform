# 项目背景与目标

本文档提供源码导航入口，帮助开发人员按业务能力和分层职责定位代码。当前项目采用标准 DDD 角色分层目录；当需要理解一条业务链路时，通过下表跨层定位，而不是依赖文件名猜测。

# 总体架构

```text
backend/
├── claim-foundation/                 公共租户、观测与基础能力
├── claim-gateway/                    网关路由、租户透传、限流与链路标识
├── claim-application-service/        理赔申请核心业务服务
├── claim-case-service/               理赔案件服务预留模块
└── claim-ai-service/                 理赔 AI 服务预留模块
```

`claim-application-service` 的依赖方向固定为：

```text
interfaces → application → domain ← infrastructure
```

`shared` 位于依赖图底部，只能被其他层引用。该方向由 `ClaimApplicationArchitectureTest` 在 Maven 测试阶段持续校验；新增代码不得通过跨层 import 绕过应用端口或领域模型。

- `interfaces`：入站适配器，按 `web`、`consumer`、`dto`、`scheduler` 分包，只处理 HTTP、消息、调度、协议和响应映射。
- `application`：应用编排，按 `command`、`query`、`port`、`process`、`exception` 分包；拥有出站能力端口、端口模型、用例编排与应用异常（`ApplicationException`）。
- `domain`：业务状态机、聚合根与聚合内实体、值对象、聚合外追加事实、聚合 Repository 和领域错误码（`DomainException`），不依赖具体技术实现。
- `infrastructure`：按 `persistence`、`adapter`（Redis、Redisson、发号、锁、熔断等技术适配）、`acl`（审核、承保、文件等外部系统实现）、`mq`、`config` 分包，实现 `application/port` 与 `domain` 聚合 Repository。
- `shared`：按 `exception`、`message`、`util` 分包，承载传输无关异常基类、消息契约与无业务归属工具。

# 核心链路设计

## 理赔申请服务目录职责

| 目录 | 进入条件 | 主要内容 | 不应放置的内容 |
| --- | --- | --- | --- |
| `domain/claim` | 要定义业务事实、状态规则或聚合持久化契约 | 聚合根、状态枚举；`material` 材料实体、`issue` 问题件实体、`person` 人员值对象 | 技术能力端口、Spring Controller、MyBatis Mapper、Redis 客户端调用 |
| `domain/claimcase` | 要表达不参与聚合加载的追加事实 | 案件关联追加事实与其端口 | 聚合状态迁移、材料/问题件实体 |
| `domain/claim/port` | 要声明聚合生命周期契约 | 聚合 Repository | 读模型查询、锁、发号、外部系统调用 |
| `domain/claim/exception` | 要表达领域拒绝 | 领域错误码 | HTTP 状态、应用编排错误 |
| `application/process` | 要编排一次完整业务操作 | 用例、事务服务、结果模型、记录装配、冲突解析 | HTTP 注解、数据库对象、第三方 SDK 调用、业务不变式 |
| `application/command` | 要描述用例入参 | 命令记录 | 协议字段、校验注解以外的框架细节 |
| `application/query` | 要实现只读查询 | 查询用例与读模型 | 聚合加载、交互轨迹写入 |
| `application/port` | 要声明出站能力 | 锁、发号、缓存、外部系统、Outbox、消费去重、补偿策略、读模型端口 | 端口实现、配置绑定 |
| `application/exception` | 要表达应用层拒绝 | `ApplicationException`、异步错误码、内部冲突信号 | 领域不变式、HTTP 状态 |
| `infrastructure/persistence` | 要接入数据库 | DO、ORM Mapper、Converter、仓储与读模型实现 | 业务状态机、接口响应映射 |
| `infrastructure/adapter` | 要接入 Redis、Redisson 等技术设施 | Redis 快照、发号、分布式锁与熔断 | 外部系统交互、用例流程决策 |
| `infrastructure/acl` | 要对接审核、承保或文件服务 | 外部系统端口实现（`Logging*`，只打印调用日志） | Redis 技术实现、用例流程决策 |
| `infrastructure/mq` | 要接入消息中间件 | Outbox 发布实现 | 消息契约、消费者 |
| `infrastructure/config` | 要绑定配置或装配框架 | `*Properties`、MyBatis-Plus/Redisson 配置 | 业务参数决策 |
| `interfaces` | 要承接外部输入或对外调度 | Controller、Consumer、Scheduler、租户协议流水线、请求响应 DTO | 领域规则和持久化实现 |
| `shared` | 确属跨业务能力复用 | 异常基类、错误码接口、消息契约、无业务归属工具 | 单一业务域模型、HTTP 请求或响应模型 |

## 按业务能力定位

| 业务能力 | 领域模型 | 应用端口与编排 | 基础设施实现 | 入站入口 |
| --- | --- | --- | --- | --- |
| 申请签收 | `domain/claim`（`ClaimApplication` 聚合根） | `application/process/{ClaimApplicationIntakeUseCase,ClaimApplicationIntakeTransactionService}`、`application/port` | `infrastructure/persistence`、`infrastructure/adapter`（锁、快照、发号） | `interfaces/web`、`interfaces/web/tenantprotocol` |
| 材料处理 | `domain/claim/material`（`Material` 聚合内实体） | `application/process/{MaterialProcessingUseCase,MaterialProcessingTransactionService}` | `infrastructure/acl/LoggingMaterialFileProcessor` | `interfaces/consumer/MaterialProcessConsumer` |
| 自动承保 | `domain/claim` 的状态推进 | `application/process/{UnderwritingUseCase,UnderwritingTransactionService,UnderwritingCallbackUseCase}`、`application/port/UnderwritingSystemPort` | `infrastructure/acl/LoggingUnderwritingSystem` | `interfaces/web/UnderwritingCallbackController` |
| 审核交付与签收完成 | `domain/claimcase`（`ClaimCaseRelation` 追加事实）、`domain/claim`（`WAIT_AUDIT → COMPLETED` 状态迁移） | `application/process/{AuditDeliveryUseCase,AuditSignedUseCase,AuditSignedTransactionService}`、`application/port/AuditSystemPort` | `infrastructure/persistence`、`infrastructure/acl/LoggingAuditSystem` | `interfaces/consumer/{AuditDeliveryConsumer,AuditSignedConsumer}` |
| 问题件 | `domain/claim/issue`（`ClaimApplicationIssue` 聚合内实体） | `application/process/{ClaimApplicationIssueIntakeUseCase,ClaimApplicationIssueCloseUseCase,ClaimApplicationIssueQueryUseCase}` | `infrastructure/persistence` | `interfaces/web/ClaimApplicationIssueIntakeController`、`ClaimApplicationIssueQueryController` |
| 详情查询 | — | `application/query/ClaimApplicationDetailQueryUseCase`、`application/port/ClaimApplicationDetailQuery` | `infrastructure/persistence/MyBatisClaimApplicationDetailQuery` | `interfaces/web/ClaimApplicationQueryController` |
| 异步集成 | — | `application/process/{OutboxRelayUseCase,ClaimApplicationCompensationUseCase}`、`application/process/compensation/*CompensationHandler`、`application/port/{OutboxEventRepository,OutboxEventPublisher,ConsumedEventRepository,InteractionLogRepository}` | `infrastructure/mq/RocketMqOutboxPublisher`、`infrastructure/persistence/MyBatis*Repository` | `interfaces/scheduler/{OutboxRelayScheduler,ClaimApplicationCompensationScheduler}` |

## 常用排查路径

| 问题 | 建议起点 | 后续阅读顺序 |
| --- | --- | --- |
| 签收请求为什么被拒绝 | `interfaces/web` 的异常映射 | `application/process/ClaimApplicationIntakeUseCase` → `domain/claim` → `infrastructure/persistence` |
| 材料为何导致受理失败 | `interfaces/consumer/MaterialProcessConsumer` | `application/process/MaterialProcessingUseCase` → `domain/claim` → `infrastructure/persistence` |
| 承保回调为什么未推进状态 | `interfaces/web/UnderwritingCallbackController` | `application/process/UnderwritingCallbackUseCase` → `domain/claim` → `application/port/UnderwritingSystemPort` |
| 消息没有发出或重复发送 | `interfaces/scheduler/OutboxRelayScheduler` | `application/process/OutboxRelayUseCase` → `application/port/{OutboxEventRepository,OutboxEventPublisher}` → `infrastructure/mq` |
| 问题件无法下发或关闭 | `interfaces/web/ClaimApplicationIssueIntakeController` | `application/process/ClaimApplicationIssue*UseCase` → `domain/claim` → `infrastructure/persistence` |
| 审核签收为什么未推进完成 | `interfaces/consumer/AuditSignedConsumer` | `application/process/AuditSignedUseCase` → `application/process/AuditSignedTransactionService` → `domain/claim`（`completeAudit`）→ `infrastructure/persistence` |
| 等待审核滞留为何未恢复 | `interfaces/scheduler/ClaimApplicationCompensationScheduler` | `application/process/ClaimApplicationCompensationUseCase` → `application/process/compensation/WaitAuditCompensationHandler` → `application/port/AuditSystemPort` → `infrastructure/acl/LoggingAuditSystem` |
| 详情查询为什么返回 404 | `interfaces/web/ClaimApplicationQueryController` | `application/query/ClaimApplicationDetailQueryUseCase` → `application/port/ClaimApplicationDetailQuery` → `infrastructure/persistence` |

# 并发控制

签收幂等、Redis 降级并发控制与唯一约束位于 `infrastructure/adapter`；加锁、快照查询与发号端口在 `application/port`；状态是否允许复用或重新申请位于 `domain/claim`；两者的组合流程位于 `application/process`。聚合写库使用 `t_claim_application.version` 乐观锁：`ClaimApplicationRepository.save` 以 `id + version` 条件更新，返回 `false` 时不写子表；签收保存冲突必须在保存事务之外的 `ClaimApplicationIntakeConflictResolver`（`REQUIRES_NEW`）中重读分类，避免 `REPEATABLE_READ` 读到旧快照。排查并发问题必须沿此顺序阅读，避免将基础设施降级策略误解为领域规则。

# 异步一致性

Outbox 事件模型与端口位于 `application/port` 与 `application/port/model`，投递编排位于 `application/process/OutboxRelayUseCase`，MyBatis-Plus 持久化实现位于 `infrastructure/persistence`，调度入口位于 `interfaces/scheduler`。消息契约位于 `shared/message`，消费者与幂等登记入口位于 `interfaces/consumer`。

# 演进原则

- 新增代码先按本导航判断归属；只有跨多个业务能力复用时才进入 `shared`。
- `domain` 只放业务模型与聚合 Repository；锁、发号、外部系统、Outbox 等技术能力端口放 `application/port`。
- 理赔申请只有一个聚合根 `ClaimApplication`：材料与问题件是聚合内实体，不设独立仓储；`ClaimCaseRelation` 是聚合外只追加事实。新增写操作必须通过根方法推进状态。
- 读场景使用 `application/query` 的读模型端口，不加载领域聚合、不写交互轨迹。
- 新增业务包时，必须同时增加 `package-info.java`，说明职责和依赖边界。
- 修改申请服务包依赖后，必须运行 `ClaimApplicationArchitectureTest`，确保不违反既有分层方向。
