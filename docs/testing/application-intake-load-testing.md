# 理赔申请对外接入 JMeter 压测说明

## 文档位置

压测脚本位于 `scripts/jmeter/`，该目录只保留可执行的 `.jmx` 文件；完整的准备、执行和报告说明已迁移至本文件。

后续仅在 `docs/testing/` 维护压测说明，脚本目录不再放置 README。

## 压测目标与安全边界

脚本以租户调用方身份请求 `claim-gateway` 暴露的 `POST /api/application/v1/claim-applications`，验证唯一外部申请号的并发创建吞吐和同一外部申请号的幂等控制。压测覆盖网关 TraceId 生成、租户请求头校验、路由转发和理赔申请服务响应，不直接调用数据库、Repository、MQ 或应用服务 `8081` 端口。仅允许使用本地环境和虚构数据，禁止指向生产、测试共享环境或真实个人信息环境。

| 脚本 | 验证场景 | 默认负载 | 预期业务结果 |
|---|---|---|---|
| `application-intake-throughput.jmx` | 每次请求产生不同 `requestNo` | 10 线程，5 秒升压，每线程 10 次 | 全部返回 HTTP 200、`code=SUCCESS` 和 `X-Trace-Id` |
| `application-intake-idempotency.jmx` | 所有线程提交相同 `requestNo` | 20 线程，1 秒升压，每线程 1 次 | 全部返回 HTTP 200、`code=SUCCESS` 和 `X-Trace-Id`；脚本提取并断言所有响应中的 `claimApplicationNo` 一致 |

## 前置准备

1. 启动本地服务：`./scripts/docker-start.sh`，并按 [本地基础服务检查说明](../environment/local-infrastructure-health-check.md) 确认依赖健康。
2. 使用 JDK 21 启动 `claim-application-service`，确认 `http://127.0.0.1:8081/actuator/health` 返回 `UP`。
3. 启动 `claim-gateway`，确认 `http://127.0.0.1:8080/actuator/health` 返回 `UP`。三条路由的目标地址统一按「环境变量 `CLAIM_*_SERVICE_URI` > 当前 profile 默认值」取值：local profile 直连本机服务端口，其他 profile 默认 `lb://` 走注册中心负载均衡，需要指向特定实例时用环境变量覆盖。
4. 安装 Apache JMeter 5.6+：`brew install jmeter`，再执行 `jmeter --version` 验证。

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

总请求量为 `THREADS × LOOPS`。脚本默认请求 `127.0.0.1:8080`；压测其他环境时通过 `-JHOST`、`-JPORT` 修改网关地址。每次幂等压测使用新的 `EXTERNAL_APPLICATION_NO`；脚本不自动测试重新申请，因为该场景需要理赔申请先处于 `ACCEPT_FAILED`。

## 查看压测报告

浏览器打开 JMeter 生成的 HTML 报告：

```bash
open scripts/jmeter/results/throughput-50x20-report/index.html
open scripts/jmeter/results/idempotency-20261028-001-report/index.html
```

重点检查 `Error %` 是否为 `0%`、吞吐量、90%/95%/99% 高分位延迟及并发线程曲线。每个成功样本还必须同时通过 HTTP 200、`code=SUCCESS` 与 `X-Trace-Id` 响应头断言；这证明请求经过了网关入口。JTL 为 CSV 格式，使用以下命令查找失败样本：

```bash
rg ',false,' scripts/jmeter/results/idempotency-20261028-001.jtl
```

## 清理压测数据

压测通过对外接口产生的数据由本地环境生命周期统一清理；本压测不以直接查询、插入或删除数据库作为执行步骤或验收依据。本项目不提供自动清理脚本，避免误删其他本地调试数据。
