# 020 消息契约类型化 · 设计

## 一、改动模块

| 模块 | 改动 |
|---|---|
| `shared/contract/message` | 新增顶层密封接口 `ClaimApplicationEventMessage`、出站密封子接口、三类消息 record、三类载荷 record、案件号严格反序列化器；删除 `ClaimApplicationEventEnvelope` |
| `usecase/integration/outbox/port` | `OutboxEventPublisher#publish` 参数改为出站消息接口 |
| `usecase/integration/record` | 生命周期记录工厂用类型化载荷构造 Outbox 事件 |
| `usecase/integration/outbox` | 投递调度按事件类型把存储载荷转换为具体消息 |
| `infrastructure/integration/messaging` | StreamBridge 投递实现改为发送类型化消息 |
| `interfaces/material/messaging` | 材料处理消费者绑定具体消息类型 |
| `interfaces/audit/messaging` | 审核交付与审核签收消费者绑定具体消息类型；装配器接收类型化消息 |
| 测试与文档 | 契约 JSON 测试、装配器测试迁移、投递转换测试、架构文档同步 |

## 二、契约结构

### 密封消息接口

```java
public sealed interface ClaimApplicationEventMessage
        permits OutboundClaimApplicationEventMessage, AuditSignedMessage {

    String eventId();

    String eventType();

    int eventVersion();

    String claimApplicationNo();

    String tenantCode();

    String occurredAt();

    String traceId();
}

public sealed interface OutboundClaimApplicationEventMessage extends ClaimApplicationEventMessage
        permits MaterialProcessMessage, AuditDeliveryRequestedMessage {
}
```

顶层接口只声明公共信封字段，不声明 `payload()`：三类载荷字段互不相同，强行抽象只会引入空接口。消息类型集合由 `permits` 封闭，新增消息必须改接口，编译器会同时暴露所有需要补齐的位置。

出站与入站再分一层：`OutboxEventPublisher` 只接受 `OutboundClaimApplicationEventMessage`，审核签收这类入站专属类型在编译期就传不进投递端口，不再依赖运行时的事件类型枚举兜底。

### 三类消息与载荷

| 消息 | `eventType` | 载荷字段 | 链路 |
|---|---|---|---|
| `MaterialProcessMessage` | `CLAIM_APPLICATION_CREATED`、`CLAIM_APPLICATION_REAPPLIED` | `MaterialProcessPayload{claimApplicationNo, tenantCode, eventType, claimApplicationAttempt, traceId}` | Outbox → 材料处理消费者 |
| `AuditDeliveryRequestedMessage` | `AUDIT_DELIVERY_REQUESTED` | `AuditDeliveryRequestedPayload{claimApplicationNo, tenantCode, claimApplicationAttempt, traceId}` | Outbox → 审核交付消费者 |
| `AuditSignedMessage` | `AUDIT_SIGNED` | `AuditSignedPayload{caseNos}` | 审核系统 → 审核签收消费者 |

载荷字段与 018 契约文档逐字段一致，包括材料处理载荷中重复出现的 `eventType`。字段名即 JSON 字段名，不使用 `@JsonProperty` 重命名。

### 关键决策

- **`eventType` 保持 `String`**：出站事件枚举 `ClaimApplicationEventType` 属于领域层，`shared/contract` 不能反向依赖领域；`AUDIT_SIGNED` 又是入站专属类型，不属于出站枚举。将枚举迁到契约层会扩大改动面，本 spec 不做。
- **使用三个具体 record，不使用泛型消息**：具体类型可由 binder 直接反序列化，泛型参数在运行时无法可靠还原；七个公共字段重复三次换取明确的转换目标，是本次重构的核心收益。
- **`claimApplicationAttempt` 使用 `Integer` 而非 `int`**：Jackson 对缺失的基本类型会给默认值 0，使用包装类型才能让消费者识别“字段缺失”并显式失败。
- **不新增 ArchUnit 规则**：消息集合封闭性由 `sealed` 在编译期保证，字段一致性由契约 JSON 测试保证，再加一条“实现类必须位于某包”的规则没有额外约束力。

## 三、序列化与兼容

- 三类消息 record 与三类载荷 record 均标注 `@JsonIgnoreProperties(ignoreUnknown = true)`，保持现状对契约外字段的容忍。
- `AuditSignedPayload#caseNos` 使用 `List<String>`，并通过 `@JsonDeserialize(using = StrictStringListDeserializer.class)` 声明严格字符串数组反序列化：元素不是 JSON 字符串时直接转换失败，替代现有装配器中的 `instanceof String` 判断，避免 Jackson 把数字静默转成字符串。
- `AuditSignedPayload#caseNos` 为 `null` 或空数组不在反序列化阶段处理，仍由装配器返回 `AUDIT_SIGNED_INVALID`。
- 内部 Outbox 载荷由本服务写入，字段集与契约一致，不额外放宽；契约外字段即使存在也不会再透传到消息。
- 消息顶层与载荷的 JSON 字段名、类型、层级保持不变，不新增 `@JsonProperty`、不调整字段顺序、不改变时间格式。

## 四、生产侧改造

### 生命周期记录工厂

`ClaimApplicationLifecycleRecordFactory` 用载荷 record 替代 `Map.of`：

- 材料处理事件序列化 `MaterialProcessPayload`。
- 审核交付事件序列化 `AuditDeliveryRequestedPayload`。

记录的 JSON 字段与现有 `Map` 载荷完全一致；`OutboxEvent.payload` 仍是 JSON 字符串，表结构不变。

### 投递调度

`OutboxRelayUseCase` 的 `toEnvelope` 改为 `toMessage`：

1. 用 `ClaimApplicationEventType.from(eventType)` 解析事件类型，未知类型直接抛异常，走现有投递失败处理。
2. 按事件类型读取对应载荷 record：
   - `CLAIM_APPLICATION_CREATED`、`CLAIM_APPLICATION_REAPPLIED` → `MaterialProcessPayload`。
   - `AUDIT_DELIVERY_REQUESTED` → `AuditDeliveryRequestedPayload`。
3. 用 `OutboxEvent` 的公共字段与载荷构造对应消息 record。
4. 载荷解析失败抛出带 `eventId` 的 `IllegalStateException`，与现有“载荷不是合法 JSON”的处理路径一致。

`traceId` 仍取载荷中的 `traceId`，为 `null` 时回退 `eventId`；`occurredAt` 的格式化规则不变。

### 投递实现

`OutboxEventPublisher#publish(OutboundClaimApplicationEventMessage)`；`RocketMqOutboxPublisher` 按 `message.eventType()` 选绑定，消息体直接使用传入的 record，Jackson 按运行期具体类型序列化，产生与现有结构一致的 JSON。

## 五、消费侧改造

| 消费者 | 绑定类型 | 装配方式 |
|---|---|---|
| `MaterialProcessConsumer` | `Consumer<Message<MaterialProcessMessage>>` | 读取 `payload.claimApplicationAttempt()`，为 `null` 时抛出异常；其余字段直接取消息 |
| `AuditDeliveryConsumer` | `Consumer<Message<AuditDeliveryRequestedMessage>>` | 只用公共字段，载荷不参与决策 |
| `AuditSignedConsumer` | `Consumer<Message<AuditSignedMessage>>` | 交给装配器转换命令 |

`AuditSignedMessageAssembler#toCommand` 改为接收 `AuditSignedMessage`：

- 保留 `eventId`、`claimApplicationNo`、`tenantCode` 非空白校验与 `eventType` 等于 `AUDIT_SIGNED` 的校验。
- `caseNos` 的非空、逐项去空白、非空、长度上限、去重规则保持不变。
- 删除 `Map` 取值、字符串 key 常量与 `instanceof String` 判断；非字符串元素由严格反序列化器拒绝。
- 错误码仍为 `ClaimApplicationAsyncErrorCode.AUDIT_SIGNED_INVALID`。

## 六、失败与日志

| 失败点 | 处理 | 与现状差异 |
|---|---|---|
| Outbox 事件类型未知 | 投递失败，退避重试或标记失败 | 无 |
| Outbox 载荷 JSON 非法或字段类型不符 | 投递失败，退避重试或标记失败 | 无 |
| 消息 JSON 无法反序列化为具体类型 | binder 转换失败，按消费失败重投，耗尽进入死信 | 应用侧不再输出装配器日志；消息结果不变 |
| `claimApplicationAttempt` 缺失 | 消费者抛异常，记录 ERROR，重投与死信 | 无 |
| 审核签收业务校验失败 | 装配器抛 `BusinessException`，记录 ERROR，重投与死信 | 无 |
| binder 自身异常 | 原样上抛 | 无 |

消息转换阶段的失败由 binder 记录并重投；应用侧日志只覆盖转换成功后的业务校验失败。运行栈回归必须确认转换失败的消息同样进入死信。

## 七、删除清单

- `shared/contract/ClaimApplicationEventEnvelope`
- `MaterialProcessConsumer`、`AuditDeliveryConsumer`、`AuditSignedConsumer` 中的 `Map` 取值与字段 key 常量
- `OutboxRelayUseCase` 的 `TypeReference<Map<String, Object>>` 转换
- `ClaimApplicationLifecycleRecordFactory` 的 `Map.of` 载荷
- `AuditSignedMessageAssembler` 的 `Map<String, Object>` 参数与 `instanceof String` 判断

## 八、对现有功能的影响

- 对外 JSON、Topic、消费组、事件类型、重投次数、死信主题、幂等键全部不变，外部调用方无感。
- 契约外字段继续被容忍；契约外载荷字段不再透传到 MQ，仅影响本服务自己写入的历史事件，且不影响任何契约字段。
- 字段类型错误从“运行期静默忽略”变为“转换期失败”，这是类型化的预期结果；合法消息不受影响。
- 领域层只把投递端口的参数类型从旧信封换成共享契约接口，依赖方向不变。
- HTTP、数据库、Redis、Outbox 表结构与调度参数不变。

## 九、测试策略

- 契约 JSON 测试：三类消息序列化后的顶层字段集合、载荷字段集合与 018 契约文档一致；文档中的审核签收 JSON 可反序列化为 `AuditSignedMessage`；数字案件号反序列化失败；未知字段被忽略。
- 装配器测试迁移：改用 `AuditSignedMessage` 与 `AuditSignedPayload`，覆盖合法去重、事件类型不匹配、缺少案件号、空白、超长、缺少申请编号与事件标识。
- 投递转换测试：三类事件类型分别转换为正确消息类型与载荷；`traceId` 缺失回退 `eventId`；未知事件类型与非法载荷抛异常。
- 既有分层 ArchUnit 规则全部保留并通过。
- 运行栈回归：材料处理首次与重新申请、审核交付、审核签收正常/重复/非法报文/DLQ，核对 Topic、消费组、死信主题与错误响应不变。
