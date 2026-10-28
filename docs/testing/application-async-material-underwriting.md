# Application 材料处理与承保异步链路本地验证说明

## 文档位置

本文件记录 004 规格（材料处理与承保分流）在本地 MQ 与 MySQL 上的端到端验证步骤。验证对象是 `claim-application-service` 自身的实现，文件系统与承保系统由服务内的模拟实现提供，不依赖真实外部系统。

脚本目录不放置验证说明，后续仅在本文件维护。

## 覆盖场景

| 场景 | 触发方式 | 预期业务结果 |
|---|---|---|
| 签收后自动投递 | 提交申请报文 | 签收成功写入 Outbox，投递器把事件发到材料处理 Topic |
| 材料处理成功 | 材料类型不在失败名单中 | 材料变为 `PROCESSED`，申请进入承保分流 |
| 无有效保单 | 被保人未登记保单 | 申请进入 `UNDERWRITING`，发起自动承保 |
| 有有效保单 | 被保人已登记保单 | 申请直接进入 `WAIT_AUDIT`，不发起自动承保 |
| 承保完成通知 | 模拟承保自动完成或手工触发 | 回调入口幂等推进到 `WAIT_AUDIT` |
| 重复消息与重复回调 | 重投同一条消息或重复调用回调 | 不产生重复材料处理与重复审核交付事件 |
| 材料处理失败 | 材料类型命中 `claim.mock.file-system.fail-material-types` | 申请进入 `ACCEPT_FAILED`，记录失败交互 |
| 补偿查询 | 关闭承保完成通知 | 补偿任务查询承保结果并推进到 `WAIT_AUDIT` |
| MQ 停机 | 停止 `claim-rocketmq-broker` 后提交申请 | 签收仍成功，事件留在 Outbox；Broker 恢复后自动补齐投递 |

## 前置准备

1. 启动本地依赖：`./scripts/docker-start.sh`，并按 [本地基础服务检查说明](../environment/local-infrastructure-health-check.md) 确认 MySQL、Redis、RocketMQ 健康。
2. 使用 JDK 21 启动 `claim-application-service`，默认 profile 为 `local`，模拟开关 `claim.mock.enabled` 默认为 `true`。
3. 确认服务健康：`curl -fsS http://127.0.0.1:8081/actuator/health` 返回 `UP`。

数据库仅为本地 Docker 开发库，命令统一使用最小权限账号：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "SHOW TABLES;"
```

## 提交申请

`demo-insurer-a` 租户报文要求申请人、被保险人、出险人、领款人四个人员节点与 `attachments` 材料数组，性别只支持 `M`、`F`。

```bash
curl -sS -X POST http://127.0.0.1:8081/api/application/v1/applications \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Code: demo-insurer-a' \
  -H 'X-Protocol-Version: v1' \
  -H 'X-Request-Id: verify-004-001' \
  -H 'X-Trace-Id: trace-004-001' \
  -d '{
    "requestNo": "VERIFY-004-001",
    "applicant": {"fullName": "张三", "sex": "M", "idType": "ID_CARD", "idNo": "110101199001010011"},
    "insured":   {"fullName": "张三", "sex": "M", "idType": "ID_CARD", "idNo": "110101199001010011"},
    "accident":  {"fullName": "张三", "sex": "M", "idType": "ID_CARD", "idNo": "110101199001010011"},
    "payee":     {"fullName": "张三", "sex": "M", "idType": "ID_CARD", "idNo": "110101199001010011"},
    "attachments": [
      {"type": "MATERIAL_TYPE_DIAGNOSIS", "sourceUrl": "https://files.example.com/diagnosis.pdf"}
    ]
  }'
```

响应 `data.applicationNo` 即平台申请编号，后续命令用变量承接：

```bash
APP_NO=$(curl -sS -X POST http://127.0.0.1:8081/api/application/v1/applications \
  -H 'Content-Type: application/json' -H 'X-Tenant-Code: demo-insurer-a' -H 'X-Protocol-Version: v1' \
  -H 'X-Request-Id: verify-004-001' -H 'X-Trace-Id: trace-004-001' -d @request.json \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["applicationNo"])')
echo "$APP_NO"
```

同一租户外部申请号重复提交会返回同一个 `applicationNo`，不重复创建数据。

## 场景一：无有效保单的黄金路径

未登记保单时，链路为：签收 → 投递 → 材料处理 → 自动承保 → 完成通知 → `WAIT_AUDIT`。

1. 提交申请后确认申请进入承保中：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db \
  -e "SELECT application_no, status, application_attempt FROM t_claim_application WHERE application_no='$APP_NO';"
```

2. 默认模拟承保在 `claim.mock.underwriting.completion-delay`（默认 1 秒）后自动完成并发送通知；也可手工触发：

```bash
curl -sS -X POST "http://127.0.0.1:8081/mock/underwriting/auto-underwritings/$APP_NO/complete"
```

3. 最终状态应为 `WAIT_AUDIT`，材料应为 `PROCESSED` 且已填写模拟文件系统地址：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "
  SELECT status FROM t_claim_application WHERE application_no='$APP_NO';
  SELECT material_type, status, uploaded_file_url FROM t_claim_application_material WHERE application_no='$APP_NO';
  SELECT interaction_no, interaction_type, action, result_status, error_code
    FROM t_claim_interaction_log WHERE application_no='$APP_NO' ORDER BY id;
  SELECT event_type, status, retry_count, published_at
    FROM t_claim_outbox_event WHERE application_no='$APP_NO' ORDER BY id;
  SELECT consumer_group, event_id FROM t_claim_consumed_event WHERE application_no='$APP_NO';"
```

预期：

- `t_claim_application.status = WAIT_AUDIT`。
- `t_claim_application_material.status = PROCESSED`，`uploaded_file_url` 形如 `mock://file-system/<applicationNo>/<batchNo>/<materialType>`。
- `t_claim_interaction_log` 出现 `MATERIAL_PROCESS` 的 `STARTED`/`COMPLETED`、`UNDERWRITING` 的 `STARTED`/`COMPLETED` 与 `UNDERWRITING_CALLBACK` 的 `COMPLETED`，同一行为的 `interaction_no` 相同。
- `t_claim_outbox_event` 中 `APPLICATION_CREATED` 与 `AUDIT_DELIVERY_REQUESTED` 均为 `PUBLISHED`。
- `t_claim_consumed_event` 只有一条材料处理消费记录。

## 场景二：存在有效保单直达待审核

1. 先为被保人证件号登记有效保单：

```bash
curl -sS -X POST http://127.0.0.1:8081/mock/underwriting/policies \
  -H 'Content-Type: application/json' \
  -d '{"certificateNo": "110101199001010011", "policyNo": "POLICY-VERIFY-001"}'
```

2. 用新的外部申请号提交申请（报文同上，`requestNo` 改为 `VERIFY-004-002`），并重新赋值 `APP_NO`。

3. 预期申请直接为 `WAIT_AUDIT`，且不产生自动承保记录：

```bash
curl -sS "http://127.0.0.1:8081/mock/underwriting/v1/auto-underwritings/$APP_NO"
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db \
  -e "SELECT interaction_type, action FROM t_claim_interaction_log WHERE application_no='$APP_NO' ORDER BY id;"
```

自动承保查询返回 `NOT_FOUND`，交互轨迹只有 `MATERIAL_PROCESS` 与 `UNDERWRITING` 的分流记录，没有 `UNDERWRITING_CALLBACK`。

验证结束后清理保单，避免影响后续场景：

```bash
curl -sS -X DELETE http://127.0.0.1:8081/mock/underwriting/policies/110101199001010011
```

## 场景三：重复消息与重复回调

1. 重复调用承保完成回调：

```bash
curl -sS -X POST http://127.0.0.1:8081/api/application/v1/callbacks/underwriting-completed \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: verify-004-callback-dup' \
  -d "{\"applicationNo\": \"$APP_NO\"}"
```

预期返回 200，`data.disposition` 为 `REUSED`；`t_claim_outbox_event` 中 `AUDIT_DELIVERY_REQUESTED` 仍只有一条，申请状态不变。

2. 重复消费验证：MQ 为至少一次投递，重复消息由 `t_claim_consumed_event` 的唯一约束拦截，材料不会重复上传。核对 `t_claim_application_material` 行数不变、`t_claim_consumed_event` 中同一 `event_id` 仍只有一行。

## 场景四：关闭承保完成通知后的补偿推进

用于验证 `UNDERWRITING` 超时补偿。先停止默认启动的服务进程，再用关闭通知的参数重启：

```bash
java -jar backend/claim-application-service/target/claim-application-service-*.jar \
  --claim.mock.underwriting.notify-on-complete=false \
  --claim.compensation.fixed-delay-ms=10000
```

1. 用新的外部申请号提交申请，申请停在 `UNDERWRITING`，模拟承保记录为 `PROCESSING`。
2. 超过 `claim.compensation.underwriting-timeout`（默认 1 分钟）后，补偿任务查询承保结果；模拟承保到期自动完成后，补偿以 `compensation-<applicationNo>` 作为请求标识调用回调入口。
3. 预期申请推进到 `WAIT_AUDIT`，`t_claim_interaction_log` 中 `UNDERWRITING_CALLBACK` 的 `request_id` 为 `compensation-<applicationNo>`。

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "
  SELECT status FROM t_claim_application WHERE application_no='$APP_NO';
  SELECT interaction_type, action, request_id, result_status
    FROM t_claim_interaction_log WHERE application_no='$APP_NO' ORDER BY id;"
```

## 场景五：MQ 停机期间签收不受影响

1. 停止 Broker：

```bash
docker stop claim-rocketmq-broker
```

2. 提交申请。预期：

- 签收接口仍返回 `SUCCESS`，申请状态为 `PENDING_ACCEPT`；
- 就绪探针保持 `UP`（就绪组只包含数据库）；
- `t_claim_outbox_event` 中材料处理事件为 `PENDING`，`retry_count` 随投递失败递增，`next_retry_at` 按退避策略后移。

```bash
curl -fsS http://127.0.0.1:8081/actuator/health
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db \
  -e "SELECT event_type, status, retry_count, next_retry_at FROM t_claim_outbox_event WHERE application_no='$APP_NO';"
```

3. 恢复 Broker：

```bash
docker start claim-rocketmq-broker
```

4. 等待一个投递周期（`claim.outbox.relay.fixed-delay-ms`，默认 2 秒）后，事件变为 `PUBLISHED`，申请继续走材料处理与承保分流，最终进入 `WAIT_AUDIT`。

## 场景六：材料处理失败

在 `claim.mock.file-system.fail-material-types` 中加入 `MATERIAL_TYPE_REJECTED`（默认已配置）后，提交包含该材料类型的申请：

```json
{"type": "MATERIAL_TYPE_REJECTED", "sourceUrl": "https://files.example.com/rejected.pdf"}
```

预期申请进入 `ACCEPT_FAILED`，命中失败名单的材料置为 `FAILED` 且不写上传地址，其余材料正常 `PROCESSED`，`t_claim_interaction_log` 记录 `MATERIAL_PROCESS` 的 `FAILED` 及错误码（模拟文件系统返回 `MOCK_UPLOAD_REJECTED`）。相同租户外部申请号可再次提交触发重新申请，走 `APPLICATION_REAPPLIED` 事件重新处理材料。

注意：文件系统抛异常或超时与明确拒绝不同，此时异常向上抛出由 binder 重投，材料与申请保持原状态，`claim.mock.file-system.delay` 可用于验证该链路。

## 状态核对清单

| 检查点 | 表 | 预期 |
|---|---|---|
| 签收成功 | `t_claim_application` | `status = PENDING_ACCEPT` |
| 事件已生成 | `t_claim_outbox_event` | `status IN (PENDING, SENDING, PUBLISHED)` |
| 材料处理完成 | `t_claim_application_material` | `status = PROCESSED` |
| 无保单分流 | `t_claim_application` | `status = UNDERWRITING` |
| 有保单分流 | `t_claim_application` | `status = WAIT_AUDIT` |
| 承保完成 | `t_claim_application` | `status = WAIT_AUDIT` |
| 审核交付已投递 | `t_claim_outbox_event` | `event_type = AUDIT_DELIVERY_REQUESTED` 且 `status = PUBLISHED` |
| 消费去重 | `t_claim_consumed_event` | 同一 `event_id` 只有一行 |

## 收尾

1. 停止本地服务，确认端口释放：`lsof -ti:8081 || echo "port 8081 released"`。
2. 清除联调登记的保单：`curl -X DELETE http://127.0.0.1:8081/mock/underwriting/policies/<certificateNo>`。
3. 如需复位本地数据，使用 `./scripts/docker-stop.sh` 后删除数据卷并重新执行 `./scripts/docker-start.sh`；该操作只允许在本地开发环境执行。
