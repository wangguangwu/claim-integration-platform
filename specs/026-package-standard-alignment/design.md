# 026 包结构标准对齐设计

## 1. 目标结构

```text
com.wangguangwu.claim.application
├── interfaces
│   ├── web                 控制器、统一响应与异常处理、响应渲染、租户协议流水线
│   ├── consumer            MQ 消费者、报文解析与装配
│   ├── dto                 HTTP 请求与响应模型
│   └── scheduler           Outbox 投递与补偿调度入口
├── application
│   ├── command             用例入参命令
│   ├── query               查询用例与读模型
│   ├── port                出站端口
│   ├── process             用例编排、事务服务、结果模型与记录装配
│   └── exception           应用异常与异步错误码
├── domain
│   └── claim               聚合根、聚合内实体、值对象、状态枚举
│       ├── port            聚合仓储
│       └── exception       领域错误码
├── infrastructure
│   ├── persistence         DO、ORM Mapper、Converter、仓储实现与读模型实现
│   ├── adapter             Redis、Redisson、发号、锁与熔断等技术适配
│   ├── acl                 审核、承保、文件等外部系统实现
│   ├── mq                  消息发布实现
│   └── config              配置属性与框架配置
└── shared
    ├── exception           传输无关业务异常基类与错误码接口
    ├── message             跨系统消息契约
    └── util                无业务归属工具
```

## 2. 迁移映射

| 原位置 | 新位置 |
| --- | --- |
| `interfaces/<域>/web`、`interfaces/claim/web` | `interfaces/web` |
| `interfaces/<域>/messaging`、`interfaces/audit/messaging` | `interfaces/consumer` |
| `interfaces/<域>/web/render`、`interfaces/issue/web/render` | `interfaces/web/render` |
| 各租户 request/response、`ApiResponse` | `interfaces/dto` |
| `interfaces/integration/scheduling` | `interfaces/scheduler` |
| `usecase/<域>/command` | `application/command` |
| `usecase/query`、`usecase/query/model` | `application/query`、`application/query/model` |
| `usecase/<域>/port`、`usecase/integration/*/port` | `application/port` |
| `usecase/<域>/model`、`usecase/integration/*/model` | `application/port/model` |
| `usecase/<域>` 其余类、`usecase/integration/*` 编排类 | `application/process` |
| `usecase/exception` | `application/exception` |
| `domain/claim/material`、`domain/claim/issue`、`domain/claim/person` | `domain/claim`（扁平） |
| `domain/claim/exception` | `domain/claim/exception` |
| `domain/exception/DomainException` | `shared/exception` |
| `shared/contract` 异常与错误码接口 | `shared/exception` |
| `shared/contract/message` | `shared/message` |
| `shared/infrastructure/Sha256Digest` | `shared/util` |
| `support/config` | `infrastructure/config` |
| `infrastructure/claim/persistence`、`infrastructure/query/persistence` 等持久化实现 | `infrastructure/persistence` |
| `infrastructure/<域>/external`、`infrastructure/integration/local` | `infrastructure/acl` |
| `infrastructure/<域>/cache`、`lock`、`number`、`resilience` | `infrastructure/adapter` |
| `infrastructure/integration/messaging` | `infrastructure/mq` |
| `infrastructure/integration/config`、`infrastructure/<域>/config` | `infrastructure/config` |

## 3. 分层与依赖规则

- `shared` 不依赖 `domain`、`application`、`interfaces`、`infrastructure`。
- `domain` 不依赖 `application`、`interfaces`、`infrastructure`，统一抛 `DomainException`。
- `application` 不依赖 `interfaces`、`infrastructure`，统一抛 `ApplicationException`。
- `infrastructure` 不依赖 `interfaces`；对 `application` 只允许依赖 `port`、`port/model`、`query/model`。
- `application/port`、`application/port/model`、`application/query/model` 不得反向依赖 `process`、`command`、`query` 用例与 `exception`。
- `interfaces` 不依赖 `infrastructure`；所有 `@RestController` 必须实现 `ApiErrorCodeProvider`。
- 查询用例与读模型实现不得依赖 `ClaimApplicationRepository` 或 `InteractionLogRepository`。
- 材料与问题件的状态迁移只能由 `ClaimApplication` 聚合根触发，反水化工厂只允许 `domain/claim` 与 `infrastructure/persistence` 调用。
- 外部系统端口实现只允许位于 `infrastructure/acl`；`infrastructure/adapter` 只承载 Redis、Redisson、发号、锁、熔断与本地批次号等技术适配，不承接外部系统交互。

规则统一由 [ClaimApplicationArchitectureTest](../../backend/claim-application-service/src/test/java/com/wangguangwu/claim/application/ClaimApplicationArchitectureTest.java) 校验。

## 4. 主要类职责

| 角色 | 包 | 说明 |
| --- | --- | --- |
| 控制器 | `interfaces/web` | 解析 HTTP 请求并调用应用用例 |
| MQ 消费者 | `interfaces/consumer` | 解析消息并调用应用用例，负责消费幂等入口 |
| 调度入口 | `interfaces/scheduler` | 触发 Outbox 投递与滞留补偿用例 |
| DTO | `interfaces/dto` | 对外请求与响应结构 |
| 命令 | `application/command` | 用例入参，不含协议细节 |
| 查询用例与读模型 | `application/query`、`application/query/model` | 只读查询，不加载聚合 |
| 出站端口与端口模型 | `application/port`、`application/port/model` | 锁、发号、缓存、外部系统、Outbox 等能力契约 |
| 用例编排与事务服务 | `application/process` | 事务边界、幂等与端口调用顺序 |
| 应用异常 | `application/exception` | `ApplicationException` 与异步错误码 |
| 聚合与领域模型 | `domain/claim` | 唯一聚合根 `ClaimApplication`，材料与问题件为聚合内实体 |
| 聚合仓储 | `domain/claim/port` | `ClaimApplicationRepository`、`ClaimCaseRelationRepository` |
| 持久化实现 | `infrastructure/persistence` | DO、ORM Mapper、Converter、仓储与读模型 |
| 技术适配 | `infrastructure/adapter` | Redis 快照、Redis 与 MySQL 发号、Redisson 锁、熔断、UUID 批次号 |
| 外部系统实现 | `infrastructure/acl` | 审核、承保、文件服务的日志实现（未接入真实系统，只打印调用日志） |
| 消息发布 | `infrastructure/mq` | RocketMQ Outbox 发布 |
| 配置 | `infrastructure/config` | `@ConfigurationProperties` 与框架装配 |

## 5. 数据与接口影响

无数据库结构变更，无 HTTP 路径变更，无消息主题与载荷变更。Spring Boot 主类位于根包，重排后的子包仍在组件扫描范围内；MyBatis-Plus 继续使用 `@Mapper` 接口，不新增 XML 与 `@MapperScan`。

## 6. 对现有功能的影响

- 运行时行为与对外契约保持不变。
- 测试类随包迁移，测试内容不变；架构测试规则按新包名重写。
- 文档中的目录路径同步更新，避免后续开发按旧路径定位。

## 7. 风险与处理

- **包声明与目录不一致**：迁移后统一编译校验，并用脚本核对 `package` 与物理路径。
- **同一包拆分为多个新包后缺少 import**：以编译错误为准逐个补齐；同包 import 统一清理。
- **组件扫描失效**：主类保持在根包，迁移后启动服务验证健康检查与接口。
- **配置绑定失效**：配置类仅移动包名，`@ConfigurationProperties` 前缀不变，启动后验证调度与外部适配可用。
