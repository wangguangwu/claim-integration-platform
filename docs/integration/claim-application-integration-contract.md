# 理赔申请集成契约

## 项目背景与目标

本文档定义 `claim-application-service` 在 `v1` 阶段使用的 HTTP 与 RocketMQ 公共契约。业务报文字段由后续 Application 业务 Spec 补充；本文档固定地址、方法、公共请求头、关联标识、Topic、消费者组和消息信封。

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
| `/api/application/v1/applications` | `POST` | 租户系统 | 提交理赔申请 | `externalApplicationNo` |
| `/api/application/v1/applications/{applicationNo}/materials` | `POST` | 租户系统 | 提交补充材料 | `applicationNo`、`batchNo` |
| `/api/application/v1/applications/{applicationNo}/revoke` | `POST` | 租户系统 | 发起 Application 撤销 | `applicationNo` |
| `/api/application/v1/callbacks/underwriting-completed` | `POST` | 承保系统 | 通知自动承保完成 | `applicationNo`、`X-Request-Id` |
| `/api/application/v1/callbacks/audit-signed` | `POST` | 审核系统 | 通知审核签收及全部 Case | `applicationNo`、`caseNos`、`X-Request-Id` |
| `/api/application/v1/callbacks/audit-issues` | `POST` | 审核系统 | 下发问题件 | `applicationNo`、`issueNo`、`X-Request-Id` |

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
| `claim_application_material_process_v1` | `APPLICATION_CREATED`、`APPLICATION_REAPPLIED` | `claim-application-material-processor-v1` | 异步处理首次申请或重新申请的材料 |
| `claim_application_audit_delivery_v1` | `AUDIT_DELIVERY_REQUESTED` | `claim-application-audit-delivery-v1` | 将已具备审核条件的申请交付审核系统 |

消息信封：

```json
{
  "eventId": "019a9c8c-4d02-7d19-9f16-32d447d4ee66",
  "eventType": "APPLICATION_CREATED",
  "eventVersion": 1,
  "applicationNo": "AP202610270001",
  "tenantCode": "tenant-a",
  "occurredAt": "2026-10-27T10:00:00+08:00",
  "traceId": "5bba2a7f468c4efe",
  "payload": {}
}
```

`payload` 不得包含完整人员 JSON、证件号、银行卡号、Token 或密钥。消费者需要业务明细时，以 `applicationNo` 读取 Application 私有数据。

## InteractionLog 行为分组

InteractionLog 以“一个事件一条记录”保存跨系统和材料处理轨迹。`interaction_no` 将同一行为的多条事件组成行为组，`interaction_type` 标识行为类型，`action` 标识该事件的阶段。

影像上传示例：

| interactionNo | interactionType | action | resultStatus | occurredAt |
|---|---|---|---|---|
| `IN202610270001` | `IMAGE_UPLOAD` | `STARTED` | `PROCESSING` | `2026-10-27T10:00:00+08:00` |
| `IN202610270001` | `IMAGE_UPLOAD` | `COMPLETED` | `SUCCESS` | `2026-10-27T10:00:03+08:00` |

查询同一 `interaction_no` 并按 `occurred_at` 升序排列，即可恢复一个完整行为。失败时新增 `action=FAILED` 的独立事件，不更新或覆盖开始事件。
