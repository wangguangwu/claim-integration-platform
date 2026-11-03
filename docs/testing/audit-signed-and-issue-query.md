# 审核签收 MQ 与问题件查询本地验证说明

## 覆盖场景

| 场景 | 触发方式 | 预期结果 |
|---|---|---|
| 审核签收多案件号 | 发送携带 2 个案件号的 `AUDIT_SIGNED` 消息 | 案件关联新增 2 行，轨迹新增 `STARTED`/`COMPLETED`，消费登记新增 1 行 |
| 重复签收事件 | 重发同一 `eventId` | 三张表行数不变，日志出现「审核签收事件已消费，跳过处理」 |
| 部分案件号重叠 | 发送新 `eventId`，携带 1 个已存在、1 个新案件号 | 只补插缺失案件号，轨迹结果与新增数量一致 |
| 审核签收申请不存在 | 发送不存在的 `claimApplicationNo` | 重投耗尽后进入 `%DLQ%claim-application-audit-signed-v1` |
| 问题件下发与关闭 | 经网关调用问题件接口 | 返回 2xx，申请在 `WAIT_AUDIT` 与 `ISSUE` 间流转 |
| 问题件业务拒绝 | 状态不合法、重复关闭、问题件不存在 | 返回 4xx 与明确错误码，不进入死信 |
| 问题件状态查询 | 按案件号查询未关闭问题件 | 分别返回有、无、案件关系不存在三种结果 |
| 重新申请 | 携带平台申请编号，申请处于 `ACCEPT_FAILED` | 返回 `REAPPLIED`，人员与材料整体替换，旧材料逻辑删除，尝试次数加 1 |
| 租户补材 | 携带平台申请编号，申请处于 `ISSUE` | 返回 `SUPPLEMENTED`，问题件关闭、人员保持原值、材料换批次、状态回到 `PENDING_ACCEPT` |
| 平台编号不匹配 | 携带与库中记录不一致的 `claimApplicationNo` | 返回 400 `CLAIM_APPLICATION_INTAKE_INVALID`，不新建申请 |
| 详情查询 | 查询材料、问题件、案件关联 | 返回摘要与对应集合；跨租户与不存在返回 404，空集合返回 200 + 空数组 |
| 旧路径失效 | 调用 `/api/application/v1/callbacks/**` | 返回 404 |

## 前置准备

1. 启动本地依赖：`./scripts/docker-start.sh`，确认 `claim_application_audit_signed_v1` 已创建。
2. 使用 JDK 21 启动 `claim-application-service`，默认 profile 为 `local`。
3. 确认服务健康：`curl -fsS http://127.0.0.1:8081/actuator/health` 返回 `UP`。
4. 启动网关 `claim-gateway`（8080 端口），问题件 HTTP 接口经网关验证。

准备一笔处于 `WAIT_AUDIT` 的申请（`valid-policy=true` 时直达待审核）：

```bash
java -jar backend/claim-application-service/target/claim-application-service-*.jar \
  --claim.integration.local.valid-policy=true
```

```bash
APP_NO=$(curl -sS -X POST http://127.0.0.1:8081/api/claim/v1/application/intake \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Code: tenant-a' \
  -H 'X-Protocol-Version: v1' \
  -H 'X-Request-Id: verify-018-intake-001' \
  -H 'X-Trace-Id: trace-018-intake-001' \
  -d '{
    "requestNo": "VERIFY-018-001",
    "applicant": {"fullName": "张三", "sex": "M", "idType": "ID_CARD", "idNo": "110101199001010011"},
    "insured":   {"fullName": "张三", "sex": "M", "idType": "ID_CARD", "idNo": "110101199001010011"},
    "accident":  {"fullName": "张三", "sex": "M", "idType": "ID_CARD", "idNo": "110101199001010011"},
    "payee":     {"fullName": "张三", "sex": "M", "idType": "ID_CARD", "idNo": "110101199001010011"},
    "attachments": [
      {"type": "MATERIAL_TYPE_DIAGNOSIS", "sourceUrl": "https://files.example.com/diagnosis.pdf"}
    ]
  }' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["claimApplicationNo"])')
echo "$APP_NO"
```

## 场景一：审核签收多案件号

消息体：

```json
{
  "eventId": "018-audit-signed-001",
  "eventType": "AUDIT_SIGNED",
  "eventVersion": 1,
  "claimApplicationNo": "<APP_NO>",
  "tenantCode": "tenant-a",
  "occurredAt": "2026-11-02T10:00:00+08:00",
  "traceId": "trace-018-signed-001",
  "payload": {
    "caseNos": ["CASE-2026-0001", "CASE-2026-0002"]
  }
}
```

消息中的 `eventVersion`、`occurredAt` 是历史信封字段，当前平台按未知字段忽略，保留它们用于验证兼容性。

发送命令：

```bash
docker exec claim-rocketmq-broker sh /home/rocketmq/rocketmq-5.5.0/bin/mqadmin sendMessage \
  -n rocketmq-namesrv:9876 \
  -t claim_application_audit_signed_v1 \
  -p "{\"eventId\":\"018-audit-signed-001\",\"eventType\":\"AUDIT_SIGNED\",\"eventVersion\":1,\"claimApplicationNo\":\"$APP_NO\",\"tenantCode\":\"tenant-a\",\"occurredAt\":\"2026-11-02T10:00:00+08:00\",\"traceId\":\"trace-018-signed-001\",\"payload\":{\"caseNos\":[\"CASE-2026-0001\",\"CASE-2026-0002\"]}}"
```

核对数据库：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "
  SELECT tenant_code, claim_application_no, case_no
    FROM t_claim_case_relation WHERE claim_application_no='$APP_NO' ORDER BY id;
  SELECT interaction_no, interaction_type, action, result_status, request_id
    FROM t_claim_interaction_log
   WHERE claim_application_no='$APP_NO' AND interaction_type='AUDIT_SIGNED' ORDER BY id;
  SELECT consumer_group, event_id
    FROM t_claim_consumed_event
   WHERE claim_application_no='$APP_NO' AND consumer_group='claim-application-audit-signed-v1';"
```

预期：

- 案件关联 2 行，`case_no` 分别为 `CASE-2026-0001`、`CASE-2026-0002`。
- 轨迹 2 行，同一 `interaction_no` 下为 `STARTED` 与 `COMPLETED`，`request_id` 为消息 `eventId`。
- 消费登记 1 行。
- 申请状态保持 `WAIT_AUDIT`，不因签收改变。

## 场景二：重复签收事件

重发场景一的同一条命令。预期三张表行数均不变，应用日志出现「审核签收事件已消费，跳过处理」。

## 场景三：部分案件号重叠

用新的 `eventId=018-audit-signed-002`，`caseNos` 改为 `["CASE-2026-0001", "CASE-2026-0003"]` 重新发送。预期：

- `t_claim_case_relation` 只新增 `CASE-2026-0003`，总数为 3；
- 轨迹新增一组 `STARTED`/`COMPLETED`；
- 新 `eventId` 新增一条消费登记。

## 场景四：审核签收申请不存在进入死信

1. 以最小重投次数重启服务：

```bash
java -jar backend/claim-application-service/target/claim-application-service-*.jar \
  --spring.cloud.stream.rocketmq.bindings.auditSigned-in-0.consumer.push.max-reconsume-times=1
```

2. 发送 `claimApplicationNo=NOT-EXIST-018` 的 `AUDIT_SIGNED` 消息。
3. 预期应用日志出现「审核签收消费失败…consumerGroup=claim-application-audit-signed-v1」，无案件关联与消费登记写入。
4. 重投耗尽后核对死信：

```bash
docker exec claim-rocketmq-broker /home/rocketmq/rocketmq-5.5.0/bin/mqadmin topicStatus \
  -n rocketmq-namesrv:9876 -t '%DLQ%claim-application-audit-signed-v1'
```

预期 `#Max Offset` 为 1，平台不消费死信。

## 场景五：问题件下发与关闭（经网关）

下发问题件：

```bash
curl -sS -X POST http://127.0.0.1:8080/api/claim/v1/callbacks/audit-issues \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Code: tenant-a' \
  -H 'X-Protocol-Version: v1' \
  -H 'X-Trace-Id: trace-018-issue-001' \
  -d "{
    \"claimApplicationNo\": \"$APP_NO\",
    \"issueNo\": \"ISSUE-2026-0001\",
    \"reason\": \"医疗票据缺失\",
    \"auditor\": \"auditor-01\",
    \"issueContent\": \"{\\\"missingMaterials\\\":[\\\"INVOICE\\\"]}\"
  }"
```

预期 200，`data.created=true`，申请状态变为 `ISSUE`。重复发送同一请求返回 200 且 `data.created=false`。

关闭问题件：

```bash
curl -sS -X POST http://127.0.0.1:8080/api/claim/v1/callbacks/audit-issues/ISSUE-2026-0001/close \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Code: tenant-a' \
  -H 'X-Protocol-Version: v1' \
  -H 'X-Trace-Id: trace-018-issue-002' \
  -d '{"auditor": "auditor-01"}'
```

预期 200，`data.closed=true`，申请恢复 `WAIT_AUDIT`。重复关闭返回 200 且 `data.closed=false`。

## 场景六：问题件业务拒绝

| 请求 | 预期 HTTP 状态 | 错误码 |
|---|---|---|
| 向非 `WAIT_AUDIT` 申请下发问题件 | 400 | `ISSUE_APPLICATION_STATUS_INVALID` |
| 已有未关闭问题件且申请状态为 `WAIT_AUDIT`（并发窗口或状态漂移） | 409 | `ISSUE_ALREADY_OPEN` |
| 关闭不存在的问题件 | 404 | `ISSUE_NOT_FOUND` |
| 请求缺少必填字段 | 400 | `ISSUE_INTAKE_INVALID` |
| 请求报文无法解析 | 400 | `ISSUE_INTAKE_INVALID` |
| 调用旧路径 `/api/application/v1/callbacks/audit-issues` | 404 | 无业务错误码 |

串行重发同一 `issueNo` 返回 200 且 `created=false`；换成新 `issueNo` 时申请已处于 `ISSUE`，先命中状态校验返回 400。`ISSUE_ALREADY_OPEN` 只在申请状态与未关闭问题件不一致时命中，验证时可临时把申请状态改回 `WAIT_AUDIT` 制造该分支：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db \
  -e "UPDATE t_claim_application SET status='WAIT_AUDIT' WHERE claim_application_no='$APP_NO';"
```

验证后改回 `ISSUE`，再按场景五关闭问题件：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db \
  -e "UPDATE t_claim_application SET status='ISSUE' WHERE claim_application_no='$APP_NO';"
```

## 场景七：问题件状态查询

有未关闭问题件（下发后、关闭前）：

```bash
curl -sS "http://127.0.0.1:8080/api/claim/v1/cases/CASE-2026-0001/issue" \
  -H 'X-Tenant-Code: tenant-a' \
  -H 'X-Protocol-Version: v1' \
  -H 'X-Trace-Id: trace-018-query-001'
```

预期 200，`data.hasOpenIssue=true`，返回 `issueNo`、`reason`、`raisedAt`。

关闭后再次查询：预期 200，`data.hasOpenIssue=false`，其余问题件字段为 `null`。

查询不存在的案件号：预期 404，`code=CASE_RELATION_NOT_FOUND`。

## 场景八：重新申请与租户补材

### 重新申请

1. 以材料失败配置启动服务：`--claim.integration.local.fail-material-types=MATERIAL_TYPE_DIAGNOSIS`。
2. 提交一笔新申请并等待材料处理完成，确认状态为 `ACCEPT_FAILED`。
3. 去掉失败配置重启服务，使用同一 `requestNo` 并携带 `claimApplicationNo` 再次提交：

```bash
curl -sS -X POST http://127.0.0.1:8081/api/claim/v1/application/intake \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Code: tenant-a' \
  -H 'X-Protocol-Version: v1' \
  -H 'X-Request-Id: verify-024-reapply-001' \
  -H 'X-Trace-Id: trace-024-reapply-001' \
  -d "{
    \"requestNo\": \"VERIFY-024-REAPPLY\",
    \"claimApplicationNo\": \"$APP_NO\",
    \"applicant\": {\"fullName\": \"李四\", \"sex\": \"M\", \"idType\": \"ID_CARD\", \"idNo\": \"110101199001010022\"},
    \"insured\":   {\"fullName\": \"李四\", \"sex\": \"M\", \"idType\": \"ID_CARD\", \"idNo\": \"110101199001010022\"},
    \"accident\":  {\"fullName\": \"李四\", \"sex\": \"M\", \"idType\": \"ID_CARD\", \"idNo\": \"110101199001010022\"},
    \"payee\":     {\"fullName\": \"李四\", \"sex\": \"M\", \"idType\": \"ID_CARD\", \"idNo\": \"110101199001010022\"},
    \"attachments\": [
      {\"type\": \"MATERIAL_TYPE_DIAGNOSIS\", \"sourceUrl\": \"https://files.example.com/diagnosis-v2.pdf\"}
    ]
  }"
```

预期 200；数据库中状态为 `PENDING_ACCEPT`，`claim_application_attempt` 与 `version` 均加 1，旧材料 `deleted=1`，人员 JSON 已替换为李四。

### 租户补材

1. 以 `--claim.integration.local.valid-policy=true` 启动服务，创建 `VERIFY-024-SUPPLEMENT` 并等待进入 `WAIT_AUDIT`。
2. 按场景五下发问题件，确认状态为 `ISSUE`。
3. 携带 `claimApplicationNo` 提交补材，人员字段故意改为不同姓名，`attachments` 使用新文件地址。
4. 预期 200；状态回到 `PENDING_ACCEPT`，未关闭问题件 `closed_at` 非空且 `closed_by='TENANT_SUPPLEMENT'`，人员 JSON 仍为原值，旧材料 `deleted=1`，新批次材料写入，`claim_application_attempt` 与 `version` 均加 1。
5. 等待异步材料处理与承保分流完成，状态回到 `WAIT_AUDIT`；再次调用问题件关闭接口返回 `data.closed=false`。

## 场景九：理赔申请详情查询

```bash
curl -sS "http://127.0.0.1:8081/api/claim/v1/application/$APP_NO/materials" \
  -H 'X-Tenant-Code: tenant-a' -H 'X-Protocol-Version: v1' -H 'X-Trace-Id: trace-024-query-001'
curl -sS "http://127.0.0.1:8081/api/claim/v1/application/$APP_NO/issues" \
  -H 'X-Tenant-Code: tenant-a' -H 'X-Protocol-Version: v1' -H 'X-Trace-Id: trace-024-query-002'
curl -sS "http://127.0.0.1:8081/api/claim/v1/application/$APP_NO/cases" \
  -H 'X-Tenant-Code: tenant-a' -H 'X-Protocol-Version: v1' -H 'X-Trace-Id: trace-024-query-003'
```

预期：

- 三个接口均返回 200，`data` 包含申请摘要与对应集合；材料只含未删除行，问题件含历史记录，案件关联在审核签收后可见。
- 空集合返回 200 与空数组，不返回 404。
- 使用 `X-Tenant-Code: tenant-b` 查询同一申请返回 404 `CLAIM_APPLICATION_QUERY_NOT_FOUND`。
- 缺少租户请求头时返回 400 `CLAIM_APPLICATION_QUERY_INVALID`。
- 查询前后 `t_claim_interaction_log` 行数不变。

## 状态核对清单

| 检查点 | 表 | 预期 |
|---|---|---|
| 案件关联已登记 | `t_claim_case_relation` | 按 `claimApplicationNo` 可查到消息中的全部案件号 |
| 签收轨迹已记录 | `t_claim_interaction_log` | `interaction_type=AUDIT_SIGNED` 且含 `STARTED`/`COMPLETED` |
| 消费去重 | `t_claim_consumed_event` | 同一 `(consumer_group, event_id)` 只有一行 |
| 申请状态不被签收改变 | `t_claim_application` | 签收前后均为 `WAIT_AUDIT` |
| 问题件状态流转 | `t_claim_application` | 下发后 `ISSUE`，关闭后 `WAIT_AUDIT` |
| 补材闭环 | `t_claim_application`、`t_claim_material`、`t_claim_application_issue` | 状态回到 `PENDING_ACCEPT` 后重新到达 `WAIT_AUDIT`；问题件由 `TENANT_SUPPLEMENT` 关闭；旧材料逻辑删除 |
| 乐观锁 | `t_claim_application` | 重新申请与补材后 `claim_application_attempt` 与 `version` 均递增 |
| 详情查询只读 | `t_claim_interaction_log` | 查询前后行数不变；跨租户返回 404 |
| 消费失败 | Broker | 重投耗尽后进入 `%DLQ%claim-application-audit-signed-v1` |
