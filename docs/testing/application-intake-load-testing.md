# 理赔申请对外接入 JMeter 压测说明

## 文档位置

压测脚本位于 `scripts/jmeter/`，该目录只保留可执行的 `.jmx` 文件；完整的准备、执行和报告说明已迁移至本文件。

后续仅在 `docs/testing/` 维护压测说明，脚本目录不再放置 README。

## 压测目标与安全边界

脚本以租户调用方身份请求 `claim-gateway` 暴露的 `POST /api/claim/v1/application/intake`，验证唯一外部申请号的并发创建吞吐和同一外部申请号的幂等控制。压测覆盖网关 TraceId 生成、租户请求头校验、租户限流、路由转发和理赔申请服务响应，不直接调用数据库、Repository、MQ 或应用服务 `8081` 端口。仅允许使用本地环境和虚构数据，禁止指向生产、测试共享环境或真实个人信息环境。

| 脚本 | 验证场景 | 默认负载 | 预期业务结果 |
|---|---|---|---|
| `application-intake-throughput.jmx` | 每次请求产生不同 `requestNo` | 10 线程，5 秒升压，每线程 10 次 | 全部返回 HTTP 200、`code=SUCCESS` 和 `X-Trace-Id` |
| `application-intake-idempotency.jmx` | 所有线程提交相同 `requestNo` | 20 线程，1 秒升压，每线程 1 次 | 全部返回 HTTP 200、`code=SUCCESS` 和 `X-Trace-Id`；脚本提取并断言所有响应中的 `claimApplicationNo` 一致 |

## 前置准备

1. 启动本地服务：`./scripts/docker-start.sh`，并按 [本地基础服务检查说明](../environment/local-infrastructure-health-check.md) 确认依赖健康。
2. 使用 JDK 21 启动 `claim-application-service`，确认 `http://127.0.0.1:8081/actuator/health` 返回 `UP`。
3. 启动 `claim-gateway`，确认 `http://127.0.0.1:8080/actuator/health` 返回 `UP`。三条路由的目标地址统一按「环境变量 `CLAIM_*_SERVICE_URI` > 当前 profile 默认值」取值：local profile 直连本机服务端口，其他 profile 默认 `lb://` 走注册中心负载均衡，需要指向特定实例时用环境变量覆盖。
4. 安装 Apache JMeter 5.6+：`brew install jmeter`，再执行 `jmeter --version` 验证。
5. 按 [网关限流](#网关限流) 先把两层阈值抬到高于目标吞吐再启动网关。默认 `tenant-a` 配额是 20 QPS，直接跑默认负载就会触发 429 并计入 `Error %`。

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

### 失败样本的判定

| JTL 中的表现 | 含义 | 处理 |
|---|---|---|
| `responseCode=429`，响应体 `CLAIM_APPLICATION_RATE_LIMITED` | 租户配额或路由兜底生效，请求没有进入业务服务 | 抬高阈值后重跑；不是脚本缺陷 |
| `Non HTTP response code: ...HttpHostConnectException` / `Connection refused` | 网关没有监听该地址端口，全部样本失败 | 确认网关进程与 `-JHOST`/`-JPORT`，恢复后重跑；本次结果作废 |
| `responseCode=200`，但 `failureMessage` 含脚本异常或编号不一致 | 断言失败 | 按 `failureMessage` 排查，不要只看 HTTP 状态 |

脚本不提供回退到应用服务 `8081` 的开关：绕过网关会丢掉租户校验、限流与 `X-Trace-Id` 生成，这样的“成功”没有意义。只有单独测应用本体吞吐时才直连 `8081`，同时抬高阈值，并在结论里注明「不含网关」。本地只有一个网关实例，网关不可用时签收入口整体不可用；生产多网关节点位于负载均衡之后，阈值按「单节点 × 节点数」计算。

## 网关限流

网关对签收路由执行两层限流，阈值都是单节点 QPS：租户全局上界 = 单节点阈值 × 网关节点数。

- 路由兜底：`CLAIM_APPLICATION_RATE_LIMIT_DEFAULT_QPS`（默认 50），不区分接口与租户。
- 接口级租户配额：`CLAIM_APPLICATION_RATE_LIMIT_TENANT_A_QPS`（默认 20）只作用于签收接口 `claim-application-intake`，同一租户的承保回调不受该配额影响；接口路径由 `CLAIM_APPLICATION_RATE_LIMIT_INTAKE_PATH` 指定，默认 `/api/claim/v1/application/intake`。

压测脚本固定使用 `tenant-a`，而默认配额 20 低于压测吞吐，直接跑吞吐脚本会把 429 计入 `Error %`。压测吞吐前把两层阈值都调到高于目标吞吐再启动网关：

```bash
export CLAIM_APPLICATION_RATE_LIMIT_TENANT_A_QPS=500
export CLAIM_APPLICATION_RATE_LIMIT_DEFAULT_QPS=500
```

### 限流演练

1. 用 `CLAIM_APPLICATION_RATE_LIMIT_TENANT_A_QPS=2` 重启网关，连续提交 50 次签收请求：`tenant-a` 应返回 HTTP 429 与 `code=CLAIM_APPLICATION_RATE_LIMITED`，响应头带 `X-Trace-Id`；预期首批 2 次返回 200，其余 429。
2. 配额被打满时，同一 `tenant-a` 调用承保回调 `POST /api/claim/v1/callbacks/underwriting-completed` 应正常返回 200，证明接口级配额只作用于签收接口。
3. 同时用 `X-Tenant-Code: tenant-b` 提交签收，请求应正常返回 200，证明租户之间互不影响。
4. 核对理赔申请服务日志：被限流的 `tenant-a` 请求没有签收日志，`t_claim_interaction_log` 也没有对应轨迹，说明请求没有进入业务服务。
5. 恢复阈值并重启网关后，同样的请求重新返回 200。

缺少 `X-Tenant-Code` 或 `X-Protocol-Version` 的请求由网关直接返回 400 `TENANT_HEADER_MISSING`，不进入限流统计。

### 控制台观测

控制台只做观测，不承载规则：`deploy/docker-compose.yml` 的 `sentinel-dashboard` 服务监听 `8858`，账号取自 `deploy/.env` 的 `SENTINEL_DASHBOARD_USERNAME` / `SENTINEL_DASHBOARD_PASSWORD`。

1. 启动后先发一次签收请求，Sentinel 在首次业务调用时初始化并开始每 10 秒心跳。
2. 控制台「机器列表」应出现 `claim-gateway`，地址为网关所在宿主机的网卡 IP 与命令端口 `8719`。
3. 「网关流控规则」应看到 2 条规则：路由兜底 50（资源 `claim-application-service`，无参数项）与签收接口 `tenant-a` 配额（资源 `claim-application-intake`，参数 `X-Tenant-Code`）。
4. 「机器」目录下的实时监控应能看到 `claim-application-intake` 的 `passQps` 与 `blockQps`。
5. 在控制台临时改一条规则，重启网关后应恢复为配置文件中的值，证明规则不依赖控制台持久化。

> 控制台会校验上报的心跳 IP 必须是字面 IP，主机名会被拒绝且客户端只记一条静默失败。默认留空 `CLAIM_GATEWAY_SENTINEL_CLIENT_IP`，由 Sentinel 自动探测宿主机网卡 IP；容器化或跨网段部署时显式设置为控制台容器可达的宿主机 IP。

## Redis 停机演练

1. 停掉 Redis：`docker stop claim-redis`。
2. 通过网关连续提交 10 个不同外部申请号：全部返回 200，编号互不相同；应用日志出现降级 WARN `Redis 不可用，签收进入降级路径，reason=…，concurrency=…`，相邻两条间隔不小于 10 秒。
3. 恢复 Redis：`docker start claim-redis`，等待健康检查通过后继续提交。
4. 核对编号不回退：新编号尾号必须大于停机和降级期间已落库的最大尾号，且 Redis 计数器尾号不小于数据库最大尾号：

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e \
  "SELECT MAX(claim_application_no) FROM t_claim_application WHERE claim_application_no LIKE 'A$(date +%Y%m%d)%';"

docker exec claim-redis redis-cli --user claim_app --pass claim_redis --no-auth-warning \
  GET "claim-applicationnumber:$(date +%Y%m%d)"
```

降级期间本节点签收并发受信号量限制（默认 10），超出直接返回 500 `CLAIM_APPLICATION_INTAKE_SYSTEM_ERROR`；这类响应属于预期保护行为，不是压测失败样本。

## 清理压测数据

压测通过对外接口产生的数据由本地环境生命周期统一清理；本压测不以直接查询、插入或删除数据库作为执行步骤或验收依据。本项目不提供自动清理脚本，避免误删其他本地调试数据。
