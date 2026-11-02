# 023 架构边界收口与冻结 · 设计

## 1. 现状核对与裁定

| 事项 | 核对结果 | 裁定 |
| --- | --- | --- |
| `UnderwritingCompletedCommand` | 由承保回调 Controller、承保回调用例与补偿用例使用，是应用输入，不是 MQ 契约 | 移到 `usecase/underwriting/command`，保留 `Command` 名称 |
| 租户协议验签/解密 | `AbstractTenantProtocolPipeline` 已有 `verifySignature`、`decrypt` 模板钩子，但 `stage` 包没有对应端口 | 抽成两个显式阶段端口，默认实现直通；删除模板钩子，避免两套扩展点 |
| `ClaimApplicationIntakePolicy` | 只暴露 `uniqueKeyRetryTimes()`，来自部署配置 | 属于应用控制策略，保留 `usecase/intake/port`，仅补注释 |
| `BusinessException` | 19 个生产类引用，domain 1 处、usecase 5 处、interfaces 11 处、infrastructure 1 处 | 保留为传输无关基类；新增 `DomainException`、`ApplicationException`，限制 domain/usecase 直接使用基类 |
| `domain/claimcase` | 只有 `ClaimCaseRelation` 与 Repository，没有 Case 聚合、状态或生命周期 | 合并到 `domain/claim`，`infrastructure/claimcase` 同步改为 `infrastructure/claim` |

`shared.contract` 最终保留四类跨模块契约：错误码接口、业务异常基类、异步错误码、消息信封。其余仍属跨模块能力，不做进一步搬迁。

## 2. 改动模块

| 模块 | 改动 |
| --- | --- |
| `shared/contract` | 迁出 `UnderwritingCompletedCommand` |
| `usecase/underwriting/command` | 新增命令包并迁入 `UnderwritingCompletedCommand` |
| `interfaces/intake/tenantprotocol/core/stage` | 新增 `TenantProtocolSignatureVerifier`、`TenantProtocolDecryptor` 与两个默认实现 |
| `interfaces/intake/tenantprotocol/core/pipeline` | 流水线装配五个阶段，删除模板验签/解密方法 |
| `interfaces/intake/tenantprotocol/tenants/tenant_a,b` | 构造器装配默认验签/解密实现 |
| `domain/exception` | 新增 `DomainException` |
| `usecase/exception` | 新增 `ApplicationException` |
| `domain/claim` | 迁入 `ClaimCaseRelation`，Repository 迁入 `domain/claim/port` |
| `infrastructure/claim/persistence` | 由 `infrastructure/claimcase/persistence` 迁入四个持久化类 |
| `ClaimApplicationArchitectureTest` | 新增 `shared` 依赖规则与异常使用规则 |
| 架构文档 | 同步目录、阶段、异常与统计 |

## 3. 核心链路

### 3.1 租户协议五阶段

```text
rawPayload
  → TenantProtocolSignatureVerifier.verify(rawPayload)
  → TenantProtocolDecryptor.decrypt(rawPayload)
  → TenantProtocolParser.parse(plaintext)
  → TenantProtocolValidator.validate(request)
  → TenantProtocolConverter.convert(tenantCode, request)
  → ClaimApplicationIntakeCommand
```

- 验签/解密端口位于 `core/stage`，只接收 `String`，不依赖 Spring Web 或租户实现。
- `DefaultTenantProtocolSignatureVerifier` 不做任何事；`DefaultTenantProtocolDecryptor` 原样返回。
- tenant-a、tenant-b 构造器显式传入默认实现；未来租户可在自己的流水线中传入真实实现，不需要改抽象类。
- 解析节点的空对象校验仍在流水线内，错误码与消息保持不变。

### 3.2 异常层级

```text
RuntimeException
└── BusinessException            // 传输无关基类，入站适配可直接使用
    ├── DomainException          // domain 的业务拒绝
    └── ApplicationException     // usecase 的编排拒绝
```

- `DomainException` 位于 `domain/exception`；`ApplicationException` 位于 `usecase/exception`。
- 两者构造器与 `BusinessException` 一致，只透传错误码与消息，不增加行为。
- `WebExceptionHandler` 继续捕获 `BusinessException`，因此 HTTP 状态与响应结构不变。
- `interfaces` 直接使用的解析、协议、请求校验异常继续使用 `BusinessException`，不强行包装成领域异常。
- `infrastructure` 的唯一使用点（Redis 锁获取失败）属于技术适配拒绝，保留基类。

### 3.3 案件关联归属

```text
domain/claim
├── ClaimApplication
├── ClaimApplicationStatus
├── ClaimCaseRelation
└── port
    ├── ClaimApplicationRepository
    └── ClaimCaseRelationRepository
```

- `ClaimCaseRelation` 是申请与审核案件之间的事实关系，生命周期由申请侧用例触发，不构成独立聚合。
- 持久化实现整体迁到 `infrastructure/claim/persistence`，表名、字段、Mapper 泛型与 SQL 不变。
- `usecase/audit`、`usecase/issue` 改为引用 `domain/claim` 与 `domain/claim/port`。

## 4. ArchUnit 规则

在现有 7 条规则上新增：

1. `shared` 不得依赖 `domain`、`usecase`、`interfaces`、`infrastructure`。
2. `domain` 生产类不得依赖 `shared.contract.BusinessException`，`DomainException` 自身除外。
3. `usecase` 生产类不得依赖 `shared.contract.BusinessException`，`ApplicationException` 自身除外。
4. 现有 `domain`、`usecase`、`infrastructure`、`interfaces` 方向规则保持不变。

规则排除 `*Test` 类，避免测试断言基类异常时被误判；生产代码边界仍由规则锁定。

## 5. 数据、接口与基础设施

- 无数据库表、字段、索引变更。
- 无 HTTP 路径、请求/响应字段变更。
- 无 MQ Topic、消费组、事件类型、消息 JSON 变更。
- 无 Redis Key、配置项变更。
- 包迁移后 Spring 组件扫描与 MyBatis Mapper 扫描仍以根包为范围，不需要改扫描配置。

## 6. 文档与统计

- `ddd-standard-architecture.md`：更新包示例、五阶段流水线、异常层级、统计与自检项。
- `claim-application-architecture.md`：更新案件关联归属与异常说明。
- `code-navigation.md`：更新 claimcase 路径、异常位置与命令位置。
- `specs/022-architecture-deviation-cleanup`：同步被本轮再次移动的路径。
- 统计以构建后实测为准，任务 T6 记录最终类数、文件数与目录数。
