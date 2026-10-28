# Application 接入 JMeter 压测说明

## 文档位置

压测脚本位于 `scripts/jmeter/`，该目录只保留可执行的 `.jmx` 文件；完整的准备、执行、报告和数据核对说明已迁移至本文件。

后续仅在 `docs/testing/` 维护压测说明，脚本目录不再放置 README。

## 压测目标与安全边界

脚本调用本地示例协议 `demo-insurer-a/v1`，验证唯一外部申请号的并发创建吞吐，以及同一外部申请号的幂等控制。压测会向 `claim_application_db` 写入 `t_application`、`t_material`、`t_interaction_log`、`t_outbox_event` 数据；仅允许使用本地 Docker 开发库和虚构数据，禁止指向生产、测试共享库或真实个人信息环境。

| 脚本 | 验证场景 | 默认负载 | 预期业务结果 |
|---|---|---|---|
| `application-intake-throughput.jmx` | 每次请求产生不同 `requestNo` | 10 线程，5 秒升压，每线程 10 次 | 每个请求创建一条 Application |
| `application-intake-idempotency.jmx` | 所有线程提交相同 `requestNo` | 20 线程，1 秒升压，每线程 1 次 | 全部请求返回同一个 applicationNo，只创建一条 Application |

## 前置准备

1. 启动本地服务：`./scripts/docker-start.sh`，并按 [本地基础服务检查说明](../environment/local-infrastructure-health-check.md) 确认依赖健康。
2. 使用 JDK 21 启动 `claim-application-service`，并通过 `curl -fsS http://127.0.0.1:8081/actuator/health` 确认 `UP`。
3. 安装 Apache JMeter 5.6+：`brew install jmeter`，再执行 `jmeter --version` 验证。

## 执行压测

默认吞吐压测：

```bash
jmeter -n -t scripts/jmeter/application-intake-throughput.jmx -l scripts/jmeter/results/throughput.jtl -e -o scripts/jmeter/results/throughput-report
```

50 线程、每线程 20 次的吞吐压测：

```bash
jmeter -n -t scripts/jmeter/application-intake-throughput.jmx -JTHREADS=50 -JRAMP_UP_SECONDS=10 -JLOOPS=20 -l scripts/jmeter/results/throughput-50x20.jtl -e -o scripts/jmeter/results/throughput-50x20-report
```

同单号幂等并发压测：

```bash
jmeter -n -t scripts/jmeter/application-intake-idempotency.jmx -JTHREADS=50 -JRAMP_UP_SECONDS=1 -JLOOPS=1 -JEXTERNAL_APPLICATION_NO=IDEMPOTENCY-20261028-001 -l scripts/jmeter/results/idempotency-20261028-001.jtl -e -o scripts/jmeter/results/idempotency-20261028-001-report
```

总请求量为 `THREADS × LOOPS`。每次幂等压测使用新的 `EXTERNAL_APPLICATION_NO`；脚本不自动测试重新申请，因为该场景需要 Application 先处于 `ACCEPT_FAILED`。

## 查看压测报告

浏览器打开 JMeter 生成的 HTML 报告：

```bash
open scripts/jmeter/results/throughput-50x20-report/index.html
open scripts/jmeter/results/idempotency-20261028-001-report/index.html
```

重点检查 `Error %` 是否为 `0%`、吞吐量、90%/95%/99% 高分位延迟及并发线程曲线。JTL 为 CSV 格式，使用以下命令查找失败样本：

```bash
rg ',false,' scripts/jmeter/results/idempotency-20261028-001.jtl
```

## 查看业务数据

以下查询以外部申请号 `IDEMPOTENCY-20261028-001` 为例。先获取 Application：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "SELECT id, tenant_code, application_no, external_application_no, status, application_attempt, created_at FROM t_application WHERE tenant_code = 'demo-insurer-a' AND external_application_no = 'IDEMPOTENCY-20261028-001';"
```

幂等压测预期只返回一行，`status=PENDING_ACCEPT`、`application_attempt=1`。将查询结果中的 `application_no` 替换为 `<APPLICATION_NO>`，继续核对材料、交互日志与 Outbox：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "SELECT application_no, batch_no, material_type, original_file_url, uploaded_file_url, status, deleted, created_at FROM t_material WHERE application_no = '<APPLICATION_NO>' ORDER BY id;"
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "SELECT interaction_no, action, result_status, request_id, trace_id, occurred_at FROM t_interaction_log WHERE application_no = '<APPLICATION_NO>' ORDER BY interaction_no, occurred_at;"
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "SELECT event_id, event_type, event_version, status, retry_count, created_at FROM t_outbox_event WHERE application_no = '<APPLICATION_NO>' ORDER BY id;"
```

首次签收应有同一材料批次、`PENDING_PROCESS` 材料、`STARTED + COMPLETED` 交互行为组和一条 `APPLICATION_CREATED` Outbox。幂等重复请求应产生 `STARTED + REUSED` 行为组，但不得新增 Material 或 Outbox。

## 清理压测数据

仅在确认目标为本地开发库且不再需要结果时，按 `tenant_code` 与外部申请号手动删除。本项目不提供自动清理脚本，避免误删其他本地调试数据。
