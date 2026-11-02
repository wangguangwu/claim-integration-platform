# 021 DDD 分层边界基线调整

## 背景与目标

当前 `claim-application-service` 的分层骨架是 `domain / usecase / interfaces / infrastructure`，方向正确，但 `domain` 的边界过宽：加锁、发号、签收快照查询、外部系统调用、Outbox、消费去重、补偿策略与交互轨迹都放在 `domain`。这些类不是业务人员能理解的理赔业务概念，而是流程编排和技术能力。

更关键的是，现有 [标准 DDD 架构说明](../../docs/architecture/ddd-standard-architecture.md) 明确规定“端口在 domain，实现留在 infrastructure”，并记录了曾经把 `OutboxEventPublisher` 从 usecase 移回 domain 的修正。本次不是修一个“写错的包”，而是**调整架构基线**：把端口与查询模型的所有权从 domain 移到 usecase，并同步 ArchUnit、文档与代码，避免三套规则并存。

目标：

- `domain` 只保留聚合、值对象、状态枚举、领域事件、领域服务、聚合 Repository 抽象、业务异常与错误码。
- `usecase` 承担业务流程编排、事务边界、Command/Result、查询模型、出站能力端口与配置策略。
- `interfaces` 与 `infrastructure` 的职责和包布局保持不变；`infrastructure` 通过实现 `usecase` 端口与 `domain` 仓储提供能力。
- 只调整职责归属和包依赖，不改变任何业务行为、事务边界、消息语义与对外契约。

## 架构规则

1. `domain` 只允许出现以下内容：聚合根与实体、值对象、状态枚举、领域事件、领域服务、聚合 Repository 接口、业务异常与错误码。
2. `usecase` 允许出现：UseCase、TransactionService、Factory、Command、Result、查询模型、出站能力端口、配置策略端口。
3. `interfaces` 保持现有结构，只做 HTTP、MQ、回调、租户协议与调度等入站适配，不因本次调整移动包。
4. `infrastructure` 保持现有包布局，只修改端口实现声明与 import；不按本次调整重排基础设施目录。
5. 依赖方向固定为：`interfaces → usecase → domain`；`infrastructure → usecase.port/model` 与 `infrastructure → domain.repository`；`domain` 不依赖 `usecase`、`interfaces`、`infrastructure`；`infrastructure` 不依赖 `usecase` 的业务编排实现，也不依赖 `interfaces`。
6. 跨层契约只通过 `usecase/<域>/port` 接口与 `usecase/<域>/model` 模型暴露；`infrastructure` 允许依赖这两个包，其他 `usecase` 包一律禁止。
7. 端口接口统一放在 `usecase/<域>/port`；端口与查询模型统一放在 `usecase/<域>/model`。配置策略（`*Policy`）也是出站端口，放进 `port`，不单独建 `policy` 包。
8. 聚合 Repository 接口留在 `domain/<聚合>/port`；`domain/issue` 的 Repository 同步收进 `domain/issue/port`，统一包形态。
9. 消息 Outbox 仍由事务服务直接写入，本次不引入领域事件驱动 Outbox；`IssueDomainEventPublisher` 作为领域事件端口保留在 `domain/issue/event`。
10. `usecase` 不改名为 `application`：根包已经是 `com.wangguangwu.claim.application`，继续改名会产生 `application.application`，并使 ArchUnit 的包匹配失效。
11. `UnderwritingResult` 只是外部承保系统的三值查询状态，不是领域值对象，随承保端口一起移到 `usecase/underwriting/model`。
12. 搬迁按四组执行：签收端口、外部能力端口、异步集成内核、Domain 清理；每组独立可编译、可测试，不做一次性机械搬包。
13. 架构文档、ArchUnit 规则与代码必须在 021 合并前达到同一基线；历史 spec 不重写，020 的路径引用随本次调整同步更新。
14. 本次不修改任何业务逻辑、事务边界、状态机、消息 Topic、事件类型、JSON 结构、HTTP 状态码、数据库表与 Redis 键。

## 正常场景

- 四组搬迁完成后，`domain` 只剩业务模型、领域事件、领域服务、Repository 与错误码，空包 `domain/audit`、`domain/underwriting`、`domain/integration` 被删除。
- `usecase` 通过自己的端口调用锁、发号、快照缓存、外部系统、Outbox、消费去重、补偿策略与交互轨迹，`infrastructure` 实现这些端口。
- 全量构建、既有单元测试与调整后的 ArchUnit 规则通过。
- 运行栈上签收、材料处理、承保、审核交付、审核签收、问题件、Outbox 投递、补偿与死信链路行为不变。

## 异常场景与边界条件

- `infrastructure` 依赖了 `usecase` 的 UseCase、TransactionService 或其他编排类：ArchUnit 失败，禁止合入。
- `domain` 反向依赖 `usecase`、`interfaces` 或 `infrastructure`：ArchUnit 失败，禁止合入。
- 端口或模型类反向依赖本域编排类：ArchUnit 失败，禁止合入。
- 搬迁后遗留旧包引用、空目录或过期 `package-info`：构建或文档自检失败，必须清理。
- Spring 组件扫描、MyBatis Mapper 扫描与配置绑定均以根包或 `infrastructure` 包为范围，本次不移动 `infrastructure`，不得改变扫描范围。
- 搬迁类若存在包级私有访问或同名类冲突，编译失败，必须在对应分组内解决；不得通过放宽可见性掩盖问题。
- 消息 Outbox、消费幂等、补偿调度与死信行为不在本次调整范围；搬迁前后必须逐项一致。

## 本次范围

- `claim-application-service` 的 `domain`、`usecase` 包结构调整与 import 修复。
- `ClaimApplicationArchitectureTest` 的依赖规则调整。
- `ddd-standard-architecture.md`、`claim-application-architecture.md`、`code-navigation.md`、各层 `package-info.java` 的同步更新。
- `specs/020-message-contract-typing` 中旧包路径引用的同步更新。
- 全量构建、测试与运行栈回归。

## 非本次范围

- 不把 `usecase` 改名为 `application`，不调整根包。
- 不重排 `infrastructure` 目录，不修改 MyBatis Mapper、DO、Converter 与表结构。
- 不引入领域事件驱动 Outbox，不修改事务边界、事件产生时机与消费模型。
- 不修改 `interfaces` 的包结构、HTTP 接口、MQ 消费函数签名与租户协议实现。
- 不修改任何业务规则、状态机、错误码、响应结构与消息契约。
- 不重写历史 spec，不删除既有业务单元测试。
- 不新增业务功能，不新增框架依赖。

## 验收标准

1. `domain` 下只存在聚合、值对象、状态枚举、领域事件、领域服务、Repository 端口、业务异常与错误码；`domain/audit`、`domain/underwriting`、`domain/integration` 删除。
2. 所有技术能力端口与查询模型位于 `usecase/<域>/{port,model}`；`domain/claim/port`、`domain/material/port`、`domain/claimcase/port`、`domain/issue/port` 只保留 Repository。
3. `infrastructure` 只依赖 `usecase` 的 `port`/`model` 与 `domain`，不依赖 `usecase` 编排类或 `interfaces`。
4. 调整后的 ArchUnit 规则全部通过，且能分别拦截 domain 反向依赖、infrastructure 越界依赖与端口反向依赖。
5. 全量 Maven 构建与既有测试通过；020 的包路径引用已更新为 021 后的路径。
6. 运行栈回归覆盖签收、材料、承保、审核交付、审核签收、问题件、Outbox、补偿与死信，行为与 021 前一致。
7. 活文档不再出现“所有端口在 domain”的旧基线，架构说明、导航文档与实际包结构一致。
