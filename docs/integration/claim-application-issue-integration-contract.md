# 问题件集成契约

## 功能概述

问题件链路用于审核系统在下发理赔申请审核结果时，发现材料不完整或存在疑问，向平台下发问题件并要求租户补充材料。平台记录问题件并更新申请状态为 `ISSUE`，待租户补材后审核系统关闭问题件，申请状态回退到 `WAIT_AUDIT` 继续审核流程。

核心能力：
- 审核系统通过 HTTP 回调下发问题件
- 平台记录问题件并更新申请状态
- 同一申请同一时间只允许一个未关闭问题件
- 审核系统关闭问题件后申请状态回退
- 完整的幂等性与并发控制

## 契约版本

- **版本**：v1
- **生效日期**：2026-10-31
- **关联 Spec**：012-problem-document-workflow、018-audit-inbound-mq-integration、024-intake-ddd-redesign

## HTTP 契约

### 公共请求头

| 请求头 | 适用范围 | 必填 | 说明 |
|---|---|---|---|
| `X-Trace-Id` | 全部接口 | 否 | 链路追踪标识，缺失时由网关生成并向下游透传 |
| `X-Request-Id` | 全部接口 | 否 | 请求标识，用于日志关联，不参与幂等判断 |
| `Content-Type` | 全部接口 | 是 | 固定为 `application/json` |

### 接口清单

| 接口 | 方法 | 调用方 | 用途 | 关键参数 |
|---|---|---|---|---|
| `/api/claim/v1/callbacks/audit-issues` | `POST` | 审核系统 | 下发问题件 | `claimApplicationNo`、`issueNo` |
| `/api/claim/v1/callbacks/audit-issues/{issueNo}/close` | `POST` | 审核系统 | 关闭问题件 | `issueNo`（路径参数） |

---

## 接口详情

### 1. 下发问题件

审核系统发现理赔申请材料不完整或存在疑问时，调用此接口下发问题件。

#### 请求

**HTTP 方法**：`POST`  
**路径**：`/api/claim/v1/callbacks/audit-issues`  
**Content-Type**：`application/json`

**请求体字段**：

| 字段 | 类型 | 必填 | 长度限制 | 说明 |
|---|---|---|---|---|
| `claimApplicationNo` | String | 是 | 64 | 平台生成的理赔申请编号 |
| `issueNo` | String | 是 | 64 | 审核系统生成的问题件编号，全局唯一 |
| `reason` | String | 是 | 512 | 问题件原因说明 |
| `auditor` | String | 是 | 128 | 创建问题件的审核人员标识 |
| `issueContent` | String | 是 | 64KB | 问题件详情 JSON，记录缺少的材料清单、疑问说明等 |

**请求示例**：

```json
{
  "claimApplicationNo": "CLA202610310001",
  "issueNo": "ISS202610310001",
  "reason": "缺少病历首页和出院小结",
  "auditor": "auditor_zhangsan",
  "issueContent": "{\"missingMaterials\":[\"病历首页\",\"出院小结\"],\"description\":\"请补充住院相关材料\"}"
}
```

#### 响应

**成功响应（新创建）**：

- **HTTP 状态码**：`200 OK`
- **Content-Type**：`application/json`

```json
{
  "success": true,
  "traceId": "5bba2a7f468c4efe",
  "data": {
    "issueNo": "ISS202610310001",
    "claimApplicationNo": "CLA202610310001",
    "created": true
  }
}
```

**成功响应（幂等命中）**：

当同一 `issueNo` 已存在时，返回成功但不重复创建，`created` 为 `false`。

```json
{
  "success": true,
  "traceId": "5bba2a7f468c4efe",
  "data": {
    "issueNo": "ISS202610310001",
    "claimApplicationNo": "CLA202610310001",
    "created": false
  }
}
```

**错误响应**：

| HTTP 状态码 | 错误码 | 说明 | 处理建议 |
|---|---|---|---|
| 400 | `ISSUE_INTAKE_INVALID` | 请求参数不合法（必填字段缺失或超长） | 检查请求体字段 |
| 404 | `ISSUE_APPLICATION_NOT_FOUND` | 申请不存在 | 确认 `claimApplicationNo` 是否正确 |
| 400 | `ISSUE_APPLICATION_STATUS_INVALID` | 申请状态不合法（不是 `WAIT_AUDIT`） | 确认申请当前状态 |
| 409 | `ISSUE_ALREADY_OPEN` | 同一申请已有未关闭问题件 | 等待现有问题件关闭后再下发 |
| 500 | `ISSUE_SYSTEM_ERROR` | 系统异常 | 联系平台运维 |

**错误响应示例**：

```json
{
  "success": false,
  "traceId": "5bba2a7f468c4efe",
  "error": {
    "code": "ISSUE_APPLICATION_NOT_FOUND",
    "message": "申请不存在，claimApplicationNo=CLA202610310001"
  }
}
```

#### 业务规则

1. **前置校验**：
   - 申请必须存在
   - 申请状态必须为 `WAIT_AUDIT`
   - 同一申请不能已有 `OPEN` 状态的问题件

2. **幂等性**：
   - 以 `issueNo` 为幂等键
   - 重复下发同一 `issueNo` 返回成功，`created=false`
   - 并发下发触发数据库唯一约束时，按幂等处理

3. **状态变更**：
   - 问题件创建成功后，申请状态从 `WAIT_AUDIT` 变更为 `ISSUE`
   - 问题件是 `ClaimApplication` 聚合内实体，与申请状态在同一次聚合 `save` 中提交

4. **并发控制**：
   - 数据库唯一约束 `uk_open_issue_claim_application` 保证同一申请最多一个 `OPEN` 问题件
   - 聚合保存使用 `t_claim_application.version` 乐观锁；保存失败时在保存事务之外重读：同一 `issueNo` 已落库按幂等成功返回，否则按状态冲突返回 400

---

### 2. 关闭问题件

租户补充材料后，审核系统确认材料完整，调用此接口关闭问题件。

#### 请求

**HTTP 方法**：`POST`  
**路径**：`/api/claim/v1/callbacks/audit-issues/{issueNo}/close`  
**Content-Type**：`application/json`

**路径参数**：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `issueNo` | String | 是 | 问题件编号 |

**请求体字段**：

| 字段 | 类型 | 必填 | 长度限制 | 说明 |
|---|---|---|---|---|
| `auditor` | String | 是 | 128 | 处理问题件的审核人员标识 |

**请求示例**：

```json
{
  "auditor": "auditor_lisi"
}
```

#### 响应

**成功响应（新关闭）**：

- **HTTP 状态码**：`200 OK`
- **Content-Type**：`application/json`

```json
{
  "success": true,
  "traceId": "5bba2a7f468c4efe",
  "data": {
    "issueNo": "ISS202610310001",
    "closed": true
  }
}
```

**成功响应（幂等命中）**：

当问题件已关闭时，返回成功但不重复处理，`closed` 为 `false`。

```json
{
  "success": true,
  "traceId": "5bba2a7f468c4efe",
  "data": {
    "issueNo": "ISS202610310001",
    "closed": false
  }
}
```

**错误响应**：

| HTTP 状态码 | 错误码 | 说明 | 处理建议 |
|---|---|---|---|
| 404 | `ISSUE_NOT_FOUND` | 问题件不存在 | 确认 `issueNo` 是否正确 |
| 404 | `ISSUE_APPLICATION_NOT_FOUND` | 问题件关联的申请不存在 | 数据异常，联系平台运维 |
| 500 | `ISSUE_SYSTEM_ERROR` | 系统异常 | 联系平台运维 |

**错误响应示例**：

```json
{
  "success": false,
  "traceId": "5bba2a7f468c4efe",
  "error": {
    "code": "ISSUE_NOT_FOUND",
    "message": "问题件不存在，issueNo=ISS202610310001"
  }
}
```

#### 业务规则

1. **前置校验**：
   - 问题件必须存在
   - 问题件关联的申请必须存在

2. **幂等性**：
   - 以 `issueNo` + 当前状态为幂等键
   - 已关闭的问题件重复关闭返回成功，`closed=false`

3. **状态变更**：
   - 问题件状态从 `OPEN` 变更为 `CLOSED`
   - 记录 `closedAt` 为当前时间
   - 更新 `auditor` 为处理人
   - 申请状态从 `ISSUE` 回退到 `WAIT_AUDIT`
   - 问题件是 `ClaimApplication` 聚合内实体，与申请状态在同一次聚合 `save` 中提交

---

## 状态机

### 申请状态流转

```
WAIT_AUDIT → ISSUE（下发问题件）
ISSUE → WAIT_AUDIT（关闭问题件）
```

### 问题件状态

```
OPEN（未关闭）
CLOSED（已关闭）
```

---

## 数据模型

### 问题件表（t_claim_application_issue）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | 自增主键 |
| `claim_application_no` | VARCHAR(64) | 所属理赔申请的平台申请编号 |
| `issue_no` | VARCHAR(64) | 平台问题件编号，全局唯一 |
| `reason` | VARCHAR(512) | 问题件原因说明 |
| `auditor` | VARCHAR(128) | 创建或处理问题件的审核人员标识 |
| `issue_content_json` | JSON | 问题件详情 JSON |
| `status` | VARCHAR(32) | 问题件处理状态（OPEN/CLOSED） |
| `is_open` | TINYINT(1) | 是否为未关闭问题件（1=OPEN, 0=CLOSED） |
| `open_claim_application_no` | VARCHAR(64) | 生成列，未关闭时等于 `claim_application_no`，用于唯一约束 |
| `closed_at` | DATETIME(3) | 问题件关闭时间，未关闭时为 NULL |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

**唯一约束**：
- `uk_issue_no`：`issue_no` 全局唯一
- `uk_open_issue_claim_application`：同一申请最多一个 `OPEN` 问题件（通过生成列 `open_claim_application_no` 实现）

**索引**：
- `idx_issue_claim_application_created_at`：按申请编号和创建时间查询

---

## 错误码汇总

| 错误码 | HTTP 状态 | 说明 | 触发场景 |
|---|---|---|---|
| `ISSUE_APPLICATION_NOT_FOUND` | 404 | 申请不存在 | 下发问题件时申请编号未找到 |
| `ISSUE_APPLICATION_STATUS_INVALID` | 400 | 申请状态不合法 | 下发问题时申请状态不是 `WAIT_AUDIT` |
| `ISSUE_ALREADY_OPEN` | 409 | 已有未关闭问题件 | 同一申请已有 `OPEN` 状态问题件 |
| `ISSUE_NOT_FOUND` | 404 | 问题件不存在 | 关闭时问题件编号未找到 |
| `ISSUE_INTAKE_INVALID` | 400 | 问题件命令不合法 | 必填字段缺失或超长 |
| `ISSUE_SYSTEM_ERROR` | 500 | 问题件系统异常 | 未预期的系统错误 |

---

## 调用示例

### 场景 1：正常下发与关闭

**Step 1：审核系统下发问题件**

```bash
curl -X POST http://localhost:8080/api/claim/v1/callbacks/audit-issues \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: trace-001" \
  -d '{
    "claimApplicationNo": "CLA202610310001",
    "issueNo": "ISS202610310001",
    "reason": "缺少病历首页",
    "auditor": "auditor_zhangsan",
    "issueContent": "{\"missingMaterials\":[\"病历首页\"]}"
  }'
```

**响应**：

```json
{
  "success": true,
  "traceId": "trace-001",
  "data": {
    "issueNo": "ISS202610310001",
    "claimApplicationNo": "CLA202610310001",
    "created": true
  }
}
```

**Step 2：租户补充材料后，审核系统关闭问题件**

```bash
curl -X POST http://localhost:8080/api/claim/v1/callbacks/audit-issues/ISS202610310001/close \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: trace-002" \
  -d '{
    "auditor": "auditor_lisi"
  }'
```

**响应**：

```json
{
  "success": true,
  "traceId": "trace-002",
  "data": {
    "issueNo": "ISS202610310001",
    "closed": true
  }
}
```

### 场景 2：幂等处理

**重复下发同一问题件**：

```bash
# 第一次下发
curl -X POST http://localhost:8080/api/claim/v1/callbacks/audit-issues \
  -H "Content-Type: application/json" \
  -d '{
    "claimApplicationNo": "CLA202610310001",
    "issueNo": "ISS202610310001",
    "reason": "缺少病历首页",
    "auditor": "auditor_zhangsan",
    "issueContent": "{\"missingMaterials\":[\"病历首页\"]}"
  }'
# 响应：created=true

# 第二次下发（相同 issueNo）
curl -X POST http://localhost:8080/api/claim/v1/callbacks/audit-issues \
  -H "Content-Type: application/json" \
  -d '{
    "claimApplicationNo": "CLA202610310001",
    "issueNo": "ISS202610310001",
    "reason": "缺少病历首页",
    "auditor": "auditor_zhangsan",
    "issueContent": "{\"missingMaterials\":[\"病历首页\"]}"
  }'
# 响应：created=false（幂等命中）
```

### 场景 3：错误处理

**申请不存在**：

```bash
curl -X POST http://localhost:8080/api/claim/v1/callbacks/audit-issues \
  -H "Content-Type: application/json" \
  -d '{
    "claimApplicationNo": "CLA_NOT_EXIST",
    "issueNo": "ISS202610310002",
    "reason": "缺少病历首页",
    "auditor": "auditor_zhangsan",
    "issueContent": "{\"missingMaterials\":[\"病历首页\"]}"
  }'
```

**响应**：

```json
{
  "success": false,
  "traceId": "trace-003",
  "error": {
    "code": "ISSUE_APPLICATION_NOT_FOUND",
    "message": "申请不存在，claimApplicationNo=CLA_NOT_EXIST"
  }
}
```

**HTTP 状态码**：`404 Not Found`

---

## 注意事项

1. **issueContent 格式**：
   - 必须为合法的 JSON 字符串
   - 大小不超过 64KB
   - 建议包含 `missingMaterials`（缺少材料清单）和 `description`（问题说明）

2. **幂等性保证**：
   - 审核系统重试时应保持 `issueNo` 不变
   - 平台保证同一 `issueNo` 只会创建一次问题件

3. **并发控制**：
   - 同一申请同一时间最多一个 `OPEN` 问题件
   - 并发下发时，后到的请求会返回幂等成功或 `ISSUE_ALREADY_OPEN` 错误

4. **事务一致性**：
   - 问题件状态与申请状态在同一事务内变更
   - 要么全部成功，要么全部回滚

5. **链路追踪**：
   - 建议审核系统在请求头中传入 `X-Trace-Id`
   - 平台会在响应中返回相同的 `traceId`，便于日志关联

---

## 关联文档

- [理赔申请集成契约](./claim-application-integration-contract.md)
- [Spec 012：问题件链路](../../specs/012-problem-document-workflow/)
  - [需求规格](../../specs/012-problem-document-workflow/requirements.md)
  - [技术设计](../../specs/012-problem-document-workflow/design.md)
  - [任务清单](../../specs/012-problem-document-workflow/tasks.md)
