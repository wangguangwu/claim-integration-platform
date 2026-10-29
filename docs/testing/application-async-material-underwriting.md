# 理赔申请签收后异步链路本地验证说明

## 文档位置

本文件记录 004 规格（材料处理与承保分流）与 009 规格（签收后异步链路）在本地 MQ 与 MySQL 上的端到端验证步骤。验证对象是 `claim-application-service` 自身的实现，文件系统、承保系统与审核系统由服务内的本地实现提供，不依赖真实外部系统。

脚本目录不放置验证说明，后续仅在本文件维护。

## 覆盖场景

| 场景 | 触发方式 | 预期业务结果 |
|---|---|---|
| 签收后自动投递 | 提交申请报文 | 签收成功写入 Outbox，投递器把事件发到材料处理 Topic |
| 材料处理成功 | 材料类型不在失败名单中 | 材料变为 `PROCESSED`，申请进入承保分流 |
| 无有效保单 | `claim.integration.local.valid-policy=false`（默认） | 申请进入 `UNDERWRITING`，承保结果查询返回 `COMPLETED` 后经补偿推进到 `WAIT_AUDIT` |
| 有有效保单 | `claim.integration.local.valid-policy=true` | 申请直接进入 `WAIT_AUDIT`，不发起自动承保 |
| 审核交付 | 申请进入 `WAIT_AUDIT` | 消费审核交付 Topic，写入 `AUDIT_DELIVERY` 行为组 |
| 重复消息与重复回调 | 重投同一条消息或重复调用回调 | 不产生重复材料处理与重复审核交付 |
| 材料处理失败 | 材料类型命中 `claim.integration.local.fail-material-types` | 申请进入 `ACCEPT_FAILED`，记录失败交互 |
| MQ 停机与待受理滞留补偿 | 停止 Broker 后提交申请，等待超过 `pending-accept-timeout` | 签收仍成功；补偿直接重放材料处理并推进状态 |
| 审核交付失败进死信 | `fail-audit-delivery=true` 且重投次数为 1 | 重投耗尽后消息转入死信主题 |

## 前置准备

1. 启动本地依赖：`./scripts/docker-start.sh`，并按 [本地基础服务检查说明](../environment/local-infrastructure-health-check.md) 确认 MySQL、Redis、RocketMQ 健康。
2. 使用 JDK 21 启动 `claim-application-service`，默认 profile 为 `local`。
3. 本地实现默认参数：`delay=200ms`、`valid-policy=false`、`underwriting-result=COMPLETED`、`fail-material-types=[]`、`fail-audit-delivery=false`。
4. 确认服务健康：`curl -fsS http://127.0.0.1:8081/actuator/health` 返回 `UP`。

数据库仅为本地 Docker 开发库，命令统一使用最小权限账号：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "SHOW TABLES;"
```

启动时覆盖本地实现参数，配置生效即重启即可，不需要另外启动模拟服务：

```bash
java -jar backend/claim-application-service/target/claim-application-service-*.jar \
  --claim.integration.local.valid-policy=true \
  --claim.integration.local.delay=0ms
```

## 提交申请

`tenant-a` 租户报文要求申请人、被保险人、出险人、领款人四个人员节点与 `attachments` 材料数组，性别只支持 `M`、`F`。

```bash
curl -sS -X POST http://127.0.0.1:8081/api/claim/v1/application/intake \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Code: tenant-a' \
  -H 'X-Protocol-Version: v1' \
  -H 'X-Request-Id: verify-009-001' \
  -H 'X-Trace-Id: trace-009-001' \
  -d '{
    "requestNo": "VERIFY-009-001",
    "applicant": {"fullName": "张三", "sex": "M", "idType": "ID_CARD", "idNo": "110101199001010011"},
    "insured":   {"fullName": "张三", "sex": "M", "idType": "ID_CARD", "idNo": "110101199001010011"},
    "accident":  {"fullName": "张三", "sex": "M", "idType": "ID_CARD", "idNo": "110101199001010011"},
    "payee":     {"fullName": "张三", "sex": "M", "idType": "ID_CARD", "idNo": "110101199001010011"},
    "attachments": [
      {"type": "MATERIAL_TYPE_DIAGNOSIS", "sourceUrl": "https://files.example.com/diagnosis.pdf"}
    ]
  }'
```

响应 `data.claimApplicationNo` 即平台申请编号，后续命令用变量承接：

```bash
APP_NO=$(curl -sS -X POST http://127.0.0.1:8081/api/claim/v1/application/intake \
  -H 'Content-Type: application/json' -H 'X-Tenant-Code: tenant-a' -H 'X-Protocol-Version: v1' \
  -H 'X-Request-Id: verify-009-001' -H 'X-Trace-Id: trace-009-001' -d @request.json \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["claimApplicationNo"])')
echo "$APP_NO"
```

同一租户外部申请号重复提交会返回同一个 `claimApplicationNo`，不重复创建数据。

## 场景一：无有效保单的黄金路径

`valid-policy` 为 `false` 时，链路为：签收 → 投递 → 材料处理 → `UNDERWRITING` → 承保结果查询补偿 → `WAIT_AUDIT` → 审核交付。

本地实现不发送承保完成通知，`UNDERWRITING` 由补偿任务查询承保结果推进。默认阈值 1 分钟，验证时缩短：

```bash
java -jar backend/claim-application-service/target/claim-application-service-*.jar \
  --claim.compensation.fixed-delay-ms=5000 \
  --claim.compensation.underwriting-timeout=10s
```

1. 提交申请后确认申请短暂处于承保中与已受理之间的状态：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db \
  -e "SELECT claim_application_no, status, claim_application_attempt FROM t_claim_application WHERE claim_application_no='$APP_NO';"
```

2. 等待超过一轮补偿后核对最终状态、材料、轨迹、事件与消费登记：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "
  SELECT status FROM t_claim_application WHERE claim_application_no='$APP_NO';
  SELECT material_type, status, uploaded_file_url FROM t_claim_application_material WHERE claim_application_no='$APP_NO';
  SELECT interaction_no, interaction_type, action, result_status, request_id, error_code
    FROM t_claim_interaction_log WHERE claim_application_no='$APP_NO' ORDER BY id;
  SELECT event_type, status, retry_count, published_at
    FROM t_claim_outbox_event WHERE claim_application_no='$APP_NO' ORDER BY id;
  SELECT consumer_group, event_id FROM t_claim_consumed_event WHERE claim_application_no='$APP_NO';"
```

预期：

- `t_claim_application.status = WAIT_AUDIT`。
- `t_claim_application_material.status = PROCESSED`，`uploaded_file_url` 形如 `https://local.invalid/material/<claimApplicationNo>/<materialType>`。
- `t_claim_interaction_log` 依次出现 `MATERIAL_PROCESS`、`UNDERWRITING`（含补偿查询）、`UNDERWRITING_CALLBACK` 与 `AUDIT_DELIVERY` 的 `STARTED`/`COMPLETED`，同一行为的 `interaction_no` 相同。
- 补偿推进的承保回调记录 `request_id = compensation-<claimApplicationNo>`。
- `t_claim_outbox_event` 中 `CLAIM_APPLICATION_CREATED` 与 `AUDIT_DELIVERY_REQUESTED` 均为 `PUBLISHED`。
- `t_claim_consumed_event` 含 `claim-application-material-processor-v1` 与 `claim-application-audit-delivery-v1` 两条记录。

## 场景二：存在有效保单直达待审核

1. 以 `--claim.integration.local.valid-policy=true` 重启服务。
2. 用新的外部申请号提交申请（报文同上，`requestNo` 改为 `VERIFY-009-002`），并重新赋值 `APP_NO`。
3. 预期申请直接为 `WAIT_AUDIT`，轨迹没有 `UNDERWRITING_CALLBACK`：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db \
  -e "SELECT interaction_type, action FROM t_claim_interaction_log WHERE claim_application_no='$APP_NO' ORDER BY id;"
```

验证结束后恢复默认参数重启：`valid-policy=false`。

## 场景三：审核交付消费

申请进入 `WAIT_AUDIT` 后，Outbox 投递器把 `AUDIT_DELIVERY_REQUESTED` 发到 `claim_application_audit_delivery_v1`，审核交付消费者写入行为组：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "
  SELECT interaction_no, action, result_status, request_id
    FROM t_claim_interaction_log
   WHERE claim_application_no='$APP_NO' AND interaction_type='AUDIT_DELIVERY' ORDER BY id;"
```

预期同一 `interaction_no` 下有 `STARTED` 与 `COMPLETED` 两条记录，`request_id` 为消息 `eventId`，申请状态保持 `WAIT_AUDIT` 不变。

## 场景四：重复消息与重复回调

1. 重复调用承保完成回调：

```bash
curl -sS -X POST http://127.0.0.1:8081/api/claim/v1/callbacks/underwriting-completed \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: verify-009-callback-dup' \
  -d "{\"claimApplicationNo\": \"$APP_NO\"}"
```

预期返回 200，`data.disposition` 为 `REUSED`；`t_claim_outbox_event` 中 `AUDIT_DELIVERY_REQUESTED` 仍只有一条，申请状态不变。

2. 重复消费验证：MQ 为至少一次投递，重复消息由 `t_claim_consumed_event` 的唯一约束拦截，材料不会重复上传、审核不会重复交付。核对 `t_claim_application_material` 行数不变、`t_claim_consumed_event` 中同一 `event_id` 仍只有一行。需要主动制造重复时，用 `mqadmin resetOffsetByTime` 回拨消费位点重投同一事件，日志出现「审核交付事件已消费，跳过处理」。

## 场景五：MQ 停机与待受理滞留补偿

1. 停止 Broker 后以短补偿参数重启服务：

```bash
docker stop claim-rocketmq-broker
java -jar backend/claim-application-service/target/claim-application-service-*.jar \
  --claim.compensation.fixed-delay-ms=5000 \
  --claim.compensation.pending-accept-timeout=10s
```

2. 提交申请。预期：

- 签收接口仍返回 `SUCCESS`，申请状态为 `PENDING_ACCEPT`；
- 就绪探针保持 `UP`（就绪组只包含数据库）；
- `t_claim_outbox_event` 中材料处理事件为 `PENDING`，`retry_count` 随投递失败递增，`next_retry_at` 按退避策略后移。

```bash
curl -fsS http://127.0.0.1:8081/actuator/health
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db \
  -e "SELECT event_type, status, retry_count, next_retry_at FROM t_claim_outbox_event WHERE claim_application_no='$APP_NO';"
```

3. 等待超过 `pending-accept-timeout` 后，补偿直接重放材料处理，申请离开 `PENDING_ACCEPT`：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "
  SELECT status FROM t_claim_application WHERE claim_application_no='$APP_NO';
  SELECT event_id FROM t_claim_consumed_event
   WHERE claim_application_no='$APP_NO' AND consumer_group='claim-application-material-processor-v1';"
```

预期申请推进到 `UNDERWRITING`（或 `WAIT_AUDIT`），消费登记的 `event_id` 为 `compensation-<claimApplicationNo>-<attempt>`。

4. 恢复 Broker：

```bash
docker start claim-rocketmq-broker
```

5. 等待一个投递周期（`claim.outbox.relay.fixed-delay-ms`，默认 2 秒）后，事件变为 `PUBLISHED`；原消息被消费时申请已离开 `PENDING_ACCEPT`，按重复事件记录 `REUSED` 行为组，不产生第二套材料结果。

## 场景六：材料处理失败

以失败名单启动服务后提交包含该材料类型的申请：

```bash
java -jar backend/claim-application-service/target/claim-application-service-*.jar \
  --claim.integration.local.fail-material-types=MATERIAL_TYPE_REJECTED
```

```json
{"type": "MATERIAL_TYPE_REJECTED", "sourceUrl": "https://files.example.com/rejected.pdf"}
```

预期申请进入 `ACCEPT_FAILED`，命中失败名单的材料置为 `FAILED` 且不写上传地址，其余材料正常 `PROCESSED`，`t_claim_interaction_log` 记录 `MATERIAL_PROCESS` 的 `FAILED` 及错误码 `LOCAL_UPLOAD_REJECTED`。相同租户外部申请号可再次提交触发重新申请，走 `CLAIM_APPLICATION_REAPPLIED` 事件重新处理材料。

注意：外部系统异常与明确拒绝不同，异常向上抛出由 binder 重投，材料与申请保持原状态；`claim.integration.local.delay` 可放大调用耗时，便于观察消费占用。

## 场景七：审核交付失败与死信

1. 以交付失败开关和最小重投次数重启服务：

```bash
java -jar backend/claim-application-service/target/claim-application-service-*.jar \
  --claim.integration.local.valid-policy=true \
  --claim.integration.local.fail-audit-delivery=true \
  --spring.cloud.stream.rocketmq.bindings.auditDelivery-in-0.consumer.push.max-reconsume-times=1
```

2. 提交申请，申请进入 `WAIT_AUDIT` 后审核交付固定失败：

- 应用日志出现「外部系统本地实现交付失败」ERROR，`claimApplicationNo` 可见、无证件号；次数等于初次投递加 `max-reconsume-times` 次重投（每次内部还会经 Spring Retry 重试若干次，日志条数因此多于投递次数）；
- 每次投递失败另有一条「审核交付消费失败」ERROR，含 `consumerGroup=claim-application-audit-delivery-v1`、`claimApplicationNo`、`eventId`；
- `t_claim_interaction_log` 没有 `AUDIT_DELIVERY` 行为组（外部调用失败，事务未开启）；
- 重投耗尽后消息进入死信主题 `%DLQ%claim-application-audit-delivery-v1`，平台不消费死信。

```bash
docker exec claim-rocketmq-broker /home/rocketmq/rocketmq-5.5.0/bin/mqadmin topicList \
  -n claim-rocketmq-namesrv:9876 | grep DLQ || echo "DLQ topic not found"
docker exec claim-rocketmq-broker /home/rocketmq/rocketmq-5.5.0/bin/mqadmin topicStatus \
  -n claim-rocketmq-namesrv:9876 -t '%DLQ%claim-application-audit-delivery-v1'
```

预期 `#Max Offset` 为 1；平台没有死信消费者，读数不会继续增长。

## 状态核对清单

| 检查点 | 表 | 预期 |
|---|---|---|
| 签收成功 | `t_claim_application` | `status = PENDING_ACCEPT` |
| 事件已生成 | `t_claim_outbox_event` | `status IN (PENDING, SENDING, PUBLISHED)` |
| 材料处理完成 | `t_claim_application_material` | `status = PROCESSED` |
| 无保单分流 | `t_claim_application` | `status = UNDERWRITING` 后推进 `WAIT_AUDIT` |
| 有保单分流 | `t_claim_application` | `status = WAIT_AUDIT` |
| 审核交付已投递 | `t_claim_outbox_event` | `event_type = AUDIT_DELIVERY_REQUESTED` 且 `status = PUBLISHED` |
| 审核交付已消费 | `t_claim_interaction_log` | `interaction_type = AUDIT_DELIVERY` 且含 `STARTED`/`COMPLETED` |
| 消费去重 | `t_claim_consumed_event` | 同一 `event_id` 只有一行 |
| 消费失败 | Broker | 重投耗尽后进入 `%DLQ%<消费组>` |

## 收尾

1. 停止本地服务，确认端口释放：`lsof -ti:8081 || echo "port 8081 released"`。
2. 恢复默认启动参数，确认 `valid-policy=false`、`fail-audit-delivery=false`、`fail-material-types` 为空。
3. 如需复位本地数据，使用 `./scripts/docker-stop.sh` 后删除数据卷并重新执行 `./scripts/docker-start.sh`；该操作只允许在本地开发环境执行。
