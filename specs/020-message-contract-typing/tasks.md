# 020 消息契约类型化 · 任务清单

## 任务批次

### Batch 1：契约类型

#### T1：密封消息接口与三类消息
- 产物：`ClaimApplicationEventMessage.java`、`OutboundClaimApplicationEventMessage.java`、`MaterialProcessMessage.java`、`MaterialProcessPayload.java`、`AuditDeliveryRequestedMessage.java`、`AuditSignedMessage.java`、`AuditSignedPayload.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/shared/contract/message/`
- 完成条件：
  - `ClaimApplicationEventMessage` 为 `sealed interface`，`permits` 出站接口与 `AuditSignedMessage`，只声明 `eventId`、`claimApplicationNo`、`tenantCode`、`traceId` 四个公共字段访问器
  - `OutboundClaimApplicationEventMessage` 为密封子接口，`permits` 材料处理与审核交付两个消息 record
  - `AuditDeliveryRequestedMessage` 恰好包含 4 个公共字段组件，不声明载荷
  - `MaterialProcessMessage` 为 4 个公共字段加 `MaterialProcessPayload`，载荷只含 `Integer claimApplicationAttempt`
  - `AuditSignedMessage` 为 4 个公共字段加 `eventType` 与 `AuditSignedPayload`，载荷只含 `caseNos`
  - 三类消息 record 与两个载荷 record 均标注 `@JsonIgnoreProperties(ignoreUnknown = true)`
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

#### T3：Outbox 存储载荷类型化
- 产物：新增 `MaterialProcessOutboxPayload.java`、`AuditDeliveryRequestedOutboxPayload.java`；修改 `ClaimApplicationLifecycleRecordFactory`
- 路径：`usecase/integration/outbox/model/`、`usecase/integration/record/`
- 完成条件：
  - 材料处理事件序列化 `MaterialProcessOutboxPayload`，字段仅 `claimApplicationAttempt`、`traceId`
  - 审核交付事件序列化 `AuditDeliveryRequestedOutboxPayload`，字段仅 `traceId`
  - 不再写入 `claimApplicationNo`、`tenantCode`、`eventType` 等重复字段
  - 两个存储载荷 record 标注 `@JsonIgnoreProperties(ignoreUnknown = true)`，兼容历史重复字段
  - `OutboxEvent`、事件版本落库、交互日志工厂逻辑不变
  - 不再出现 `Map<String, Object>` 载荷与 `Map.of`
  - 中文 JavaDoc，编译通过

#### T4：投递调度类型化
- 产物：修改 `OutboxRelayUseCase`
- 路径：`usecase/integration/outbox/`
- 完成条件：
  - `toEnvelope` 改为 `toMessage`，返回 `OutboundClaimApplicationEventMessage`，按 `ClaimApplicationEventType` 分成材料处理与审核交付两类
  - `traceId` 从存储载荷 JSON 读取，`null` 时回退 `eventId`
  - 材料处理读取 `MaterialProcessPayload`，申请尝试次数缺失保留 `null`；非法 JSON 或类型不符抛带 `eventId` 的 `IllegalStateException`
  - 审核交付不解析业务载荷，直接构造 4 字段消息
  - 删除 `occurredAt` 格式化逻辑
  - 未知事件类型、退避重试与失败标记逻辑不变
  - 编译通过

#### T5：投递端口与实现类型化
- 产物：修改 `OutboxEventPublisher`、`RocketMqOutboxPublisher`
- 路径：`usecase/integration/outbox/port/`、`infrastructure/integration/messaging/`
- 完成条件：
  - 端口方法参数为 `OutboundClaimApplicationEventMessage`
  - 绑定名由消息具体类型经穷尽 `switch` 选择：材料处理 → `materialProcess-out-0`，审核交付 → `auditDelivery-out-0`
  - 不再读取 `message.eventType()`
  - `StreamBridge.send` 返回 false 时抛出带 `bindingName` 与 `eventId` 的异常
  - 依赖方向不变，编译通过

### Batch 3：消费侧改造

#### T6：三个消费者绑定具体消息
- 产物：修改 `MaterialProcessConsumer`、`AuditDeliveryConsumer`、`AuditSignedConsumer`
- 路径：`interfaces/material/messaging/`、`interfaces/audit/messaging/`
- 完成条件：
  - 分别绑定 `Message<MaterialProcessMessage>`、`Message<AuditDeliveryRequestedMessage>`、`Message<byte[]>`
  - 审核签收绑定声明 `consumer.use-native-decoding: true`，报文由 `AuditSignedMessageParser` 严格解析
  - 材料处理消费者在载荷或 `claimApplicationAttempt` 为 `null` 时抛出异常
  - 审核交付消费者只使用 4 个公共字段，不访问载荷
  - 失败日志字段、级别与脱敏不变，编译通过

#### T7：审核签收装配器类型化
- 产物：新增 `AuditSignedMessageParser`；修改 `AuditSignedMessageAssembler`
- 路径：`interfaces/audit/messaging/`
- 完成条件：
  - 解析器使用平台 `ObjectMapper` 把原生字节解析为 `AuditSignedMessage`，空报文、非法 JSON、类型不符统一抛 `AUDIT_SIGNED_INVALID`
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
  - 无 `AuditDeliveryRequestedPayload`
  - 出站消息中无 `eventType`、`eventVersion`、`occurredAt`
  - 无残留旧 import、字段 key 常量与无用代码
  - 全量编译通过

### Batch 4：测试与文档

#### T9：测试迁移与新增
- 产物：修改 `AuditSignedMessageAssemblerTest`；新增 `AuditSignedMessageParserTest`、`MessageContractJsonTest`、`OutboxRelayUseCaseTest`、`OutboxEventPayloadTest`、`OutboxEventPublisherBindingTest`
- 完成条件：
  - 装配器测试改用 `AuditSignedMessage` 与 `AuditSignedPayload`，覆盖 design §九 列出的全部校验场景
  - 解析器测试覆盖完整信封忽略历史与契约外字段、数字案件号、非法 JSON、空报文
  - 契约测试断言三类消息的精确顶层字段与载荷字段集合，并断言出站消息不含 `eventType`、`eventVersion`、`occurredAt`
  - 契约测试验证带 `eventVersion`、`occurredAt` 的审核签收样例可反序列化，数字案件号被拒绝，未知字段被忽略
  - 存储载荷测试覆盖两个存储 record 的字段集合与历史重复字段读取
  - 投递转换测试覆盖三类事件类型、`traceId` 回退、材料处理缺少申请尝试次数、未知事件类型、非法载荷
  - 绑定测试覆盖材料处理与审核交付分别命中各自绑定
  - 既有分层 ArchUnit 规则全部通过

#### T10：契约与架构文档同步
- 产物：修改 `docs/architecture/ddd-standard-architecture.md`、`docs/integration/claim-application-integration-contract.md`
- 完成条件：
  - 架构文档跨域契约条目改为 `ClaimApplicationEventMessage` 与三类消息/两个载荷 record，说明公共字段只有 4 个
  - 集成契约文档更新材料处理与审核交付的出站样例，移除 `eventType`、`eventVersion`、`occurredAt` 与重复载荷字段
  - 集成契约文档注明审核签收入站仍可携带 `eventVersion`、`occurredAt`，平台忽略这两个字段
  - `docs/` 下文档不再出现 `ClaimApplicationEventEnvelope`（历史 spec 除外）
  - 确认 `docs/testing/audit-signed-and-issue-query.md` 的审核签收样例仍可正常发送与消费

### Batch 5：验证

#### T11：构建与运行栈回归
- 产物：无代码产物，输出验证结论
- 完成条件：
  - `JAVA_HOME` 指向 JDK 21 执行全量 Maven 构建成功
  - 现有测试、新增契约测试与 ArchUnit 规则全部通过
  - 运行栈回归材料处理首次签收与重新申请、审核交付、审核签收正常/重复/非法报文/DLQ
  - 核对三条 Topic、消费组、死信主题与 018 一致
  - 核对实际出站 JSON 字段集合与集成契约文档一致
  - 确认非法 JSON 与类型不符的消息同样进入重投与死信
  - 确认 `ClaimApplicationEventEnvelope`、`AuditDeliveryRequestedPayload` 与 Map 载荷无残留
