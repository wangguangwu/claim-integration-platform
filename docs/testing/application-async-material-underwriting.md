# 理赔申请签收后异步链路本地验证说明

## 文档位置

本文件记录 004 规格（材料处理与承保分流）与 009 规格（签收后异步链路）在本地 MQ 与 MySQL 上的端到端验证步骤。验证对象是 `claim-application-service` 自身的实现，文件系统、承保系统与审核系统由服务内的日志实现提供：只打印调用日志并返回可继续流程的默认结果，不依赖真实外部系统。

脚本目录不放置验证说明，后续仅在本文件维护。

## 覆盖场景

| 场景 | 触发方式 | 预期业务结果 |
|---|---|---|
| 签收后自动投递 | 提交申请报文 | 签收成功写入 Outbox，投递器把事件发到材料处理 Topic |
| 材料处理成功 | 提交合法材料 | 材料变为 `PROCESSED`，申请进入承保分流 |
| 无有效保单 | 默认行为（日志实现固定返回无有效保单） | 申请进入 `UNDERWRITING` |
| 承保完成 | 自动承保发起后日志实现固定返回 `COMPLETED` | 申请同步推进到 `WAIT_AUDIT`，生成审核交付事件 |
| 审核交付 | 申请进入 `WAIT_AUDIT` | 消费审核交付 Topic，写入 `AUDIT_DELIVERY` 行为组 |
| 重复消息与重复回调 | 重投同一条消息或重复调用回调 | 不产生重复材料处理与重复审核交付 |
| MQ 停机与待受理滞留补偿 | 停止 Broker 后提交申请，等待超过 `pending-accept-timeout` | 签收仍成功；补偿直接重放材料处理并推进状态 |

## 前置准备

1. 启动本地依赖：`./scripts/docker-start.sh`，并按 [本地基础服务检查说明](../environment/local-infrastructure-health-check.md) 确认 MySQL、Redis、RocketMQ 健康。
2. 使用 JDK 21 启动 `claim-application-service`，默认 profile 为 `local`。
3. 确认服务健康：`curl -fsS http://127.0.0.1:8081/actuator/health` 返回 `UP`。

数据库仅为本地 Docker 开发库，命令统一使用最小权限账号：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "SHOW TABLES;"
```

日志实现没有延迟、失败或结果开关，验证不同分支时通过自动承保与审核签收消息驱动，不需要额外启动模拟服务。

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

## 场景一：无有效保单进入承保中

日志实现固定按无有效保单返回，链路为：签收 → 投递 → 材料处理 → `UNDERWRITING` → 自动承保完成 → `WAIT_AUDIT` → 审核交付。

日志实现不发送承保完成通知，但在发起自动承保后查询结果固定返回 `COMPLETED`，服务同步推进 `UNDERWRITING → WAIT_AUDIT`，无需人工回调。查询失败或返回 `PROCESSING` 时保持承保中状态，交由补偿调度推进。

1. 提交申请后确认申请已进入待审核状态：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db \
  -e "SELECT claim_application_no, status, claim_application_attempt FROM t_claim_application WHERE claim_application_no='$APP_NO';"
```

2. 核对最终状态、材料、轨迹、事件与消费登记：

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
- `t_claim_application_material.status = PROCESSED`，`uploaded_file_url` 为租户提交的原始地址。
- `t_claim_interaction_log` 依次出现 `MATERIAL_PROCESS`、`UNDERWRITING`（含自动承保请求）、`UNDERWRITING_CALLBACK` 与 `AUDIT_DELIVERY` 的 `STARTED`/`COMPLETED`，同一行为的 `interaction_no` 相同。
- `t_claim_outbox_event` 中 `CLAIM_APPLICATION_CREATED` 与 `AUDIT_DELIVERY_REQUESTED` 均为 `PUBLISHED`。
- `t_claim_consumed_event` 含 `claim-application-material-processor-v1` 与 `claim-application-audit-delivery-v1` 两条记录。

## 场景二：存在有效保单直达待审核

该分支依赖承保系统返回“存在有效保单”。日志实现固定返回无有效保单，本地无法构造；接入真实承保系统或在 `LoggingUnderwritingSystem.hasValidPolicy` 中返回 `true` 后再验证：申请应直接进入 `WAIT_AUDIT`，轨迹没有 `UNDERWRITING_CALLBACK`。

## 场景三：审核交付消费

申请进入 `WAIT_AUDIT` 后，Outbox 投递器把 `AUDIT_DELIVERY_REQUESTED` 发到 `claim_application_audit_delivery_v1`，审核交付消费者写入行为组：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "
  SELECT interaction_no, action, result_status, request_id
    FROM t_claim_interaction_log
   WHERE claim_application_no='$APP_NO' AND interaction_type='AUDIT_DELIVERY' ORDER BY id;"
```

预期同一 `interaction_no` 下有 `STARTED` 与 `COMPLETED` 两条记录，`request_id` 为消息 `eventId`，申请状态保持 `WAIT_AUDIT` 不变。

## 场景四：重复消息

MQ 为至少一次投递，重复消息由 `t_claim_consumed_event` 的唯一约束拦截，材料不会重复上传、审核不会重复交付。核对 `t_claim_application_material` 行数不变、`t_claim_consumed_event` 中同一 `event_id` 仍只有一行。需要主动制造重复时，用 `mqadmin resetOffsetByTime` 回拨消费位点重投同一事件，日志出现「审核交付事件已消费，跳过处理」。

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

预期申请推进到 `UNDERWRITING`，消费登记的 `event_id` 为 `compensation-<claimApplicationNo>-<attempt>`。

4. 恢复 Broker：

```bash
docker start claim-rocketmq-broker
```

5. 等待一个投递周期（`claim.outbox.relay.fixed-delay-ms`，默认 2 秒）后，事件变为 `PUBLISHED`；原消息被消费时申请已离开 `PENDING_ACCEPT`，按重复事件记录 `REUSED` 行为组，不产生第二套材料结果。

## 场景六：材料处理失败

材料处理失败依赖文件系统明确拒绝（`FileUploadResult.success=false`）。`LoggingMaterialFileProcessor` 固定返回成功，本地无法构造该分支；接入真实文件服务后再验证：申请进入 `ACCEPT_FAILED`，被拒绝的材料置为 `FAILED` 且不写上传地址，`t_claim_interaction_log` 记录 `MATERIAL_PROCESS` 的 `FAILED` 与失败原因。重新申请必须携带原 `claimApplicationNo` 再次提交，走 `CLAIM_APPLICATION_REAPPLIED` 事件重新处理材料；未携带平台编号时按重复请求复用，不会触发重新申请。

租户补材复用 `CLAIM_APPLICATION_REAPPLIED` 事件：补材提交后申请回到 `PENDING_ACCEPT`，材料处理消费者按新的受理尝试次数重新处理新批次材料，再进入承保分流与审核交付。材料处理事务会校验 `PENDING_ACCEPT` 与 `claimApplicationAttempt`，过期批次只记录 `REUSED` 轨迹。

注意：外部系统异常与明确拒绝不同，异常向上抛出由 binder 重投，材料与申请保持原状态。

## 场景七：审核交付失败与死信

审核交付失败依赖审核系统抛出异常。`LoggingAuditSystem.deliver` 只记日志且不失败，本地无法构造该分支；接入真实审核系统后，以最小重投次数启动：

```bash
java -jar backend/claim-application-service/target/claim-application-service-*.jar \
  --spring.cloud.stream.rocketmq.bindings.auditDelivery-in-0.consumer.push.max-reconsume-times=1
```

重投耗尽后消息进入死信主题 `%DLQ%claim-application-audit-delivery-v1`，平台不消费死信：

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
| 无保单分流 | `t_claim_application` | `status = UNDERWRITING`，经承保完成回调推进 `WAIT_AUDIT` |
| 有保单分流 | `t_claim_application` | 需真实承保系统返回有效保单；命中时 `status = WAIT_AUDIT` |
| 审核交付已投递 | `t_claim_outbox_event` | `event_type = AUDIT_DELIVERY_REQUESTED` 且 `status = PUBLISHED` |
| 审核交付已消费 | `t_claim_interaction_log` | `interaction_type = AUDIT_DELIVERY` 且含 `STARTED`/`COMPLETED` |
| 消费去重 | `t_claim_consumed_event` | 同一 `event_id` 只有一行 |
| 消费失败 | Broker | 重投耗尽后进入 `%DLQ%<消费组>` |

## 收尾

1. 停止本地服务，确认端口释放：`lsof -ti:8081 || echo "port 8081 released"`。
2. 如需复位本地数据，使用 `./scripts/docker-stop.sh` 后删除数据卷并重新执行 `./scripts/docker-start.sh`；该操作只允许在本地开发环境执行。
