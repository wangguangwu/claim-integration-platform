# 020 消息契约类型化

## 背景与目标

理赔申请服务的三条 MQ 链路共用 `ClaimApplicationEventEnvelope`，其中 `payload` 是 `Map<String, Object>`。生产侧用 `Map.of` 拼字段，消费侧用字符串 key 取值并做 `instanceof` 判断，字段名、类型和必填约束都只存在于运行期。

逐字段核对现有信封后，实际消费需求只有 4 个公共字段：

- `eventId`：三类消费者都用于幂等登记或轨迹请求标识。
- `claimApplicationNo`、`tenantCode`、`traceId`：三类消费者都用于定位申请、外部调用或轨迹。
- `eventType`：只有审核签收消费者校验；材料处理与审核交付消费者不读取。
- `eventVersion`、`occurredAt`：全仓没有消费方读取。
- `payload`：审核交付消费者完全不读；其内容与顶层字段重复，其中 `claimApplicationAttempt` 也不参与审核交付决策。

本次目标是把消息契约从“通用信封 + Map 载荷”改为“按消息类型取最小字段集”的封闭类型：

- 每条消息有唯一的具体类型，字段以 record 组件声明，编译期可校验。
- 公共字段只保留三个消费者都实际使用的 4 个。
- 载荷只保留该消息实际消费的业务字段，不重复公共字段。
- 消息集合封闭，新增消息类型必须显式扩展契约。
- 消费侧只做业务校验，不再从 Map 猜字段类型。

## 业务规则

1. 平台对外共三类消息：材料处理、审核交付、审核签收；每类消息有唯一消息类型。
2. 消息公共字段固定为 `eventId`、`claimApplicationNo`、`tenantCode`、`traceId`；字段顺序不构成契约，字段名构成契约。
3. `eventType`、`eventVersion`、`occurredAt` 不进入出站消息：
    - `eventType` 仍是 Outbox 表的事件类型，只用于生产侧选择消息类型与输出绑定。
    - `eventVersion` 仍是 Outbox 表列，不参与消息构造。
    - `occurredAt` 不再生成；事件时间以 Outbox `created_at` 与交互轨迹 `occurred_at` 为准。
4. 审核签收消息额外保留 `eventType`，因为 018 要求校验其必须为 `AUDIT_SIGNED`。
5. 载荷按消息类型定义，且不得重复公共字段：
    - 材料处理：`payload.claimApplicationAttempt`，必填，类型为 `Integer`。
    - 审核交付：无载荷；业务键只有 `claimApplicationNo`，案件号只出现在审核签收消息中。
    - 审核签收：`payload.caseNos`，必填非空字符串数组。
6. 对外 JSON 变化：
    - 材料处理与审核交付两个 Topic 的 JSON 收缩为上述字段，不再出现 `eventType`、`eventVersion`、`occurredAt` 与重复的载荷字段。本服务是这两个 Topic 的唯一生产者与消费者，不构成对外 breaking。
    - 审核签收入站仍接受完整信封；`eventVersion`、`occurredAt` 与契约外字段被忽略，业务校验不变。
    - Topic、消费组、事件类型字符串、重投次数、死信主题与幂等键均不变。
7. 消息类型集合封闭，只能通过修改密封接口扩展；新增消息类型必须同时补齐生产、消费与测试。
8. 出站消息与入站消息在类型层区分，Outbox 投递端口只接受出站消息，不能传入审核签收等入站专属类型。
9. 消息字段与载荷字段在契约层显式声明，生产侧不得再用 `Map<String, Object>` 拼接载荷。
10. 消费侧不得再用字符串 key 读取载荷；字段缺失或类型不符必须显式失败，不允许静默取到 null 后继续处理。
11. 材料处理消息的 `claimApplicationAttempt` 缺失或为 `null` 时消费失败，进入既有重投与死信流程。
12. 审核交付消息只使用 4 个公共字段；消息类型不声明载荷，报文中出现 `payload` 字段按未知字段忽略，不影响处理。
13. 审核签收消息的校验规则保持不变：`eventType` 必须为 `AUDIT_SIGNED`，`eventId`、`claimApplicationNo`、`tenantCode` 非空白，`caseNos` 为非空字符串数组，案件号去空白后非空、不超过 64 字符、去重后落库；非法消息错误码仍为 `AUDIT_SIGNED_INVALID`。
14. 审核签收消息的未知额外字段继续被容忍；案件号数组中的非字符串元素继续被拒绝。
15. 出站消息的输出绑定由消息具体类型选择，不再依赖消息中的 `eventType`；未知事件类型、存储载荷 JSON 非法或字段类型不符仍按投递失败处理，进入既有退避重试与失败标记流程。
16. Outbox 存储载荷继续保留 `traceId`；材料处理还保留 `claimApplicationAttempt`，供投递调度组装消息。`traceId` 缺失时回退为 `eventId`，与现状一致。
17. 消息消费失败仍原样抛给 binder，由 binder 重投并最终进入死信；业务拒绝日志、系统异常日志与敏感字段脱敏要求不变。

## 正常场景

- 首次签收或重新申请写入 Outbox 事件，投递调度构造材料处理消息（4 个公共字段 + 类型化申请尝试次数），材料消费者直接读取并处理。
- 申请进入 `WAIT_AUDIT` 写入审核交付事件，投递调度构造审核交付消息（仅 4 个公共字段），审核交付消费者直接读取并交付。
- 审核系统回传 `AUDIT_SIGNED`，消息反序列化为审核签收类型，装配器校验事件类型并读取类型化案件号列表，完成去空白与去重后落库。
- 出站 JSON 与更新后的集成契约逐字段一致；入站完整信封即使携带 `eventVersion`、`occurredAt` 仍可正常解析。

## 异常场景与边界条件

- 事件类型不在出站事件集合内：投递调度拒绝构造消息，事件按现有规则退避重试或标记失败。
- Outbox 存储载荷不是合法 JSON，或申请尝试次数类型与契约不符：投递调度按投递失败处理，不发送半成品消息。
- 材料处理消息缺少载荷或 `claimApplicationAttempt` 为 `null`：消费者显式失败，按既有规则重投与死信。
- 审核交付消息携带 `eventType`、`eventVersion`、`occurredAt` 或 `payload`：全部按未知字段忽略，正常处理。
- 审核签收消息缺少 `caseNos`、数组为空、案件号空白或超过 64 字符：装配器返回 `AUDIT_SIGNED_INVALID`，消息重投后进入死信。
- 审核签收消息 `eventType` 不是 `AUDIT_SIGNED`：装配器返回 `AUDIT_SIGNED_INVALID`。
- 审核签收消息的 `caseNos` 含非字符串元素：消息转换阶段失败，按消费失败进入重投与死信，不写入案件关系。
- 审核签收消息携带 `eventVersion`、`occurredAt` 或其他契约外字段：忽略，不影响已知字段解析。
- 历史 Outbox 行包含 `claimApplicationNo`、`tenantCode`、`eventType` 等已被移除的载荷字段：转换时忽略，消息仍按契约字段正常组装。

## 本次范围

- `claim-application-service` 的 MQ 消息契约、Outbox 存储载荷与投递转换、三个消费者与审核签收装配器。
- 契约序列化/反序列化测试、既有装配器测试迁移、投递转换与绑定选择测试。
- 集成契约文档与架构文档中的消息契约条目。
- 运行栈三条 Topic 与死信链路回归。

## 非本次范围

- 不修改 Topic、消费组、事件类型字符串、重投次数、死信主题与幂等键。
- 不修改 MQ 死信策略、补偿策略与消费幂等实现。
- 不修改 HTTP 接口、错误码与响应结构；审核签收错误码保持 `AUDIT_SIGNED_INVALID`。
- 不修改网关、租户、案件、AI 模块。
- 不调整领域模型、数据库、Redis 与 Outbox 表结构；`event_version` 列继续保留。
- 不引入消息版本升级机制，不实现多版本消息兼容。
- 不引入新的序列化框架或依赖。

## 验收标准

1. `ClaimApplicationEventEnvelope` 删除且代码无引用（历史 spec 文档除外）；消息生产与消费代码中不再出现 `Map<String, Object>` 载荷与 `payload.get(...)`。
2. 三类消息均有独立消息 record；顶层接口与出站接口均为密封接口；载荷 record 只有 `MaterialProcessPayload` 与 `AuditSignedPayload` 两个。
3. `AuditDeliveryRequestedMessage` 恰好包含 `eventId`、`claimApplicationNo`、`tenantCode`、`traceId` 四个组件且无载荷；`MaterialProcessMessage` 为四个公共字段加 `claimApplicationAttempt` 载荷；`AuditSignedMessage` 为四个公共字段加 `eventType` 与 `caseNos` 载荷。
4. 契约测试序列化三类消息，断言精确字段集合：出站消息不出现 `eventType`、`eventVersion`、`occurredAt` 与重复载荷字段；入站样例携带 `eventVersion`、`occurredAt` 仍可反序列化。
5. 审核签收的全部校验规则与错误码与 018 交付结果一致，非字符串案件号仍被拒绝。
6. 材料处理、审核交付、审核签收三条链路在运行栈上的成功、重投与死信行为与 018 一致，Topic 与消费组不变。
7. 全量 Maven 构建与测试通过，既有分层 ArchUnit 规则继续通过。
8. 集成契约文档与架构文档不再出现 `ClaimApplicationEventEnvelope`，文档样例与实际出站 JSON 一致。
