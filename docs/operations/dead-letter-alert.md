# 审核签收死信告警与人工重放

## 适用范围

本文件适用于 `claim-application-service` 的审核签收入站链路。材料处理与审核交付死信沿用同一套处理原则，只需替换主题与消费组。

| 项 | 值 |
|---|---|
| 原 Topic | `claim_application_audit_signed_v1` |
| 消费组 | `claim-application-audit-signed-v1` |
| 死信主题 | `%DLQ%claim-application-audit-signed-v1` |
| 重投上限 | `max-reconsume-times`，默认 3，环境变量 `CLAIM_APPLICATION_MQ_MAX_RECONSUME_TIMES` 覆盖 |
| 应用内处理 | 不消费死信、不自动重放 |

消息进入死信说明消费在重投耗尽后仍然失败。常见原因：申请不存在、消息载荷非法、租户不一致、申请状态不是 `WAIT_AUDIT`/`COMPLETED`、`WAIT_AUDIT` 仍存在未关闭问题件或数据库持续不可用。应用不会吞掉异常，失败原因以 ERROR 形式记录在应用日志，日志包含 `consumerGroup`、`claimApplicationNo`、`eventId` 与异常摘要。

| 失败原因 | 触发场景 | 处置建议 |
|---|---|---|
| 申请不存在 | 平台不存在该申请 | 确认 `claimApplicationNo`，必要时补建申请后重放 |
| 消息载荷非法 | 消息载荷非法（如案件号为空、超长） | 由审核系统修正消息后发送新事件 |
| 租户不一致 | 消息 `tenantCode` 与申请归属不一致 | 核对审核系统租户映射，修正后重发 |
| 状态不合法 | 申请状态不是 `WAIT_AUDIT`/`COMPLETED`，或 `WAIT_AUDIT` 存在未关闭问题件 | 核对申请当前状态与问题件，确认业务是否已变更 |
| 系统异常 | 未预期的系统异常 | 排查数据库与依赖后重放 |

## 告警邮件字段

告警由运维监控平台配置，SMTP 凭证、收件人与模板不进入业务服务。邮件至少包含：

| 字段 | 来源 |
|---|---|
| 环境 | 监控平台标签，例如 `local`、`staging`、`prod` |
| 告警时间 | 监控平台触发时间 |
| 死信主题 | `%DLQ%claim-application-audit-signed-v1` |
| 消费组 | `claim-application-audit-signed-v1` |
| `eventId` | 死信消息体 `eventId` |
| `claimApplicationNo` | 死信消息体 `claimApplicationNo` |
| 失败摘要 | 应用日志「审核签收消费失败」行的 `reason` |
| 处置建议 | 先排查失败原因，再按下方步骤重放 |

## 监控配置方式

方案一：使用 rocketmq-exporter 采集 Topic 位点，对死信主题最大位点增长配置阈值告警。指标标签中 Topic 为 `%DLQ%claim-application-audit-signed-v1`，位点大于 0 且持续增长即触发。

方案二：没有 exporter 时，由监控平台定时执行以下命令，解析 `#Max Offset`，非 0 或较上次增长即触发：

```bash
docker exec claim-rocketmq-broker /home/rocketmq/rocketmq-5.5.0/bin/mqadmin topicStatus \
  -n rocketmq-namesrv:9876 \
  -t '%DLQ%claim-application-audit-signed-v1'
```

在 RocketMQ Dashboard（本地 `http://127.0.0.1:8090`）的 `Topic` 页面同样可以查看死信主题最大位点。

## 人工排查与重放

1. 在应用日志中按 `eventId` 检索「审核签收消费失败」，确认失败原因：

```bash
grep '审核签收消费失败' <application-log> | grep '<eventId>'
```

2. 按失败原因修复数据或代码。申请不存在时先补建申请；载荷非法时由审核系统修正后重新发送新事件。
3. 在 RocketMQ Dashboard 的 `Message` 页面按死信主题检索消息，确认消息体中的 `eventId` 与 `claimApplicationNo`。
4. 使用 Dashboard 的「重新发送」把消息投回原 Topic，或复制消息体后手工重发：

```bash
docker exec claim-rocketmq-broker sh /home/rocketmq/rocketmq-5.5.0/bin/mqadmin sendMessage \
  -n rocketmq-namesrv:9876 \
  -t claim_application_audit_signed_v1 \
  -p '<死信消息体>'
```

5. 核对处理结果：

```sql
SELECT tenant_code, claim_application_no, status, completed_at
  FROM t_claim_application
 WHERE claim_application_no = '<claimApplicationNo>';

SELECT tenant_code, claim_application_no, case_no
  FROM t_claim_case_relation
 WHERE claim_application_no = '<claimApplicationNo>';

SELECT interaction_no, action, result_status, request_id
  FROM t_claim_interaction_log
 WHERE claim_application_no = '<claimApplicationNo>'
   AND interaction_type = 'AUDIT_SIGNED'
 ORDER BY id;

SELECT consumer_group, event_id
  FROM t_claim_consumed_event
 WHERE claim_application_no = '<claimApplicationNo>'
   AND consumer_group = 'claim-application-audit-signed-v1';
```

重放使用原 `eventId`。事务失败时消费登记一起回滚，重放会重新处理；若消费登记已存在，重放会按幂等跳过，不会重复写入案件关联与轨迹。`WAIT_AUDIT` 申请首次成功重放后状态为 `COMPLETED` 且 `completed_at` 非空；已完成申请重放只补插缺失案件关联，不改变状态与完成时间。

## 禁止操作

- 不要在未排查原因前反复重放，失败会再次进入死信。
- 不要删除死信主题或清空位点来消除告警，应先保留现场再处理。
- 不要让应用自动消费死信；死信处理是人工确认后的动作。
