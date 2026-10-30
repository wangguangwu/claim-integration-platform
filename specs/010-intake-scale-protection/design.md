# 010 签收扩容保护设计

## 一、改动模块

| 模块 | 类或文件 | 改动 |
|---|---|---|
| `domain/claim` | 新增 `ClaimApplicationIntakeSnapshot`、`ClaimApplicationIntakeLookup`、`ClaimApplicationIntakeLookupKind` | 判定快照与查询结果模型 |
| `domain/claim/port` | 新增 `ClaimApplicationIntakeSnapshotQuery`；删除 `ClaimApplicationExistenceQuery` | 一次查询同时回答"是否可复用"与"是否存在" |
| `infrastructure/intake/cache` | 新增 `RedisClaimApplicationIntakeSnapshotQuery`；删除 `RedisClaimApplicationExistenceQuery` | 单键快照缓存 + 进程内 L1 |
| `infrastructure/intake/resilience` | 新增 `RedisAvailabilityCircuit` | Redis 可用性熔断 |
| `infrastructure/intake/number` | `RedisMySqlClaimApplicationNoGenerator` | 降级路径串行化；熔断恢复后抬升计数器 |
| `infrastructure/intake/lock` | `RedissonClaimApplicationIntakeLock` | 接入熔断；降级期并发闸门 |
| `usecase/intake` | `ClaimApplicationIntakeUseCase`、`ClaimApplicationIntakeTransactionService` | 快照命中直接复用；新增只写轨迹的 `recordReused`；事务服务的存在性入参由 `boolean` 改为查询结论；重试次数取配置 |
| `infrastructure/intake/config` | 新增 `ClaimApplicationIntakeProperties`、`ClaimApplicationRedisCircuitProperties` | `claim.intake.*` 与 `claim.redis.circuit.*` 配置绑定 |
| `claim-gateway` | 新增 `rate-limit` 包下规则装配与限流响应；`pom.xml` 引入 sentinel | 租户维度限流 |
| `docs` | 契约、架构、联调文档 | 限流、缓存与降级口径同步 |

## 二、核心链路

```mermaid
sequenceDiagram
    participant T as 租户
    participant GW as 网关 Sentinel
    participant W as 签收用例
    participant L1 as 进程内 L1
    participant R as Redis 快照
    participant DB as MySQL

    T->>GW: POST /api/claim/v1/application/intake
    GW->>GW: 租户维度限流（超限 429）
    GW->>W: 转发
    W->>L1: 查快照
    L1-->>W: 未命中
    W->>R: 查快照
    R-->>W: REUSABLE / EXISTING / ABSENT
    alt REUSABLE
        W->>DB: 只写 STARTED/REUSED 轨迹
        W-->>T: REUSED
    else ABSENT
        W->>W: 生成候选编号
        W->>DB: 事务内创建申请、材料、轨迹、事件
        W-->>T: CREATED
    else EXISTING
        W->>DB: 事务内读取聚合
        W->>DB: REUSED 或 REAPPLIED
    end
```

Redis 熔断打开时，`L1 → DB` 直连，锁降级为无锁并受降级并发闸门约束，发号走本节点串行降级路径。

## 三、类职责

| 类 | 职责 |
|---|---|
| `ClaimApplicationIntakeSnapshotQuery` | 按租户与外部申请号查询判定快照，返回可复用快照、仅存在、或不存在三种结论 |
| `ClaimApplicationIntakeLookup` | 查询结果：种类 + 平台申请编号 + 状态；`REUSABLE` 时前两者必填 |
| `RedisClaimApplicationIntakeSnapshotQuery` | 单键缓存实现：L1 → Redis → MySQL，回填规则与 TTL 在此定义 |
| `RedisAvailabilityCircuit` | 连续失败计数、熔断打开 10 秒、半开探测、恢复标记 |
| `RedisMySqlClaimApplicationNoGenerator` | Redis 计数发号；降级时本节点串行取最大号；恢复时抬升计数器 |
| `RedissonClaimApplicationIntakeLock` | 同外部申请号锁；熔断期无锁 + 降级并发闸门 |
| `ClaimApplicationIntakeProperties` | `claim.intake.*` 配置 |
| `ClaimApplicationRedisCircuitProperties` | `claim.redis.circuit.*` 配置 |
| `ClaimApplicationRateLimitProperties`（网关） | `claim.gateway.rate-limit.*` 配置 |
| `ClaimApplicationRateLimitConfiguration`（网关） | 启动加载租户限流规则 |
| `ClaimApplicationRateLimitBlockHandler`（网关） | 限流响应 429 与错误码 |

## 四、判定快照缓存

### 4.1 键、值与 TTL

- 键：`claim-applicationintake:` + `sha256(租户编码:外部申请号)`。
- 值三种：

| 值 | 含义 | TTL |
|---|---|---|
| `A2026102900000058\|WAIT_AUDIT` | 已过受理，可直接复用 | 1 小时 |
| `X` | 存在，但状态可变（`PENDING_ACCEPT` / `ACCEPT_FAILED`） | 1 分钟 |
| `N` | 不存在 | 1 分钟 |

### 4.2 命中与未命中

- `A…|状态`：直接返回 `REUSED`，只调用事务服务写 `STARTED`/`REUSED` 两条轨迹，不读聚合。
- `X`：跳过编号生成，进入原事务流程读聚合，由状态决定 `REUSED` 或 `REAPPLIED`；回源读到安全状态时顺带把 `X` 升级为 `A…|状态`。
- `N`：生成候选编号并创建；创建成功后把键覆盖为 `X`，避免空标记把刚创建的申请误判为不存在。
- 未命中：读一次聚合（`findByTenantAndExternalClaimApplicationNo`）。状态属于安全集合则写 `A…|状态` 并直接复用；状态可变写 `X`；不存在写 `N`。

空标记只用于回源确认不存在后的短暂占位，创建成功即被 `X` 覆盖；覆盖失败不影响正确性，最多让下一次请求多做一次唯一键冲突重试。

### 4.3 为什么不需要失效

安全集合 `ACCEPTED`、`UNDERWRITING`、`WAIT_AUDIT` 在 `ClaimApplicationStatus` 的状态机里单调不可逆：`PENDING_ACCEPT → ACCEPTED / ACCEPT_FAILED`，之后只能向 `UNDERWRITING`、`WAIT_AUDIT` 前进，永不回到 `ACCEPT_FAILED`。因此这三个状态对签收的答案恒为 `REUSED`，缓存不需要在材料处理、承保、回调、补偿四处做失效，也不存在"缓存说可复用、实际已受理失败"的错判。

`PENDING_ACCEPT` 与 `ACCEPT_FAILED` 恰恰是可变的两个状态，只写 `X` 而不写入快照，保证"受理失败仍可重新处理"。

### 4.4 进程内 L1

- 结构：`ConcurrentHashMap` 存值 + 写入时间，TTL 5 秒，条目上限 10000，超限不再写入（靠 TTL 自然淘汰）。
- 作用：Redis 故障时吸收重复探测，避免每请求都回源；因为只缓存上述三种结论，多节点短暂不一致最坏就是多读一次库。

## 五、网关租户限流

### 5.1 依赖与装配

网关新增 `spring-cloud-starter-alibaba-sentinel` 与 `sentinel-spring-cloud-gateway-adapter`，版本由现有 Spring Cloud Alibaba BOM 统一。规则不依赖控制台：启动时把配置转换成 `GatewayFlowRule` 并 `loadRules`，控制台接入留可选项。

### 5.2 规则

- 资源：`RESOURCE_MODE_ROUTE`，资源名 `claim-application-service`（与路由 id 一致）。
- 参数：`PARAM_PARSE_STRATEGY_HEADER`，字段 `X-Tenant-Code`，`EXACT` 匹配。
- 每个配置了阈值的租户生成一条规则；其余租户共用 `default-qps` 一条规则。
- 行为：`CONTROL_BEHAVIOR_DEFAULT`（快速失败），统计窗口 1 秒。

### 5.3 响应

`BlockRequestHandler` 写出 429 与 `{"code":"CLAIM_APPLICATION_RATE_LIMITED","message":"租户请求超过限流阈值"}`，与网关现有 400 拒绝体保持同一结构。

### 5.4 阈值口径

Sentinel 本地规则是节点独立的，租户全局上界 = 单节点阈值 × 网关节点数。配置注释、契约文档与联调文档都按这个口径写；需要精确全局配额时再上集群流控，本 Spec 不做。

## 六、Redis 降级

### 6.1 可用性熔断

- 连续失败 3 次打开 10 秒；打开期间锁、缓存、发号直接走降级，不再发起 Redis 调用。
- 到期进入半开：放行一个请求探测，成功即关闭并置恢复标记，失败重新打开。

### 6.2 发号降级

- 正常：`INCR claim-applicationnumber:{yyyyMMdd}`。
- 降级：本节点 `synchronized` 段内取当天最大尾号 + 1，避免同节点并发读出同一个号；跨节点冲突仍由唯一键与用例重试收敛。
- 恢复：消费一次恢复标记，先把计数器 CAS 抬升到数据库最大尾号，再继续 `INCR`，避免编号回退撞号。

### 6.3 降级并发闸门

熔断打开时，签收锁在放行前先获取本节点信号量（默认 10），获取失败直接抛 `CLAIM_APPLICATION_INTAKE_SYSTEM_ERROR`。闸门放在锁适配器里，因为它是每次签收唯一必经的切面；正常模式下不获取信号量。

### 6.4 日志与观测

- 降级日志：`Redis 不可用，签收进入降级路径，reason={}，concurrency={}`，相邻两条间隔不小于 10 秒。
- 熔断打开、关闭各打一条 WARN/INFO。
- 不打印报文、证件号与文件地址。

## 七、配置

```yaml
claim:
  intake:
    snapshot-cache:
      reusable-ttl: ${CLAIM_APPLICATION_INTAKE_SNAPSHOT_REUSABLE_TTL:1h}
      mutable-ttl: ${CLAIM_APPLICATION_INTAKE_SNAPSHOT_MUTABLE_TTL:1m}
      absent-ttl: ${CLAIM_APPLICATION_INTAKE_SNAPSHOT_ABSENT_TTL:1m}
      local-ttl: ${CLAIM_APPLICATION_INTAKE_SNAPSHOT_LOCAL_TTL:5s}
      local-max-entries: ${CLAIM_APPLICATION_INTAKE_SNAPSHOT_LOCAL_MAX_ENTRIES:10000}
    degraded-max-concurrency: ${CLAIM_APPLICATION_INTAKE_DEGRADED_MAX_CONCURRENCY:10}
    unique-key-retry-times: ${CLAIM_APPLICATION_INTAKE_UNIQUE_KEY_RETRY_TIMES:5}
  redis:
    circuit:
      failure-threshold: ${CLAIM_APPLICATION_REDIS_CIRCUIT_FAILURE_THRESHOLD:3}
      open-duration: ${CLAIM_APPLICATION_REDIS_CIRCUIT_OPEN_DURATION:10s}
      log-interval: ${CLAIM_APPLICATION_REDIS_CIRCUIT_LOG_INTERVAL:10s}
```

网关侧：

```yaml
claim:
  gateway:
    rate-limit:
      default-qps: ${CLAIM_APPLICATION_RATE_LIMIT_DEFAULT_QPS:50}
      tenants:
        tenant-a: ${CLAIM_APPLICATION_RATE_LIMIT_TENANT_A_QPS:20}
```

## 八、对现有功能的影响

| 功能 | 影响 |
|---|---|
| 首次签收 | 多一次缓存读；创建流程、轨迹与事件不变 |
| 重复签收 | 命中快照时不再读聚合，只写轨迹 |
| 受理失败重新申请 | 行为不变，仍回源读库并按状态条件更新 |
| 发号 | 正常路径不变；降级路径由"并发撞唯一键"改为"本节点串行" |
| 签收锁 | 正常路径不变；降级时多一道并发闸门 |
| MQ 消费、补偿、审核交付 | 无变化 |
| 网关其他路由 | 只对签收路由生效，其他路由不限流 |

## 九、验证方式

1. `mvn -B clean package` 全反应堆通过。
2. 黄金路径：签收 → `PENDING_ACCEPT`；同一外部申请号重复提交返回 `REUSED`，第二次起日志显示快照命中，`t_claim_application` 无新增查询（以日志与 SQL 计数核对）。
3. 重新申请：构造 `ACCEPT_FAILED` 后用同一外部申请号提交，返回 `REAPPLIED`，`Redis` 中无该键的快照值。
4. Redis 停机：`docker stop claim-redis` 后连续提交 10 个申请，全部成功且编号唯一；降级 WARN 间隔不小于 10 秒；`docker start claim-redis` 后继续提交，编号不回退。
5. 网关限流：把某租户阈值调到 2 QPS 后循环请求，返回 429 与 `CLAIM_APPLICATION_RATE_LIMITED`；其他租户不受影响。
6. 敏感信息：`grep` 应用与网关日志，确认无证件号、报文原文与文件地址。

## 十、不做什么

- 不做号段发号与序列表。
- 不做 Sentinel 集群流控、不做 Redis 全局分布式限流。
- 不做缓存失效广播，不引入多级缓存一致性协议。
- 不改变编号格式、签收入口路径与既有状态机。
- 不写单元测试：按项目口径，验证走运行栈黄金路径与故障演练。
