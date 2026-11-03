# 理赔申请集成契约

## 项目背景与目标

本文档定义 `claim-application-service` 在 `v1` 阶段使用的 HTTP 与 RocketMQ 公共契约。业务报文字段由后续理赔申请业务 Spec 补充；本文档固定地址、方法、公共请求头、关联标识、Topic、消费者组和消息信封。

## 契约变更（breaking）

007 理赔申请命名全链路收敛把指代业务对象的名称统一加上 `claim` 限定，对接收方有以下破坏性影响，对接方需同步调整：

| 类别 | 旧 | 新 |
|---|---|---|
| HTTP 资源段 | `/api/application/v1/applications` | `/api/application/v1/claim-applications` |
| 签收响应字段 | `data.applicationNo` | `data.claimApplicationNo` |
| 回调与出站报文字段 | `applicationNo`、`externalApplicationNo`、`applicationAttempt` | `claimApplicationNo`、`externalClaimApplicationNo`、`claimApplicationAttempt` |
| MQ 信封与载荷字段 | `applicationNo` | `claimApplicationNo` |
| 事件类型 | `APPLICATION_CREATED`、`APPLICATION_REAPPLIED` | `CLAIM_APPLICATION_CREATED`、`CLAIM_APPLICATION_REAPPLIED` |
| 交互类型 | `APPLICATION_INTAKE` | `CLAIM_APPLICATION_INTAKE` |
| 错误码 | `APPLICATION_INTAKE_INVALID`、`APPLICATION_INTAKE_BUSY`、`APPLICATION_INTAKE_SYSTEM_ERROR` | 已统一移除，失败响应只返回 `FAILURE` |
| HTTP 接口前缀 | `/api/application/v1`，网关 `Path=/api/application/**` | `/api/claim/v1`，网关 `Path=/api/claim/**` |

MQ Topic 与消费者组名、数据库名与 Redis 键前缀均保持不变；租户私有入参字段名不受影响。

### 018 审核方向接入变更

| 类别 | 旧 | 新 |
|---|---|---|
| 审核签收传输 | HTTP `POST /api/claim/v1/callbacks/audit-signed` | MQ Topic `claim_application_audit_signed_v1`，消费者组 `claim-application-audit-signed-v1` |
| 问题件下发路径 | `/api/application/v1/callbacks/audit-issues` | `/api/claim/v1/callbacks/audit-issues` |
| 问题件关闭路径 | `/api/application/v1/callbacks/audit-issues/{issueNo}/close` | `/api/claim/v1/callbacks/audit-issues/{issueNo}/close` |
| 问题件状态查询 | 无 | `GET /api/claim/v1/cases/{caseNo}/issue` |

问题件是带前置条件的命令，保留 HTTP 以便审核系统同步获得 2xx/4xx 裁决；审核签收是事实通知，改为 MQ。旧问题件路径不保留兼容别名。

## HTTP 契约

### 公共请求头

| 请求头 | 适用范围 | 必填 | 说明 |
|---|---|---|---|
| `X-Tenant-Code` | 全部接口 | 是 | 租户编码 |
| `X-Protocol-Version` | 全部接口 | 是 | 租户协议版本 |
| `X-Trace-Id` | 全部接口 | 否 | 缺失时由网关生成并向下游透传 |
| `X-Request-Id` | 回调接口 | 是 | 回调发送方生成且重试时保持不变的请求标识 |

### 接口清单

| 接口 | 方法 | 调用方 | 用途 | 关联字段 |
|---|---|---|---|---|
| `/api/claim/v1/application/intake` | `POST` | 租户系统 | 提交理赔申请；补材同样经此接口携带 `claimApplicationNo` | `externalClaimApplicationNo`、可选 `claimApplicationNo` |
| `/api/claim/v1/application/{claimApplicationNo}/revoke` | `POST` | 租户系统 | 发起理赔申请撤销（规划，未开放） | `claimApplicationNo` |
| `/api/claim/v1/application/{claimApplicationNo}/materials` | `GET` | 租户系统 | 查询申请摘要与未删除材料 | `claimApplicationNo` |
| `/api/claim/v1/application/{claimApplicationNo}/issues` | `GET` | 租户系统 | 查询申请摘要与全部问题件 | `claimApplicationNo` |
| `/api/claim/v1/application/{claimApplicationNo}/cases` | `GET` | 租户系统 | 查询申请摘要与案件关联 | `claimApplicationNo` |
| `/api/claim/v1/callbacks/audit-issues` | `POST` | 审核系统 | 下发问题件 | `claimApplicationNo`、`issueNo`、`X-Request-Id` |
| `/api/claim/v1/callbacks/audit-issues/{issueNo}/close` | `POST` | 审核系统 | 关闭问题件 | `issueNo`、`X-Request-Id` |
| `/api/claim/v1/cases/{caseNo}/issue` | `GET` | 审核系统 | 查询案件是否存在未关闭问题件 | `caseNo` |

审核签收不再使用 HTTP 回调，改为消费 `claim_application_audit_signed_v1`，消息结构见下文 MQ 契约。

自动承保完成不提供 HTTP 回调入口：平台通过补偿任务调用承保系统的结果查询接口推进 `UNDERWRITING → WAIT_AUDIT`，承保系统只需保证结果查询接口可用。

成功响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "traceId": "5bba2a7f468c4efe",
  "data": {}
}
```

错误响应：

```json
{
  "code": "FAILURE",
  "message": "请求参数不合法",
  "traceId": "5bba2a7f468c4efe",
  "data": null
}
```

### 网关拒绝与限流

网关在转发前完成租户校验与租户限流，被拒绝的请求不进入理赔申请服务，也不产生 InteractionLog。这两种拒绝体不含 `traceId` 字段，`traceId` 通过响应头 `X-Trace-Id` 返回：

| 场景 | HTTP 状态 | 响应体 |
|---|---|---|
| 缺少 `X-Tenant-Code` 或 `X-Protocol-Version` | 400 | `{"code":"TENANT_HEADER_MISSING","message":"缺少租户标识或协议版本"}` |
| 超过接口级租户配额或路由兜底阈值 | 429 | `{"code":"CLAIM_APPLICATION_RATE_LIMITED","message":"租户请求超过限流阈值"}` |

限流只在签收路由 `claim-application-service` 生效，其他路由不受影响。限流分两层：路由兜底阈值和接口级租户配额，实际生效值取两者较小值。

- 路由兜底：`claim.gateway.rate-limit.default-qps`（默认 50），不区分接口与租户，是所有租户的公共上界。
- 接口级租户配额：`claim.gateway.rate-limit.apis.<接口名>.tenants.<租户编码>`（默认 `tenant-a: 20`），只能更严，配置值大于 `default-qps` 时网关启动失败；接口未配置的租户只受兜底约束。
- 接口级配额按路径匹配，当前只有签收接口 `claim-application-intake` 配置了租户配额（`/api/claim/v1/application/intake`），同一租户的承保回调不受该配额影响。

阈值口径是**单节点 QPS**：租户全局上界 = 单节点阈值 × 网关节点数，配置里不得写期望的全局值。规则随网关启动从配置加载；Sentinel 控制台只做观测，控制台上的临时规则在网关重启后失效。

### 问题件状态查询

`GET /api/claim/v1/cases/{caseNo}/issue` 必须携带租户请求头，按 `(X-Tenant-Code, caseNo)` 在 `t_claim_case_relation` 中反查平台申请，再读取该申请未关闭的问题件。接口只读，不写交互轨迹。

| 场景 | HTTP 状态 | 响应 |
|---|---|---|
| 有未关闭问题件 | 200 | `hasOpenIssue=true`，返回 `issueNo`、`reason`、`raisedAt` |
| 无未关闭问题件 | 200 | `hasOpenIssue=false`，其余问题件字段为 `null` |
| 案件关系不存在 | 400 | `code=FAILURE` |

### 理赔申请详情查询

三个查询接口均按 `TenantContext` 中的租户隔离，只读读模型，不加载聚合、不写交互轨迹：

| 接口 | 返回 |
|---|---|
| `GET /api/claim/v1/application/{claimApplicationNo}/materials` | 申请摘要 + 未删除材料 |
| `GET /api/claim/v1/application/{claimApplicationNo}/issues` | 申请摘要 + 全部问题件 |
| `GET /api/claim/v1/application/{claimApplicationNo}/cases` | 申请摘要 + 案件关联 |

| 场景 | HTTP 状态 | 响应 code |
|---|---|---|
| 申请不存在或不属于当前租户 | 400 | `FAILURE` |
| 缺少租户上下文 | 400 | `FAILURE` |
| 未预期系统异常 | 500 | `FAILURE` |

子集合为空返回 200 与空数组。材料按主键升序，问题件按创建时间与主键倒序，案件关联按创建时间与主键升序。跨租户与不存在返回相同状态与响应，避免暴露其他租户数据的存在性。

### 签收幂等判定

- 未携带 `claimApplicationNo` 时：首次提交创建申请并返回新 `claimApplicationNo`；同一 `externalClaimApplicationNo` 的重复提交返回同一 `claimApplicationNo`，不覆盖人员、材料与状态。
- 携带 `claimApplicationNo` 时跳过快照直接读库，编号必须与 `tenantCode + externalClaimApplicationNo` 对应记录一致；申请不存在或编号不一致返回 HTTP 400 与 `FAILURE`，且不新建申请。
- 携带 `claimApplicationNo` 且状态为 `ACCEPT_FAILED` 时返回 `REAPPLIED`：人员与材料整体替换，旧材料逻辑删除，尝试次数加 1，状态回到 `PENDING_ACCEPT`。
- 携带 `claimApplicationNo`、状态为 `ISSUE` 且存在未关闭问题件时返回 `SUPPLEMENTED`：关闭该问题件（关闭人 `TENANT_SUPPLEMENT`），报文人员不生效，旧材料逻辑删除并写入新批次，尝试次数加 1，状态回到 `PENDING_ACCEPT`，随后异步处理材料。
- 携带 `claimApplicationNo` 的其他状态（含 `COMPLETED`）返回 `REUSED`，只写交互轨迹，不触发重新申请或补材。
- 聚合保存冲突时，尝试次数已增大按 `REUSED`；补材时状态已离开 `ISSUE` 返回 HTTP 400 与 `FAILURE`；其余返回 HTTP 503 与 `FAILURE`，调用方可重试。
- 判定结果缓存在 Redis，只有 `ACCEPTED`、`UNDERWRITING`、`WAIT_AUDIT`、`COMPLETED` 四种可复用状态形成可复用快照（TTL 1 小时），命中后重复请求不再读库；`PENDING_ACCEPT`、`ACCEPT_FAILED` 与 `ISSUE` 每次都回源数据库确认。
- 该缓存不改变对调用方可见的响应结构与响应码。

### Redis 降级

- 连续失败达到 3 次后熔断打开 10 秒，期间签收不依赖 Redis：锁降级为无锁、判定快照只走进程内缓存与 MySQL、编号改为本节点串行取当天最大尾号加一。
- 降级期间本节点签收并发受信号量限制（默认 10），超出时返回 HTTP 500 与 `FAILURE`，调用方可按原重试策略重试。
- 降级不改变编号格式与长度；跨节点并发产生的唯一键冲突由平台内部重试（上限 5 次）消化，重试耗尽同样返回 HTTP 500 与 `FAILURE`。

## MQ 契约

| Topic | 事件类型 | 消费者组 | 职责 |
|---|---|---|---|
| `claim_application_material_process_v1` | `CLAIM_APPLICATION_CREATED`、`CLAIM_APPLICATION_REAPPLIED` | `claim-application-material-processor-v1` | 异步处理首次申请、重新申请或租户补材的材料 |
| `claim_application_audit_delivery_v1` | `AUDIT_DELIVERY_REQUESTED` | `claim-application-audit-delivery-v1` | 将已具备审核条件的申请交付审核系统 |
| `claim_application_audit_signed_v1` | `AUDIT_SIGNED` | `claim-application-audit-signed-v1` | 接收审核系统回传的签收结果与案件号 |

### 消费失败与死信

消费函数不捕获业务异常，异常直接抛给 binder，由 RocketMQ 按消费组重投；重投次数由 `spring.cloud.stream.rocketmq.bindings.<binding>.consumer.push.max-reconsume-times` 声明，默认 3 次，可用环境变量 `CLAIM_APPLICATION_MQ_MAX_RECONSUME_TIMES` 覆盖。重投耗尽后 broker 自动写入死信主题，平台不消费死信。

| 消费组 | 死信主题 |
|---|---|
| `claim-application-material-processor-v1` | `%DLQ%claim-application-material-processor-v1` |
| `claim-application-audit-delivery-v1` | `%DLQ%claim-application-audit-delivery-v1` |
| `claim-application-audit-signed-v1` | `%DLQ%claim-application-audit-signed-v1` |

平台不区分可重试与不可重试异常：事件结构非法、申请不存在这类错误同样重投后进入死信，由人工排查重放。消息幂等由 `t_claim_consumed_event` 的 `(consumer_group, event_id)` 唯一键兜底。

### 出站消息

平台是 `claim_application_material_process_v1` 与 `claim_application_audit_delivery_v1` 的唯一生产者，消息只携带消费方实际读取的字段。

材料处理消息（`CLAIM_APPLICATION_CREATED`、`CLAIM_APPLICATION_REAPPLIED`；租户补材复用 `CLAIM_APPLICATION_REAPPLIED`）：

```json
{
  "eventId": "019a9c8c-4d02-7d19-9f16-32d447d4ee66",
  "claimApplicationNo": "AP202610270001",
  "tenantCode": "tenant-a",
  "traceId": "5bba2a7f468c4efe",
  "payload": {
    "claimApplicationAttempt": 1
  }
}
```

审核交付消息（`AUDIT_DELIVERY_REQUESTED`）不含 `payload`：

```json
{
  "eventId": "019a9c8c-4d02-7d19-9f16-32d447d4ee67",
  "claimApplicationNo": "AP202610270001",
  "tenantCode": "tenant-a",
  "traceId": "5bba2a7f468c4efe"
}
```

出站消息不携带 `eventType`、`eventVersion`、`occurredAt`：`eventType` 只用于生产端路由到 Topic，事件时间以 Outbox `created_at` 与交互轨迹 `occurred_at` 为准。`payload` 不得包含完整人员 JSON、证件号、银行卡号、Token 或密钥；消费者需要业务明细时，以 `claimApplicationNo` 读取理赔申请私有数据。

### 审核签收入站消息

审核系统向 `claim_application_audit_signed_v1` 发送事实通知，`eventType` 必须为 `AUDIT_SIGNED`，`payload.caseNos` 为非空字符串数组，每条案件号去空白后非空、不超过 64 字符，重复值去重后落库：

```json
{
  "eventId": "8f0d1a52-6a3e-4f0b-9d7e-2c1b5a6e9f10",
  "eventType": "AUDIT_SIGNED",
  "eventVersion": 1,
  "claimApplicationNo": "A2026110200000001",
  "tenantCode": "tenant-a",
  "occurredAt": "2026-11-02T10:00:00+08:00",
  "traceId": "b3a5c1d2e4f6",
  "payload": {
    "caseNos": ["CASE-2026-0001", "CASE-2026-0002"]
  }
}
```

| 处理结果 | 条件 |
|---|---|
| `COMPLETED` | 申请处于 `WAIT_AUDIT`，本次追加缺失案件关联并迁移 `COMPLETED`，同时写入完成时间 |
| `RECORDED` | 申请处于 `COMPLETED`，本次至少追加了一个新的案件号，状态与完成时间不变 |
| `REUSED` | 申请处于 `COMPLETED`，本次全部案件号已登记过 |
| `SKIPPED` | 同一 `eventId` 已被本消费组登记 |

审核签收在同一事务内完成消费登记、案件关联追加、`WAIT_AUDIT → COMPLETED` 迁移与轨迹写入。租户不一致、申请不存在、状态不是 `WAIT_AUDIT`/`COMPLETED`，或 `WAIT_AUDIT` 仍存在未关闭问题件时，均以业务异常失败，不写入任何业务数据，由 MQ 重投后进入死信。案件生命周期由案件服务承载。

样例中的 `eventVersion`、`occurredAt` 属于历史信封字段，平台不再读取；审核系统继续携带这两个字段或其他契约外字段时按未知字段忽略，不影响处理。

## InteractionLog 行为分组

InteractionLog 以“一个事件一条记录”保存跨系统和材料处理轨迹。`interaction_no` 将同一行为的多条事件组成行为组，`interaction_type` 标识行为类型，`action` 标识该事件的阶段。

影像上传示例：

| interactionNo | interactionType | action | resultStatus | occurredAt |
|---|---|---|---|---|
| `IN202610270001` | `IMAGE_UPLOAD` | `STARTED` | `PROCESSING` | `2026-10-27T10:00:00+08:00` |
| `IN202610270001` | `IMAGE_UPLOAD` | `COMPLETED` | `SUCCESS` | `2026-10-27T10:00:03+08:00` |

查询同一 `interaction_no` 并按 `occurred_at` 升序排列，即可恢复一个完整行为。业务失败时新增 `action=FAILED` 的独立事件，不更新或覆盖开始事件；外部调用抛异常导致消费失败时不写任何轨迹，申请保持原状态，由消息重投恢复。

## 外部系统接入

外部系统以领域端口接入，端口只声明业务能力，不绑定传输方式。当前版本的文件系统、承保系统与审核系统均为日志实现：只打印调用日志并返回可继续流程的默认结果，不发起 HTTP 调用。接入真实系统时新增对应适配器并替换日志实现，调用方无需改动。

| 端口 | 职责 | 默认实现 | 默认行为 |
|---|---|---|---|
| `MaterialFileProcessor` | 上传材料文件 | `LoggingMaterialFileProcessor` | 返回成功，文件地址保留租户提交的原始地址 |
| `UnderwritingSystemPort` | 有效保单查询、发起自动承保、查询承保结果 | `LoggingUnderwritingSystem` | 按无有效保单返回（进入自动承保）；承保结果按 `PROCESSING` 返回 |
| `AuditSystemPort` | 交付申请给审核系统、查询签收状态 | `LoggingAuditSystem` | 交付只记日志；签收状态按 `PROCESSING` 返回 |

约定：

- 外部系统明确拒绝以业务结果返回（如 `FileUploadResult.success=false`），进入业务失败分支。
- 外部系统不可用或超时以异常抛出，调用方保持申请原状态，由消息重投或补偿任务恢复。
- 交互日志只打印 `claimApplicationNo` 等业务标识，不打印证件号、文件地址与报文。
- 日志实现不做延迟、失败注入或结果模拟；需要验证其他分支时通过审核签收消息或承保回调消息驱动。
