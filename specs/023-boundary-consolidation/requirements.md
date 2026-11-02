# 023 架构边界收口与冻结

## 背景与目标

021 已把技术能力端口从 `domain` 移到 `usecase`，022 已清零当时发现的五项架构偏差。本轮不再调整分层基线，只做边界收口、命名一致性与防腐化约束，目标是让目录结构可以冻结。

需要处理的事项：

1. `shared.contract` 中混入了只服务单一用例的输入模型。
2. 租户协议流水线在结构上只暴露解析、校验、转换三个节点，验签与解密仍是抽象类内的模板钩子。
3. 需要确认 `ClaimApplicationIntakePolicy` 属于业务规则还是应用控制参数。
4. `BusinessException` 作为全局基类被各层直接使用，异常语义容易退化。
5. `domain/claimcase` 只维护申请与案件的关联关系，容易被误读为独立的 Case 领域。

目标：

- `shared` 只保留真正跨模块复用的契约。
- 租户协议流水线以五个显式阶段装配，验签与解密具备可替换端口，默认实现不改变现有行为。
- `domain` 与 `usecase` 分别使用本层异常类型；`BusinessException` 只作为传输无关基类由入站适配层直接使用。
- 案件关联归入 `claim` 聚合所在领域，目录名与职责一致。
- ArchUnit 锁定新的依赖与异常边界，文档与代码同步。

## 业务规则

1. 不改变任何业务规则、状态机、错误码字符串、HTTP 状态映射与响应结构。
2. 不改变 MQ Topic、消费组、事件类型与消息 JSON 结构。
3. 验签与解密默认实现必须是无副作用的直通实现，现有 tenant-a / tenant-b 报文行为不变。
4. 异常类型调整只改变 Java 类型层级，不改变对外错误码与提示信息。
5. 案件关联迁移只改变包名与 import，不改变数据库表、字段与读写语义。

## 正常场景

- `UnderwritingCompletedCommand` 位于 `usecase/underwriting/command`，`shared.contract` 不再包含用例输入模型。
- 租户协议流水线按“验签 → 解密 → 解析 → 校验 → 转换”执行，五个阶段均以接口装配，默认验签/解密直通。
- `ClaimApplicationIntakePolicy` 保留在 `usecase/intake/port`，注释明确其为应用控制策略。
- `domain` 抛出的业务拒绝统一为 `DomainException`，`usecase` 统一为 `ApplicationException`，`interfaces` 可继续直接使用 `BusinessException`。
- `ClaimCaseRelation` 与 `ClaimCaseRelationRepository` 位于 `domain/claim` 与 `domain/claim/port`；持久化实现位于 `infrastructure/claim/persistence`。
- 全量构建、ArchUnit 与既有单元测试通过；签收、材料、承保、审核签收、问题件链路行为不变。

## 异常场景与边界条件

- `domain` 或 `usecase` 直接依赖 `shared.contract.BusinessException`：ArchUnit 失败。
- `shared` 反向依赖 `domain`、`usecase`、`interfaces` 或 `infrastructure`：ArchUnit 失败。
- 租户协议阶段接口或默认实现反向依赖用例编排：ArchUnit 或编译失败。
- 迁移后遗留 `domain/claimcase`、`infrastructure/claimcase` 目录或旧 import：构建失败，必须清理。
- 新异常类型若改变 `getErrorCode()` 的返回或消息文本：视为行为变更，禁止合入。
- 验签/解密默认实现若读写请求上下文或改变报文内容：视为行为变更，禁止合入。

## 本次范围

- `UnderwritingCompletedCommand` 包迁移与引用更新。
- 租户协议 `core/stage` 新增验签、解密端口与默认实现，流水线与租户实现同步装配。
- `ClaimApplicationIntakePolicy` 注释澄清，不移动。
- 新增 `DomainException`、`ApplicationException`，迁移 `domain`、`usecase` 的抛出点。
- `domain/claimcase` 合并至 `domain/claim`，`infrastructure/claimcase` 改为 `infrastructure/claim`。
- `ClaimApplicationArchitectureTest` 增加 `shared` 与异常使用规则。
- 架构文档、导航文档、自检口径与 Spec 022 的路径引用同步。
- 全量构建、测试与运行栈回归。

## 非本次范围

- 不把 `usecase` 改名为 `application`。
- 不移动聚合 Repository，不新增领域事件驱动 Outbox。
- 不重排 `interfaces`、`infrastructure` 的其他目录。
- 不修改业务规则、状态机、错误码、HTTP/MQ 契约与数据库结构。
- 不引入真实验签算法、密钥管理或租户证书；本轮只提供端口与默认直通实现。
- 不新增业务功能，不新增框架依赖。

## 验收标准

1. `shared.contract` 只包含 `BusinessErrorCode`、`BusinessException`、`ClaimApplicationAsyncErrorCode`、`ClaimApplicationEventEnvelope` 四类跨模块契约。
2. `UnderwritingCompletedCommand` 仅存在于 `usecase/underwriting/command`，全仓无旧包引用。
3. 租户协议五个阶段均有显式接口；默认验签/解密实现不改变报文；tenant-a、tenant-b 链路行为一致。
4. `ClaimApplicationIntakePolicy` 仍在 `usecase/intake/port`，注释明确为应用控制策略。
5. `domain` 无 `new BusinessException`，`usecase` 无 `new BusinessException`；两者分别使用 `DomainException`、`ApplicationException`。
6. `domain/claimcase` 与 `infrastructure/claimcase` 不存在；案件关联读写路径正常。
7. ArchUnit 新增规则通过，并能分别拦截 `shared` 反向依赖、domain/usecase 直接使用基类异常。
8. 全量 Maven 构建、既有测试与运行栈黄金路径通过，对外契约不变。
