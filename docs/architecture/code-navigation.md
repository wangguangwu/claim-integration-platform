# 项目背景与目标

本文档提供源码导航入口，帮助开发人员按业务能力和分层职责定位代码。当前项目保持 DDD 分层目录；当需要理解一条业务链路时，通过下表跨层定位，而不是依赖文件名猜测。

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
interfaces → usecase → domain ← infrastructure
```

该方向由 `ClaimApplicationArchitectureTest` 在 Maven 测试阶段持续校验；新增代码不得通过跨层 import 绕过领域端口或用例编排。

- `interfaces`：入站适配器，只处理 HTTP、消息、调度、协议和响应映射。
- `usecase`：应用编排，负责事务、幂等结果和端口调用顺序，同时拥有出站能力端口、配置策略端口、端口模型与应用异常（`ApplicationException`）。
- `domain`：业务状态机、聚合根与聚合内实体、值对象、聚合外追加事实、聚合 Repository 和领域异常（`DomainException`），不依赖具体技术实现。
- `infrastructure`：MyBatis-Plus、Redis、Redisson、MQ 存储和第三方系统适配，实现 `usecase` 端口与 `domain` 聚合 Repository。

# 核心链路设计

## 理赔申请服务目录职责

| 目录 | 进入条件 | 主要内容 | 不应放置的内容 |
| --- | --- | --- | --- |
| `domain` | 要定义业务事实、状态规则或聚合持久化契约 | 聚合根、聚合内实体、值对象、聚合外追加事实、聚合 Repository、状态/错误码枚举、`DomainException` | 技术能力端口、Spring Controller、MyBatis Mapper、Redis 客户端调用 |
| `usecase` | 要编排一次完整业务操作 | 用例、事务服务、命令与结果、出站端口、配置策略、端口模型、`ApplicationException` | HTTP 注解、数据库对象、第三方 SDK 调用、业务不变式 |
| `infrastructure` | 要接入具体技术或外部系统 | MyBatis-Plus 仓储、DO/Mapper、Redis、第三方适配 | 业务状态机和接口响应映射 |
| `interfaces` | 要承接外部输入或对外调度 | Controller、Consumer、Scheduler、租户协议流水线 | 领域规则和持久化实现 |
| `shared` | 确属跨业务能力复用 | 事件契约、无业务归属组件 | 单一业务域模型、HTTP 请求或响应模型 |
| `support` | 要装配技术框架 | MyBatis-Plus、Redisson 配置 | 业务参数和业务决策 |

## 按业务能力定位

| 业务能力 | 领域模型 | 应用端口与编排 | 基础设施实现 | 入站入口 |
| --- | --- | --- | --- | --- |
| 申请签收 | `domain/claim`（`ClaimApplication` 聚合根） | `usecase/intake`、`usecase/intake/{port,model}` | `infrastructure/intake`、`infrastructure/claim/persistence` | `interfaces/intake/web`、`interfaces/intake/tenantprotocol` |
| 材料处理 | `domain/claim/material`（聚合内实体） | `usecase/material`、`usecase/material/{port,model}` | `infrastructure/material/{external,number}` | `interfaces/material/messaging` |
| 自动承保 | `domain/claim` 的状态推进 | `usecase/underwriting`、`usecase/underwriting/{port,model}` | `infrastructure/underwriting` | `interfaces/underwriting/web` |
| 审核交付 | `domain/claim`（`ClaimCaseRelation` 追加事实） | `usecase/audit`、`usecase/audit/{port,model}` | `infrastructure/claim/persistence`、`infrastructure/audit` | `interfaces/audit/messaging` |
| 问题件 | `domain/claim/issue`（聚合内实体） | `usecase/issue` | `infrastructure/claim/persistence` | `interfaces/issue/web` |
| 详情查询 | — | `usecase/query`、`usecase/query/{port,model}` | `infrastructure/query/persistence` | `interfaces/claim/web` |
| 异步集成 | — | `usecase/integration/{outbox,consumption,compensation,interaction}` | `infrastructure/integration` | `interfaces/integration/scheduling` |

## 常用排查路径

| 问题 | 建议起点 | 后续阅读顺序 |
| --- | --- | --- |
| 签收请求为什么被拒绝 | `interfaces/intake/web` 的异常映射 | `usecase/intake` → `domain/claim` → `infrastructure/claim/persistence` |
| 材料为何导致受理失败 | `interfaces/material/messaging` | `usecase/material` → `domain/claim/material` → `infrastructure/claim/persistence` |
| 承保回调为什么未推进状态 | `interfaces/underwriting/web` | `usecase/underwriting` → `domain/claim` → `usecase/underwriting/port` |
| 消息没有发出或重复发送 | `interfaces/integration/scheduling` | `usecase/integration/outbox` → `usecase/integration/outbox/{model,port}` → `infrastructure/integration/persistence/outbox` |
| 问题件无法下发或关闭 | `interfaces/issue/web` | `usecase/issue` → `domain/claim/issue` → `infrastructure/claim/persistence` |
| 详情查询为什么返回 404 | `interfaces/claim/web` | `usecase/query` → `usecase/query/port` → `infrastructure/query/persistence` |

# 并发控制

签收幂等、Redis 降级并发控制与唯一约束位于 `infrastructure/intake`；加锁、快照查询与发号端口在 `usecase/intake/port`；状态是否允许复用或重新申请位于 `domain/claim`；两者的组合流程位于 `usecase/intake`。聚合写库使用 `t_claim_application.version` 乐观锁：`ClaimApplicationRepository.save` 以 `id + version` 条件更新，返回 `false` 时不写子表；签收保存冲突必须在保存事务之外的 `ClaimApplicationIntakeConflictResolver`（`REQUIRES_NEW`）中重读分类，避免 `REPEATABLE_READ` 读到旧快照。排查并发问题必须沿此顺序阅读，避免将基础设施降级策略误解为领域规则。

# 异步一致性

Outbox 事件模型、状态机与端口位于 `usecase/integration/outbox/{model,port}`，投递编排位于 `usecase/integration/outbox`，MyBatis-Plus 持久化实现位于 `infrastructure/integration/persistence/outbox`，调度入口位于 `interfaces/integration/scheduling`。消息消费幂等登记遵循同样的分层路径。

# 演进原则

- 当前保持分层优先，避免一次性移动大量包和 import。
- 单个业务域出现持续增长、跨层跳转严重影响开发时，再单独评估纵向切片迁移。
- 新增代码先按本导航判断归属；只有跨多个业务能力复用时才进入 `shared`。
- `domain` 只放业务模型与聚合 Repository；锁、发号、外部系统、Outbox 等技术能力端口放 `usecase/<域>/port`。
- 理赔申请只有一个聚合根 `ClaimApplication`：材料与问题件是聚合内实体，不设独立仓储；`ClaimCaseRelation` 是聚合外只追加事实。新增写操作必须通过根方法推进状态。
- 读场景使用 `usecase/query` 的读模型端口，不加载领域聚合、不写交互轨迹。
- 新增顶层或关键业务包时，必须同时增加 `package-info.java`，说明职责和依赖边界。
- 修改申请服务包依赖后，必须运行 `ClaimApplicationArchitectureTest`，确保不违反既有分层方向。
