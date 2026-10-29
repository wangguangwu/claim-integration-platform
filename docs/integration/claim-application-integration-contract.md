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
| 错误码 | `APPLICATION_INTAKE_INVALID`、`APPLICATION_INTAKE_BUSY`、`APPLICATION_INTAKE_SYSTEM_ERROR` | `CLAIM_APPLICATION_INTAKE_INVALID`、`CLAIM_APPLICATION_INTAKE_BUSY`、`CLAIM_APPLICATION_INTAKE_SYSTEM_ERROR` |
| HTTP 接口前缀 | `/api/application/v1`，网关 `Path=/api/application/**` | `/api/claim/v1`，网关 `Path=/api/claim/**` |

MQ Topic 与消费者组名、数据库名与 Redis 键前缀均保持不变；租户私有入参字段名不受影响。


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
| `/api/claim/v1/application/intake` | `POST` | 租户系统 | 提交理赔申请| `externalClaimApplicationNo` |
| `/api/claim/v1/application/{claimApplicationNo}/materials` | `POST` | 租户系统 | 提交补充材料 | `claimApplicationNo`、`batchNo` |
| `/api/claim/v1/application/{claimApplicationNo}/revoke` | `POST` | 租户系统 | 发起理赔申请撤销 | `claimApplicationNo` |
| `/api/claim/v1/callbacks/underwriting-completed` | `POST` | 承保系统 | 通知自动承保完成 | `claimApplicationNo`、`X-Request-Id` |
| `/api/claim/v1/callbacks/audit-signed` | `POST` | 审核系统 | 通知审核签收及全部案件 | `claimApplicationNo`、`caseNos`、`X-Request-Id` |
| `/api/claim/v1/callbacks/audit-issues` | `POST` | 审核系统 | 下发问题件 | `claimApplicationNo`、`issueNo`、`X-Request-Id` |

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
  "code": "INVALID_REQUEST",
  "message": "请求参数不合法",
  "traceId": "5bba2a7f468c4efe",
  "data": null
}
```

## MQ 契约

| Topic | 事件类型 | 消费者组 | 职责 |
|---|---|---|---|
| `claim_application_material_process_v1` | `CLAIM_APPLICATION_CREATED`、`CLAIM_APPLICATION_REAPPLIED` | `claim-application-material-processor-v1` | 异步处理首次申请或重新申请的材料 |
| `claim_application_audit_delivery_v1` | `AUDIT_DELIVERY_REQUESTED` | `claim-application-audit-delivery-v1` | 将已具备审核条件的申请交付审核系统 |

### 消费失败与死信

消费函数不捕获业务异常，异常直接抛给 binder，由 RocketMQ 按消费组重投；重投次数由 `spring.cloud.stream.rocketmq.bindings.<binding>.consumer.push.max-reconsume-times` 声明，默认 3 次，可用环境变量 `CLAIM_APPLICATION_MQ_MAX_RECONSUME_TIMES` 覆盖。重投耗尽后 broker 自动写入死信主题，平台不消费死信。

| 消费组 | 死信主题 |
|---|---|
| `claim-application-material-processor-v1` | `%DLQ%claim-application-material-processor-v1` |
| `claim-application-audit-delivery-v1` | `%DLQ%claim-application-audit-delivery-v1` |

平台不区分可重试与不可重试异常：事件结构非法、申请不存在这类错误同样重投后进入死信，由人工排查重放。消息幂等由 `t_claim_consumed_event` 的 `(consumer_group, event_id)` 唯一键兜底。

消息信封：

```json
{
  "eventId": "019a9c8c-4d02-7d19-9f16-32d447d4ee66",
  "eventType": "CLAIM_APPLICATION_CREATED",
  "eventVersion": 1,
  "claimApplicationNo": "AP202610270001",
  "tenantCode": "tenant-a",
  "occurredAt": "2026-10-27T10:00:00+08:00",
  "traceId": "5bba2a7f468c4efe",
  "payload": {}
}
```

`payload` 不得包含完整人员 JSON、证件号、银行卡号、Token 或密钥。消费者需要业务明细时，以 `claimApplicationNo` 读取理赔申请私有数据。

## InteractionLog 行为分组

InteractionLog 以“一个事件一条记录”保存跨系统和材料处理轨迹。`interaction_no` 将同一行为的多条事件组成行为组，`interaction_type` 标识行为类型，`action` 标识该事件的阶段。

影像上传示例：

| interactionNo | interactionType | action | resultStatus | occurredAt |
|---|---|---|---|---|
| `IN202610270001` | `IMAGE_UPLOAD` | `STARTED` | `PROCESSING` | `2026-10-27T10:00:00+08:00` |
| `IN202610270001` | `IMAGE_UPLOAD` | `COMPLETED` | `SUCCESS` | `2026-10-27T10:00:03+08:00` |

查询同一 `interaction_no` 并按 `occurred_at` 升序排列，即可恢复一个完整行为。失败时新增 `action=FAILED` 的独立事件，不更新或覆盖开始事件。

## 外部系统接入

外部系统以领域端口接入，端口只声明业务能力，不绑定传输方式。当前版本的文件系统、承保系统与审核系统均为进程内本地实现：按配置休眠、打印交互日志并按配置返回结果，不发起 HTTP 调用。接入真实系统时新增对应适配器并替换本地实现，调用方无需改动。

| 端口 | 职责 | 本地实现 | 配置 |
|---|---|---|---|
| `MaterialFileProcessor` | 上传材料文件 | `LocalMaterialFileProcessor` | `claim.integration.local.delay`、`fail-material-types` |
| `ValidPolicyQuery` | 查询被保人是否存在有效保单 | `LocalValidPolicyQuery` | `claim.integration.local.valid-policy` |
| `AutoUnderwritingRequester` | 发起自动承保 | `LocalAutoUnderwritingRequester` | `claim.integration.local.delay` |
| `UnderwritingResultQuery` | 查询自动承保结果 | `LocalUnderwritingResultQuery` | `claim.integration.local.underwriting-result` |
| `ServiceAuditSystemPort` | 交付申请给审核系统 | `LocalAuditSystemClient` | `claim.integration.local.fail-audit-delivery` |

约定：

- 外部系统明确拒绝以业务结果返回（如 `FileUploadResult.success=false`），进入业务失败分支。
- 外部系统不可用或超时以异常抛出，调用方保持申请原状态，由消息重投或补偿任务恢复。
- 交互日志只打印目标系统、交互类型、`claimApplicationNo` 与模拟耗时，不打印证件号、文件地址与报文。
