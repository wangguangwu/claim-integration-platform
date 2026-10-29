# 004 材料处理与承保确认任务

> 2026-10-29 起功能定稿前不保留单元测试：下文任务提到的测试类已随该决定删除，端到端验证步骤说明与本地模拟能力保留，任务正文按当时交付记录保留。

## T1 领域端口、状态迁移与持久化能力

- 状态：已完成
- 对应 design：§数据模型、§并发控制、事务与异常处理
- 产物：`ClaimApplication` 生命周期迁移方法、`Material` 结果写入方法；`ClaimApplicationRepository`、`MaterialRepository`、`OutboxEventRepository` 的查询与条件更新方法；`ConsumedEventRepository` 端口与 MyBatis-Plus 实现；`ValidPolicyQuery`、`AutoUnderwritingRequester`、`UnderwritingResultQuery`、`MaterialFileProcessor` 端口；`PolicySnapshot`、`UnderwritingResult` 值对象；003 事件载荷补充 `traceId`
- 完成条件：状态迁移均为带期望状态与尝试次数的条件更新，影响行数 0 表示重复推进；材料结果只作用于未删除且处于 `PENDING_PROCESS` 的记录；持久化实现只使用 BaseMapper 与 Wrapper，不出现手写 SQL；不新增表和列

## T2 Outbox 投递器与 MQ 生产者

- 状态：已完成
- 对应 design：§MQ 与投递设计
- 产物：`OutboxRelayUseCase`、`OutboxEventPublisher` 端口、`RocketMqOutboxPublisher`、`OutboxRelayScheduler`、事件类型到 Topic 的路由表、投递配置项
- 完成条件：只有 `PENDING` 且到期的事件参与投递；并发抢占下同一事件只被一个执行者发送；发送成功后置 `PUBLISHED` 并写 `published_at`；失败按指数退避回到 `PENDING`，超过上限置 `FAILED`；信封字段与 002 契约完全一致；定时任务异常不中断后续调度

## T3 本地模拟文件系统与模拟承保实现

- 状态：已完成
- 对应 design：§本地模拟外部能力设计、§主要类职责
- 产物：`MockFileSystemStore`、`MockFileSystemController`、`MockUnderwritingStore`、`MockUnderwritingController`、`FileSystemFeignClient`、`UnderwritingFeignClient`、`FeignMaterialFileProcessor`/`FeignValidPolicyQuery`/`FeignAutoUnderwritingRequester`/`FeignUnderwritingResultQuery` 适配器、Feign 超时与模拟开关配置（`claim.mock.enabled`、`claim.mock.file-system`、`claim.mock.underwriting`）
- 完成条件：文件上传接口按源地址幂等并返回 `mock://` 模拟地址，可按材料类型模拟失败、按配置模拟延迟；保单登记、清除、查询、自动承保发起、承保结果查询、手工触发完成六类接口可用；承保数据与上传记录只存在于内存且不落库；自动承保按 `claimApplicationNo` 幂等；Feign 连接与读取超时生效；模拟开关关闭时不注册任何模拟接口

## T4 材料处理消费者与事务

- 状态：已完成
- 对应 design：§核心链路设计、§并发控制、事务与异常处理
- 产物：`MaterialProcessConsumer`、消息信封模型、`MaterialProcessingUseCase`、`MaterialProcessingTransactionService`、`ClaimApplicationLifecycleRecordFactory`
- 完成条件：消费前校验尝试次数与 `PENDING_ACCEPT` 状态，不匹配只记录 `REUSED` 轨迹并确认消息；全部材料成功推进 `ACCEPTED`，任一失败推进 `ACCEPT_FAILED`；外部失败保持 `PENDING_ACCEPT`；材料结果、状态、交互事件与消费登记同事务；重复消息不重复处理材料

## T5 承保分流与自动承保

- 状态：已完成
- 对应 design：§核心链路设计、§并发控制、事务与异常处理
- 产物：`UnderwritingUseCase`、`UnderwritingTransactionService`
- 完成条件：保单查询在事务外执行；存在有效保单时推进 `WAIT_AUDIT` 并写入 `AUDIT_DELIVERY_REQUESTED`；不存在时推进 `UNDERWRITING` 并在提交后发起自动承保；查询或发起失败保持原状态并记录轨迹；状态条件不满足时不发起任何外部调用

## T6 承保完成回调

- 状态：已完成
- 对应 design：§接口设计
- 产物：`UnderwritingCallbackController`、`UnderwritingCallbackUseCase`、回调专用错误码与异常处理器
- 完成条件：缺少 `claimApplicationNo` 或 `X-Request-Id` 返回 400；申请不存在返回 404；申请不在 `UNDERWRITING` 返回成功并记录 `REUSED`；申请在 `UNDERWRITING` 时同一事务推进 `WAIT_AUDIT`、写审核交付事件与 `COMPLETED` 轨迹；重复回调不新增事件

## T7 补偿任务

- 状态：已完成
- 对应 design：§MQ 与投递设计、§并发控制、事务与异常处理
- 产物：`ClaimApplicationCompensationUseCase`、补偿调度器与阈值配置
- 完成条件：`SENDING` 超时事件被重置为 `PENDING`；`ACCEPTED` 滞留超过阈值重试承保分流；`UNDERWRITING` 超时查询承保结果，`COMPLETED` 时推进 `WAIT_AUDIT`，查不到记录时重发自动承保请求并保持 `UNDERWRITING`；状态已变化的记录只记录日志，不改变数据

## T8 单元测试与集成测试

- 状态：已回退（2026-10-29 决定：功能未定稿前不保留单元测试，`src/test` 已全部删除；端到端验证步骤说明与模拟能力保留，功能定稿后按本任务产物与完成条件补齐）
- 对应 design：§核心链路设计、§MQ 与投递设计、§本地模拟外部能力设计
- 产物：投递抢占、退避重试、投递补偿、重复消费、陈旧事件、材料成功与失败、保单存在与不存在、自动承保发起、回调幂等、`ACCEPTED` 与 `UNDERWRITING` 补偿的单元测试；模拟文件系统与模拟承保的接口测试；本地 MQ 与 MySQL 的端到端验证步骤说明（`docs/testing/application-async-material-underwriting.md`）
- 完成条件：新增用例全部通过；覆盖 requirements 的正常场景、异常场景与边界条件；不通过修改业务规则让测试通过

## T9 构建与本地回归验证

- 状态：已完成
- 对应 design：§对现有功能的影响
- 产物：构建与本地验证记录
- 完成条件：已通过 JDK 21 下 `mvn -B -pl backend/claim-application-service -am test`（86 个测试）与 `mvn -B -q -DskipTests package`；本地验证签收后事件自动投递、材料处理、有效保单直达 `WAIT_AUDIT`、无保单进入 `UNDERWRITING` 并由模拟承保通知推进到 `WAIT_AUDIT`、关闭通知后补偿以 `compensation-<claimApplicationNo>` 请求标识推进、MQ 停机期间签收返回成功且就绪探针保持 UP、Broker 恢复后 Outbox 事件补齐投递、重复回调返回 `REUSED` 且不产生重复审核交付事件

## 后续变更

- 2026-10-29 [009 签收后异步链路](../009-post-intake-async-pipeline/design.md) 交付：本 Spec 的 `support/mock` 假件与四个 `Feign*` 外部适配器已删除，文件系统、承保系统与审核系统改为 `infrastructure/<域>/external` 的进程内本地实现；材料失败与承保超时的验证方式由 mock HTTP 接口改为 `claim.integration.local.*` 配置开关，验证步骤见 [签收后异步链路本地验证说明](../../docs/testing/application-async-material-underwriting.md)。本 Spec 的状态流转、交互轨迹与幂等口径不变。
