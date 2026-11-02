# 020 消息契约类型化 · 设计

## 一、改动模块

| 模块 | 改动 |
|---|---|
| `shared/contract/message` | 新增顶层密封接口 `ClaimApplicationEventMessage`、出站密封子接口、三类消息 record、两个载荷 record、案件号严格反序列化器；删除 `ClaimApplicationEventEnvelope` |
| `usecase/integration/outbox/model` | 新增两个 Outbox 存储载荷 record，承载消息之外的 `traceId` 与申请尝试次数 |
| `usecase/integration/outbox/port` | `OutboxEventPublisher#publish` 参数改为出站消息接口 |
| `usecase/integration/record` | 生命周期记录工厂用存储载荷 record 构造 Outbox 事件 |
| `usecase/integration/outbox` | 投递调度按事件类型把存储载荷转换为具体消息，`traceId` 取自存储载荷，不再生成 `occurredAt` |
| `infrastructure/integration/messaging` | StreamBridge 投递实现按消息具体类型选择绑定并发送 |
| `interfaces/material/messaging` | 材料处理消费者绑定具体消息类型 |
| `interfaces/audit/messaging` | 审核交付与审核签收消费者绑定具体消息类型；装配器接收类型化消息 |
| 测试与文档 | 契约 JSON 测试、装配器测试迁移、投递转换与绑定测试、集成契约与架构文档同步 |

## 二、契约结构

### 密封消息接口

```java
public sealed interface ClaimApplicationEventMessage
        permits OutboundClaimApplicationEventMessage, AuditSignedMessage {

    String eventId();

    String claimApplicationNo();

    String tenantCode();

    String traceId();
}

public sealed interface OutboundClaimApplicationEventMessage extends ClaimApplicationEventMessage
        permits MaterialProcessMessage, AuditDeliveryRequestedMessage {
}
```

顶层接口只声明三类消费者都实际使用的 4 个公共字段。`eventType` 只有审核签收需要，放在该消息自己的 record 上；`eventVersion`、`occurredAt` 没有任何消费方读取，不进入消息。

出站与入站再分一层：`OutboxEventPublisher` 只接受 `OutboundClaimApplicationEventMessage`，审核签收这类入站专属类型在编译期就传不进投递端口。

### 三类消息与载荷

```java
public record MaterialProcessMessage(
        String eventId,
        String claimApplicationNo,
        String tenantCode,
        String traceId,
        MaterialProcessPayload payload) implements OutboundClaimApplicationEventMessage {
}

public record MaterialProcessPayload(Integer claimApplicationAttempt) {
}

public record AuditDeliveryRequestedMessage(
        String eventId,
        String claimApplicationNo,
        String tenantCode,
        String traceId) implements OutboundClaimApplicationEventMessage {
}

public record AuditSignedMessage(
        String eventId,
        String eventType,
        String claimApplicationNo,
        String tenantCode,
        String traceId,
        AuditSignedPayload payload) implements ClaimApplicationEventMessage {
}

public record AuditSignedPayload(List<String> caseNos) {
}
```

| 消息 | 顶层字段 | 载荷字段 | 消费方读取 |
|---|---|---|---|
| `MaterialProcessMessage` | `eventId`、`claimApplicationNo`、`tenantCode`、`traceId` | `claimApplicationAttempt` | 全部字段 |
| `AuditDeliveryRequestedMessage` | `eventId`、`claimApplicationNo`、`tenantCode`、`traceId` | 无 | 全部字段 |
| `AuditSignedMessage` | `eventId`、`eventType`、`claimApplicationNo`、`tenantCode`、`traceId` | `caseNos` | 全部字段 |

### 关键决策

- **公共字段只保留 4 个**：`eventId` 用于幂等登记与轨迹请求标识，`claimApplicationNo`、`tenantCode`、`traceId` 用于定位申请、外部调用与轨迹。这 4 个字段在三个消费者上都有读取点。
- **删除 `eventVersion`、`occurredAt`**：全仓没有消费方读取；本次也不引入版本升级机制。Outbox 表的 `event_version` 列与 `created_at` 保留，不影响排障与历史数据。
- **`eventType` 只保留在审核签收消息上**：018 要求校验其必须为 `AUDIT_SIGNED`。材料处理的两个事件类型共用同一 Topic 与同一处理逻辑，审核交付由 Topic 本身唯一确定，两者都不需要读取 `eventType`。生产侧仍从 Outbox 行读取事件类型来选择消息类型与绑定。
- **审核交付消息不设载荷**：旧载荷的 4 个键中，`claimApplicationNo`、`tenantCode`、`traceId` 与顶层重复，`claimApplicationAttempt` 不被审核交付使用；该消息的业务键就是 `claimApplicationNo`，再包一层载荷只是重复同一个值。
- **材料处理载荷只留 `claimApplicationAttempt`**：旧工厂虽然把 `eventType` 写进载荷并注释“供消费侧自校验”，但消费方从未校验；其余字段与顶层重复，全部删除。
- **使用三个具体 record，不使用泛型消息**：具体类型可由 binder 直接反序列化，泛型参数在运行时无法可靠还原。
- **`claimApplicationAttempt` 使用 `Integer` 而非 `int`**：Jackson 对缺失的基本类型会给默认值 0，使用包装类型才能让消费者识别“字段缺失”并显式失败。
- **出站绑定按消息具体类型选择**：消息不再携带 `eventType`，投递实现用密封接口的穷尽 `switch` 映射绑定名；新增出站消息类型会编译失败，强制补齐路由。
- **存储载荷与消息载荷分开**：Outbox 表没有 `trace_id` 列且本次不改表，`traceId` 必须继续存在存储载荷 JSON 中；消息顶层已有 `traceId`，因此用两个内部存储 record 承载 `traceId`，消息载荷 record 不再包含它，避免线上重复。
- **不新增 ArchUnit 规则**：消息集合封闭性由 `sealed` 在编译期保证，字段集合由契约 JSON 测试保证。

## 三、序列化与兼容

- 三类消息 record、两个消息载荷 record、两个存储载荷 record 均标注 `@JsonIgnoreProperties(ignoreUnknown = true)`。
- 出站 JSON 字段集合变化：
    - 材料处理：`eventId`、`claimApplicationNo`、`tenantCode`、`traceId`、`payload.claimApplicationAttempt`。
    - 审核交付：`eventId`、`claimApplicationNo`、`tenantCode`、`traceId`。
    - 不再出现 `eventType`、`eventVersion`、`occurredAt`，也不再出现载荷内的 `claimApplicationNo`、`tenantCode`、`traceId`、`eventType`。
- 审核签收入站仍可携带 `eventVersion`、`occurredAt`；消息类型不声明这两个字段，反序列化时按未知字段忽略，不报错。
- `AuditSignedPayload#caseNos` 使用 `List<String>`，并通过 `@JsonDeserialize(using = StrictStringListDeserializer.class)` 声明严格字符串数组反序列化：元素不是 JSON 字符串时直接转换失败，替代现有装配器中的 `instanceof String` 判断。
- `AuditSignedPayload#caseNos` 为 `null` 或空数组不在反序列化阶段处理，仍由装配器返回 `AUDIT_SIGNED_INVALID`。
- 审核签收的 `eventType`、`claimApplicationNo`、`tenantCode` 非空白校验与 `eventId` 非空白校验保持在装配器。
- 历史 Outbox 行包含 `claimApplicationNo`、`tenantCode`、`eventType` 等存储载荷字段时，读取存储载荷按未知字段忽略，只取 `claimApplicationAttempt` 与 `traceId`。

## 四、生产侧改造

### 存储载荷

在 `usecase/integration/outbox/model` 新增两个存储载荷 record：

- `MaterialProcessOutboxPayload(Integer claimApplicationAttempt, String traceId)`
- `AuditDeliveryRequestedOutboxPayload(String traceId)`

`ClaimApplicationLifecycleRecordFactory` 改为序列化这两个 record：

- 材料处理事件写入申请尝试次数与 `traceId`，不再写 `claimApplicationNo`、`tenantCode`、`eventType`。
- 审核交付事件只写 `traceId`；申请尝试次数不被审核交付使用，不再写入。
- `OutboxEvent` 的 `eventId`、`eventType`、`eventVersion`、`claimApplicationNo`、`tenantCode` 仍按现有列落库，表结构不变。

### 投递调度

`OutboxRelayUseCase` 的 `toEnvelope` 改为 `toMessage`，返回 `OutboundClaimApplicationEventMessage`：

1. 用 `ClaimApplicationEventType.from(eventType)` 解析事件类型，未知类型直接抛异常，走现有投递失败处理。
2. 从存储载荷 JSON 读取 `traceId`，缺失时回退 `eventId`。
3. 按事件类型构造消息：
    - `CLAIM_APPLICATION_CREATED`、`CLAIM_APPLICATION_REAPPLIED` → 读取 `MaterialProcessOutboxPayload`，转换为 `MaterialProcessPayload` 后构造 `MaterialProcessMessage`。申请尝试次数缺失时保留 `null`，由消费者显式失败；JSON 非法或类型不符抛带 `eventId` 的 `IllegalStateException`。
    - `AUDIT_DELIVERY_REQUESTED` → 读取 `AuditDeliveryRequestedOutboxPayload` 取 `traceId`，直接构造 4 字段消息，不解析业务载荷。
4. 不再生成 `occurredAt`，删除时间格式化逻辑。
5. `traceId` 解析、未知事件类型与投递失败处理路径与现状一致。

### 投递实现

`OutboxEventPublisher#publish(OutboundClaimApplicationEventMessage)`；`RocketMqOutboxPublisher` 用密封接口的穷尽 `switch` 按消息具体类型取绑定名：

- `MaterialProcessMessage` → `materialProcess-out-0`。
- `AuditDeliveryRequestedMessage` → `auditDelivery-out-0`。

消息体直接使用传入的 record，Jackson 按运行期具体类型序列化；`StreamBridge.send` 返回 false 时仍抛出带 `bindingName` 与 `eventId` 的异常。

## 五、消费侧改造

| 消费者 | 绑定类型 | 装配方式 |
|---|---|---|
| `MaterialProcessConsumer` | `Consumer<Message<MaterialProcessMessage>>` | 读取 `payload.claimApplicationAttempt()`，载荷或尝试次数为 `null` 时抛出异常；其余字段直接取消息 |
| `AuditDeliveryConsumer` | `Consumer<Message<AuditDeliveryRequestedMessage>>` | 只使用 4 个公共字段，不访问载荷 |
| `AuditSignedConsumer` | `Consumer<Message<byte[]>>`（`use-native-decoding: true`） | 先由 `AuditSignedMessageParser` 严格反序列化，再交给装配器转换命令 |

审核签收绑定改为原生字节解码的原因：RocketMQ binder 的 `RocketMQMessageConverter` 依次尝试 Jackson 与 fastjson，并吞掉每个转换器的异常；Jackson 因非字符串案件号抛错后，报文会被 fastjson 按“数字转字符串”接受。仅依赖 binder 类型转换无法落实严格校验，因此 `auditSigned-in-0` 声明 `use-native-decoding: true`，由平台注入的 `ObjectMapper` 显式解析。`AuditSignedMessageParser` 把空报文、非法 JSON 与类型不符统一转换为 `ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_INVALID`，异常照常上抛交给 binder 重投。

`AuditSignedMessageAssembler#toCommand` 改为接收 `AuditSignedMessage`：

- 保留 `eventId`、`claimApplicationNo`、`tenantCode` 非空白校验与 `eventType` 等于 `AUDIT_SIGNED` 的校验。
- `caseNos` 的非空、逐项去空白、非空、长度上限、去重规则保持不变。
- 删除 `Map` 取值、字符串 key 常量与 `instanceof String` 判断；非字符串元素由严格反序列化器拒绝。
- 错误码仍为 `ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_INVALID`。

## 六、失败与日志

| 失败点 | 处理 | 与现状差异 |
|---|---|---|
| Outbox 事件类型未知 | 投递失败，退避重试或标记失败 | 无 |
| Outbox 存储载荷 JSON 非法 | 投递失败，退避重试或标记失败 | 无 |
| 材料处理存储载荷的申请尝试次数类型不符 | 投递失败，退避重试或标记失败 | 无 |
| 材料处理与审核交付消息 JSON 无法反序列化为具体类型 | binder 转换失败，按消费失败重投，耗尽进入死信 | 应用侧不输出装配器日志；消息结果不变 |
| 审核签收报文为空、非法 JSON 或案件号不是字符串 | `AuditSignedMessageParser` 抛 `BusinessException`，记录 ERROR，重投与死信 | 不再被 binder 的 fastjson 转换静默接受 |
| 材料处理缺少载荷或申请尝试次数 | 消费者抛异常，记录 ERROR，重投与死信 | 无 |
| 审核交付消息携带未知字段 | 忽略，正常处理 | 不再因载荷内容影响交付 |
| 审核签收业务校验失败 | 装配器抛 `BusinessException`，记录 ERROR，重投与死信 | 无 |
| binder 自身异常 | 原样上抛 | 无 |

消息转换阶段的失败由 binder 记录并重投；应用侧日志只覆盖转换成功后的业务校验失败。运行栈回归必须确认转换失败的消息同样进入死信。

## 七、删除清单

- `shared/contract/ClaimApplicationEventEnvelope`
- `AuditDeliveryRequestedPayload`
- `MaterialProcessConsumer`、`AuditDeliveryConsumer`、`AuditSignedConsumer` 中的 `Map` 取值与字段 key 常量
- `OutboxRelayUseCase` 的 `TypeReference<Map<String, Object>>` 转换与 `occurredAt` 格式化
- `ClaimApplicationLifecycleRecordFactory` 的 `Map.of` 载荷与重复字段
- `AuditSignedMessageAssembler` 的 `Map<String, Object>` 参数与 `instanceof String` 判断
- 出站消息中的 `eventType`、`eventVersion`、`occurredAt` 字段

## 八、对现有功能的影响

- 材料处理与审核交付两个 Topic 的出站 JSON 字段集合收缩；这两个 Topic 只由本服务生产与消费，不存在外部消费者，不构成对外 breaking。
- 审核签收入站 JSON 兼容不变：外部系统继续发送完整信封，平台只读取业务需要的字段，`eventVersion`、`occurredAt` 与契约外字段被忽略。
- Topic、消费组、事件类型字符串、重投次数、死信主题、幂等键与死信行为全部不变。
- Outbox 表结构不变；新写入的存储载荷 JSON 变小，历史行通过忽略未知字段仍可正常投递。
- HTTP、数据库、Redis、网关与领域模型均不变。
- 领域层只把投递端口的参数类型从旧信封换成共享契约接口，依赖方向不变。

## 九、测试策略

- 契约 JSON 测试：
    - 三类消息序列化后的顶层字段集合与载荷字段集合与本文 §二 一致。
    - 出站消息断言不包含 `eventType`、`eventVersion`、`occurredAt`。
    - 文档中的审核签收样例（含 `eventVersion`、`occurredAt`）可反序列化为 `AuditSignedMessage`。
    - 数字案件号反序列化失败；未知字段被忽略。
- 存储载荷测试：
    - 工厂输出的材料处理与审核交付载荷字段集合与本文 §四 一致。
    - 含历史重复字段的载荷可被新代码读取。
- 投递转换测试：
    - 三类事件类型分别转换为正确消息类型与字段。
    - `traceId` 缺失回退 `eventId`。
    - 材料处理申请尝试次数缺失保留 `null`；非法载荷与未知事件类型抛异常。
    - 审核交付消息只含 4 个公共字段。
- 绑定选择测试：材料处理与审核交付分别命中各自输出绑定。
- 装配器测试迁移：改用 `AuditSignedMessage` 与 `AuditSignedPayload`，覆盖合法去重、事件类型不匹配、缺少案件号、空白、超长、缺少申请编号与事件标识。
- 既有分层 ArchUnit 规则全部保留并通过。
- 运行栈回归：材料处理首次与重新申请、审核交付、审核签收正常/重复/非法报文/DLQ，核对 Topic、消费组、死信主题与错误响应不变，并核对实际出站 JSON 字段集合。
