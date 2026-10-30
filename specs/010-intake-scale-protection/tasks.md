# 010 任务清单

## 第 1 批 · 判定快照缓存

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T1 | 快照模型与端口 | `domain/claim/ClaimApplicationIntakeSnapshot`、`ClaimApplicationIntakeLookup`、`ClaimApplicationIntakeLookupKind`、`domain/claim/port/ClaimApplicationIntakeSnapshotQuery`；删除 `ClaimApplicationExistenceQuery` | 快照只能由 `ACCEPTED`、`UNDERWRITING`、`WAIT_AUDIT` 构造，越界构造抛业务异常；`REUSABLE` 结论下平台申请编号与状态非空；domain 包不引用 Redis 与 Spring |
| T2 | 配置绑定 | `infrastructure/intake/config/ClaimApplicationIntakeProperties`、`ClaimApplicationRedisCircuitProperties`、`claim-application-service` 的 `application.yml` 增加 `claim.intake.*` 与 `claim.redis.circuit.*` | 设计第七节的键全部可注入且缺省值生效；TTL 使用 `Duration` 类型；两个类分别绑定一个前缀，装配方式与 `LocalIntegrationProperties` 等现有配置类一致 |
| T3 | Redis 快照查询与进程内 L1 | `infrastructure/intake/cache/RedisClaimApplicationIntakeSnapshotQuery`；删除 `RedisClaimApplicationExistenceQuery` | 三种值（`A…\|状态`、`X`、`N`）的写入条件与 TTL 与设计一致；未命中只回源一次并回填；L1 TTL 5 秒、条目上限 10000、超限不写；`RedisException` 回源且不改变处置结论；全类无失效调用 |
| T4 | 用例接入 | `ClaimApplicationIntakeUseCase`、`ClaimApplicationIntakeTransactionService#recordReused`、`ClaimApplicationIntakeOutcome` | `REUSABLE` 直接返回 `REUSED`，只写 `STARTED`、`REUSED` 两条轨迹，不读聚合、不调用外部系统；`EXISTING` 进原事务流程，`intake` 的存在性入参由 `boolean` 改为查询结论，事务服务把回源观察到的可复用快照随结果返回，用例在事务提交后把 `X` 升级为 `A…\|状态`；`ABSENT` 生成编号并创建，创建成功后把键覆盖为 `X`；删除原 `evictAfterChanged` 与冲突后的缓存淘汰调用；唯一键重试次数取配置，默认 5 |

## 第 2 批 · 网关租户限流

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T5 | 依赖与配置 | `claim-gateway/pom.xml` 引入 `sentinel-spring-cloud-gateway-v6x-adapter`；`claim.gateway.rate-limit.*` 配置项 | 版本由现有 Spring Cloud Alibaba BOM 统一，不写死版本号；不引 `spring-cloud-starter-alibaba-sentinel`；`default-qps` 与租户阈值均可通过环境变量覆盖 |
| T6 | 规则装配与限流响应 | `claim-gateway` 的 `rate-limit` 包：`ClaimApplicationRateLimitProperties`、`ClaimApplicationRateLimitConfiguration`、`ClaimApplicationRateLimitBlockHandler` | 启动时加载每租户规则与默认规则，资源 `claim-application-service`、`PARAM_PARSE_STRATEGY_HEADER` + `X-Tenant-Code` + `EXACT`、统计窗口 1 秒；Sentinel 过滤器排序值为 `HIGHEST_PRECEDENCE + 20`，排在租户校验之后；租户阈值大于默认阈值时启动失败；超限返回 429 与 `CLAIM_APPLICATION_RATE_LIMITED`，响应体结构与现有 400 拒绝体一致；其他路由不限流 |

## 第 3 批 · Redis 降级

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T7 | Redis 可用性熔断 | `infrastructure/intake/resilience/RedisAvailabilityCircuit` | 连续失败达到阈值打开 10 秒，打开期间判定不再发起 Redis 调用；到期半开只放行一个探测，失败立即重新打开；成功关闭并置一次性恢复标记；打开、关闭各一条日志且不含报文与个人信息 |
| T8 | 发号降级与恢复追平 | `RedisMySqlClaimApplicationNoGenerator` | 熔断打开时在本节点 `synchronized` 段内取「当天最大尾号与本节点已分配尾号」的较大者加 1，只读最大尾号不足以避开同节点并发，降级路径不写 Redis；熔断关闭后首次发号先抬升计数器再 `INCR`，编号不回退、不撞已落库编号；编号格式不变 |
| T9 | 签收锁降级与并发闸门 | `RedissonClaimApplicationIntakeLock` | 熔断打开时跳过 Redis 直接无锁执行；放行前获取本节点信号量（默认 10），获取失败抛 `CLAIM_APPLICATION_INTAKE_SYSTEM_ERROR`，不排队、不进入事务；正常模式不获取信号量；降级日志含原因与当前并发数，相邻两条间隔不小于 10 秒 |

## 第 4 批 · 文档同步

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T10 | 契约与架构文档 | `docs/integration/claim-application-integration-contract.md`、`docs/architecture/claim-application-architecture.md`、`docs/architecture/ddd-standard-architecture.md` | 错误码表补 429；写明阈值口径为"单节点阈值 × 网关节点数"；写明快照键、三种值、TTL 与安全状态集合；写明 Redis 降级时锁、缓存、发号的行为 |
| T11 | 压测与演练文档 | `docs/testing/application-intake-load-testing.md` | 增加网关限流验证与 Redis 停机演练步骤：429 断言、降级日志间隔检查、恢复后编号不回退的核对方式；示例租户统一使用 `tenant-a`，不写个人信息 |

## 第 5 批 · 构建与运行栈验证

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T12 | 全反应堆构建 | `mvn -B clean package` | 9 个模块 SUCCESS；无未使用导入与临时代码 |
| T13 | 快照黄金路径 | 运行栈验证记录 | 首次签收创建成功；同一外部申请号重复提交返回 `REUSED`；第二次起日志显示快照命中且无 `findByTenantAndExternalClaimApplicationNo` 查询；轨迹只新增 `STARTED`、`REUSED` |
| T14 | 重新申请 | 运行栈验证记录 | 构造 `ACCEPT_FAILED` 后同一外部申请号返回 `REAPPLIED`；缓存中不存在该外部申请号的 `A…\|状态` 值；再次签收回到快照判定路径 |
| T15 | Redis 停机与恢复演练 | 运行栈验证记录 | 停机后连续 10 个申请全部成功且编号唯一；降级日志相邻两条间隔不小于 10 秒；恢复后继续提交编号不回退，Redis 计数器尾号不小于数据库最大尾号 |
| T16 | 网关限流演练 | 运行栈验证记录 | 临时调整某租户阈值后循环请求返回 429 与 `CLAIM_APPLICATION_RATE_LIMITED`，业务服务无该请求的签收日志与轨迹；其他租户不受影响；恢复阈值后放行 |
| T17 | 敏感信息检查 | 运行栈验证记录 | 应用与网关日志中证件号、报文原文、文件地址、缓存键值明文命中 0 |

## 第 6 批 · 接口级限流与控制台

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T18 | 限流细化到接口 | `ClaimApplicationRateLimitProperties`、`ApiPathMatchStrategy`、`ClaimApplicationRateLimitConfiguration`、`claim-gateway` 的 `application.yml` | 先 `GatewayApiDefinitionManager.loadApiDefinitions` 注册接口，再加载规则；每个「接口 + 租户」一条 `RESOURCE_MODE_CUSTOM_API_NAME` 参数规则，路由兜底规则保留；租户配额大于兜底阈值、接口名为空或路径不以 `/` 开头时启动失败；日志打印接口数、规则数与本层阈值 |
| T19 | 控制台上报注册 | `claim-gateway/pom.xml` 引入 `sentinel-transport-simple-http`；`monitoring/SentinelTransportEnvironmentPostProcessor` 与 `META-INF/spring.factories` | 在容器刷新前写入 `csp.sentinel.dashboard.server`、`csp.sentinel.api.port`、`project.name`，`client-ip` 有值时再写 `csp.sentinel.heartbeat.client.ip`；`dashboard-server` 为空时不写任何属性，网关正常启动 |
| T20 | 本地控制台编排 | `deploy/docker-compose.yml`、`deploy/.env.example`、`scripts/docker-start.sh` | 新增 `sentinel-dashboard` 服务，arm64 原生，宿主机 8858；登录账号密码从 `.env` 注入，仓库只留样例默认值；健康检查通过；`docker-start.sh` 输出中能看到该容器 |
| T21 | 文档同步 | 契约、架构、压测文档 | 写明两层限流、接口级配额口径、规则条数、控制台地址与"控制台不改规则"的约束 |
| T22 | 构建与运行栈验证 | 运行栈验证记录 | 全反应堆构建通过；控制台机器列表出现 `claim-gateway` 且能看到签收接口 QPS；签收接口 `tenant-a` 配额调到 2 QPS 后循环请求返回 429，同租户回调接口不受影响；重启后控制台临时规则失效 |

## 说明

- 本 Spec 不写单元测试，验证以运行栈黄金路径与故障演练为准。
- 任务顺序即执行顺序，前置任务未完成不进入下一批。
- 006 P1-3 曾以零引用删除 sentinel，本 Spec 因入口限流重新引入；不回改 006 的已完成任务，只在 T10 文档中说明现状。
- T6 的路由级规则在第 6 批扩展为「路由兜底 + 接口级租户配额」，路由兜底保留，签收与承保回调不再共用租户配额。
