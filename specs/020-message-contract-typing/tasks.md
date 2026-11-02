# 020 消息契约类型化 · 任务清单

## 任务批次

### Batch 1：契约类型

#### T1：密封消息接口与三类消息
- 产物：`ClaimApplicationEventMessage.java`、`OutboundClaimApplicationEventMessage.java`、`MaterialProcessMessage.java`、`MaterialProcessPayload.java`、`AuditDeliveryRequestedMessage.java`、`AuditDeliveryRequestedPayload.java`、`AuditSignedMessage.java`、`AuditSignedPayload.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/shared/contract/message/`
- 完成条件：
  - `ClaimApplicationEventMessage` 为 `sealed interface`，`permits` 出站接口与 `AuditSignedMessage`，只声明七个公共字段访问器
  - `OutboundClaimApplicationEventMessage` 为密封子接口，`permits` 材料处理与审核交付两个消息 record
  - 三个消息 record 实现各自接口，组件与 design §二 表格一致
  - 三个载荷 record 组件与 018 契约逐字段一致，`claimApplicationAttempt` 使用 `Integer`
  - 六个 record 均标注 `@JsonIgnoreProperties(ignoreUnknown = true)`
  - 中文 JavaDoc，编译通过

#### T2：案件号严格反序列化
- 产物：`StrictStringListDeserializer.java`
- 路径：`shared/contract/message/`
- 完成条件：
  - `AuditSignedPayload#caseNos` 声明 `@JsonDeserialize(using = StrictStringListDeserializer.class)`
  - JSON 数组元素不是字符串时转换失败，不把数字或布尔值静默转成字符串
  - `null` 与空数组由装配器处理，不在反序列化阶段抛错
  - 中文 JavaDoc，编译通过

### Batch 2：生产侧改造

#### T3：Outbox 载荷类型化
- 产物：修改 `ClaimApplicationLifecycleRecordFactory`
- 路径：`usecase/integration/record/`
- 完成条件：
  - 材料处理事件序列化 `MaterialProcessPayload`，审核交付事件序列化 `AuditDeliveryRequestedPayload`
  - JSON 字段名与现有 `Map` 载荷完全一致
  - `OutboxEvent`、事件版本、交互日志工厂逻辑不变
  - 不再出现 `Map<String, Object>` 载荷与 `Map.of`
  - 编译通过

#### T4：投递调度类型化
- 产物：修改 `OutboxRelayUseCase`
- 路径：`usecase/integration/outbox/`
- 完成条件：
  - `toEnvelope` 改为 `toMessage`，返回 `OutboundClaimApplicationEventMessage`，按 `ClaimApplicationEventType` 分成材料处理与审核交付两类
  - 载荷用 `ObjectMapper#readValue` 读取为对应 record，非法 JSON 或类型不符抛带 `eventId` 的 `IllegalStateException`
  - `traceId` 取载荷值，`null` 时回退 `eventId`；`occurredAt` 格式不变
  - 未知事件类型、退避重试与失败标记逻辑不变
  - 编译通过

#### T5：投递端口与实现类型化
- 产物：修改 `OutboxEventPublisher`、`RocketMqOutboxPublisher`
- 路径：`usecase/integration/outbox/port/`、`infrastructure/integration/messaging/`
- 完成条件：
  - 端口方法参数为 `OutboundClaimApplicationEventMessage`
  - 绑定名仍由 `message.eventType()` 解析 `ClaimApplicationEventType` 得到
  - `StreamBridge.send` 返回 false 时抛出带 `bindingName` 与 `eventId` 的异常
  - 依赖方向不变，编译通过

### Batch 3：消费侧改造

#### T6：三个消费者绑定具体消息
- 产物：修改 `MaterialProcessConsumer`、`AuditDeliveryConsumer`、`AuditSignedConsumer`
- 路径：`interfaces/material/messaging/`、`interfaces/audit/messaging/`
- 完成条件：
  - 分别绑定 `Message<MaterialProcessMessage>`、`Message<AuditDeliveryRequestedMessage>`、`Message<AuditSignedMessage>`
  - 材料处理消费者在 `claimApplicationAttempt` 为 `null` 时抛出异常
  - 审核交付消费者仍只使用公共字段
  - 失败日志字段、级别与脱敏不变，编译通过

#### T7：审核签收装配器类型化
- 产物：修改 `AuditSignedMessageAssembler`
- 路径：`interfaces/audit/messaging/`
- 完成条件：
  - `toCommand` 接收 `AuditSignedMessage`
  - 保留 `eventId`、`claimApplicationNo`、`tenantCode` 非空白校验与事件类型校验
  - 保留案件号非空、去空白、非空、64 字符上限、去重规则
  - 删除 `Map` 参数、字符串 key 常量与 `instanceof String` 判断
  - 错误码仍为 `AUDIT_SIGNED_INVALID`，中文 JavaDoc 同步，编译通过

#### T8：删除旧信封与 Map 解析
- 产物：删除 `ClaimApplicationEventEnvelope`；清理全仓引用
- 完成条件：
  - 全仓无 `ClaimApplicationEventEnvelope` 引用（历史 spec 文档除外）
  - 生产与消费代码无 `payload.get(...)`、无 `Map<String, Object>` 消息载荷
  - 无残留旧 import、字段 key 常量与无用代码
  - 全量编译通过

### Batch 4：测试与文档

#### T9：测试迁移与新增
- 产物：修改 `AuditSignedMessageAssemblerTest`；新增 `MessageContractJsonTest`、`OutboxRelayUseCaseTest`
- 完成条件：
  - 装配器测试改用 `AuditSignedMessage` 与 `AuditSignedPayload`，覆盖 design §九 列出的全部校验场景
  - 契约测试覆盖三类消息序列化字段集合、契约文档样例反序列化、数字案件号拒绝、未知字段容忍
  - 投递转换测试覆盖三类事件类型、`traceId` 回退、未知事件类型、非法载荷
  - 既有分层 ArchUnit 规则全部通过

#### T10：架构文档同步
- 产物：修改 `docs/architecture/ddd-standard-architecture.md`
- 完成条件：
  - 跨域契约条目改为 `ClaimApplicationEventMessage` 与三类消息/载荷 record
  - 说明消息类型集合由密封接口封闭、载荷使用 record、对外 JSON 不变
  - `docs/` 下文档不再出现 `ClaimApplicationEventEnvelope`（历史 spec 除外）
  - 确认 `docs/integration/claim-application-integration-contract.md` 的 Topic 表、信封样例与审核签收样例无需修改

### Batch 5：验证

#### T11：构建与运行栈回归
- 产物：无代码产物，输出验证结论
- 完成条件：
  - `JAVA_HOME` 指向 JDK 21 执行全量 Maven 构建成功
  - 现有测试、新增契约测试与 ArchUnit 规则全部通过
  - 运行栈回归材料处理首次签收与重新申请、审核交付、审核签收正常/重复/非法报文/DLQ
  - 核对三条 Topic、消费组、死信主题与 018 一致
  - 确认非法 JSON 与类型不符的消息同样进入重投与死信
  - 确认 `ClaimApplicationEventEnvelope` 与 Map 载荷无残留
