# 011 任务清单

## 第 1 批 · 测试依赖与基础设施

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T1 | 测试依赖引入 | `claim-application-service/pom.xml` | 新增 `junit-jupiter`、`mockito-core`、`mockito-junit-jupiter`、`assertj-core` 四个测试依赖；版本号由现有 BOM 统一管理，BOM 中没有的写死版本号；`mvn test-compile` 通过 |
| T2 | 测试目录结构 | `src/test/java/com/wangguangwu/claim/application/` | 创建 `test/intake`、`test/audit`、`test/resilience`、`test/number` 四个测试包；包结构与生产代码对应 |

## 第 2 批 · 签收用例测试

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T3 | 签收用例单元测试 | `test/intake/ClaimApplicationIntakeUseCaseTest` | 覆盖 5 个场景：首次创建返回 `CREATED`、重复命中缓存返回 `REUSED` 且不读聚合、重复未命中缓存回源后返回 `REUSED` 并回填快照、唯一键冲突重试成功返回 `REUSED`、重试耗尽抛 `IllegalStateException`；全部使用 Mock，不启动 Spring 容器；`mvn test` 通过 |

## 第 3 批 · 审核交付测试与异常规范化

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T4 | 审核交付异常规范化 | `AuditDeliveryUseCase` | 找不到申请时改抛 `ClaimApplicationIntakeException`（或项目统一业务异常），异常信息包含 `claimApplicationNo` 与 `eventId`；不暴露堆栈与内部实现；不包含证件号等敏感信息 |
| T5 | 审核交付用例单元测试 | `test/audit/AuditDeliveryUseCaseTest` | 覆盖 4 个场景：首次到达调审核系统并写轨迹、重复到达跳过处理且不调审核系统、找不到申请抛业务异常且异常信息包含业务标识、状态不匹配按重复事件处理且只写 `REUSED` 轨迹；全部使用 Mock，不启动 Spring 容器；`mvn test` 通过 |

## 第 4 批 · Redis 熔断测试

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T6 | Redis 熔断单元测试 | `test/resilience/RedisAvailabilityCircuitTest` | 覆盖 6 个场景：关闭态 `tryAcquire()` 返回 true、打开态 `tryAcquire()` 返回 false、连续失败达到阈值后打开、半开态探测成功关闭并置恢复标记、半开态探测失败重新打开、探测超时后重置允许新探测；使用固定时钟或 Mock 时钟，不依赖时间戳精度；`mvn test` 通过 |

## 第 5 批 · 发号器测试

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T7 | 发号器单元测试 | `test/number/RedisMySqlClaimApplicationNoGeneratorTest` | 覆盖 5 个场景：正常发号返回 `A` + `yyyyMMdd` + 8 位序列、熔断打开时降级发号且本节点串行化、恢复后首次发号抬升计数器到 MySQL 最大尾号、序号超过 99,999,999 抛 `IllegalStateException`、降级路径连续分配保证递增；Mock Redis 与 MySQL；`mvn test` 通过 |

## 第 6 批 · Local 桩去重

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T8 | Local 桩公共基类 | `infrastructure/integration/local/AbstractLocalIntegration` | 封装 `pause()` 逻辑：日志 + 休眠 + 中断恢复；接收 `InteractionType` 与 `LocalIntegrationProperties` 作为构造参数；提供 `pause(String claimApplicationNo)` 方法；`delay` 为 `Duration.ZERO` 时直接返回；休眠被中断时恢复中断标记并抛 `IllegalStateException` |
| T9 | 5 个子类继承基类 | `LocalAuditSystemClient`、`LocalValidPolicyQuery`、`LocalUnderwritingResultQuery`、`LocalAutoUnderwritingRequester`、`LocalMaterialFileProcessor` | 继承 `AbstractLocalIntegration`；删除各自的 `pause()` 方法；调用基类的 `pause(claimApplicationNo)`；不需要申请编号的端口传占位符 `-`；`mvn -B clean package` 通过 |

## 第 7 批 · 构建与运行栈验证

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T10 | 全反应堆构建 | `mvn -B clean package` | 9 个模块 SUCCESS；无未使用导入与临时代码；新增测试全部通过 |
| T11 | 签收黄金路径 | 运行栈验证记录 | 首次签收创建成功；同一外部申请号重复提交返回 `REUSED`；第二次起日志显示快照命中且无 `findByTenantAndExternalClaimApplicationNo` 查询；行为与 011 之前一致 |
| T12 | 审核交付黄金路径 | 运行栈验证记录 | 审核交付事件到达后调审核系统并写轨迹；重复事件跳过处理；找不到申请时 log.warn 并交 binder 重投；行为与 011 之前一致 |
| T13 | Redis 停机演练 | 运行栈验证记录 | 停机后连续 10 个申请全部成功且编号唯一；降级日志相邻两条间隔不小于 10 秒；恢复后继续提交编号不回退；行为与 010 一致 |

## 说明

- 本 Spec 补核心路径单元测试，验证以运行栈黄金路径与故障演练为准。
- 任务顺序即执行顺序，前置任务未完成不进入下一批。
- `AbstractTenantProtocolPipeline` 的 `verifySignature`/`decrypt`/`validateTenantRules` 已文档化"示例协议不包含签名，真实租户在子类覆写"，属于模板方法模式的标准用法，本 Spec 不修改。
- T4 的异常类型如果项目没有统一业务异常，则使用 `ClaimApplicationIntakeException`；如果 `ClaimApplicationIntakeException` 不适用于审核交付场景，则新建 `AuditDeliveryException` 或使用 `IllegalStateException` 但改进异常信息。
