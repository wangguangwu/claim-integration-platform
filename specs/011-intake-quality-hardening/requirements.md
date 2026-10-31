# 011 签收链路质量加固：核心路径测试、Local 桩去重与审核交付异常规范化

## 项目背景与目标

009（签收后异步链路）与 010（签收扩容保护）已定稿并合入 main，签收链路整体跑通。定稿后回看代码有三处可加固：

1. 全项目零测试文件。009/010 已定稿，按规范可以补测试；核心路径（签收重试/幂等、审核交付判重、熔断三态转换、发号器降级恢复）缺少可重复执行的自动化验证，后续改动只能靠运行栈回归。
2. 5 个 `Local*` 端口实现的 `pause()` 方法逻辑完全重复（日志 + 休眠 + 中断恢复），每份各自维护，新增本地实现时容易漏掉中断标记恢复。
3. `AuditDeliveryUseCase` 找不到申请时抛 `IllegalStateException`，但 outbox 事件可能早于申请落库到达，属于可预期的时序场景，应使用业务异常并交给消费者按失败策略处理。

本 Spec 的目标：

- 补核心路径单元测试，建立可重复执行的自动化安全网。
- 提取 Local 桩公共基类，消除 5 份重复的 `pause()` 实现。
- 审核交付找不到申请时改抛业务异常，异常信息携带业务上下文，不暴露内部实现。

## 业务规则

### 核心路径测试

1. 测试覆盖四类核心路径：签收用例的重试与幂等、审核交付用例的事件判重、Redis 熔断的三态转换、发号器的降级与恢复。
2. 测试只验证业务行为，不验证框架装配；Mock 掉 Redis、MySQL、MQ 等外部依赖，不启动 Spring 容器。
3. 每个测试方法只验证一个场景，命名表达「前置条件 + 动作 + 期望结论」。
4. 测试不依赖外部系统、不依赖网络、不依赖时间戳精度，可重复执行。
5. 测试不修改生产代码的业务规则，不为让测试通过而放宽校验。

### Local 桩去重

6. 公共基类只提取 `pause()` 逻辑（日志 + 休眠 + 中断恢复），不提取业务方法（`deliver`、`hasValidPolicy` 等）。
7. 基类接收 `InteractionType` 与 `LocalIntegrationProperties` 作为构造参数，子类只传入这两个参数并实现自己的业务方法。
8. 基类的 `pause()` 方法支持可选的 `claimApplicationNo` 参数；不需要申请编号的端口（如 `LocalValidPolicyQuery`）传占位符 `-`，与现有日志口径一致。
9. 基类命名为 `AbstractLocalIntegration`，放在 `infrastructure/integration/local` 包下，与 `LocalIntegrationProperties` 同层。
10. 5 个子类删除各自的 `pause()` 方法，改为调用基类的 `pause()`。

### 审核交付异常规范化

11. `AuditDeliveryUseCase` 找不到申请时改抛 `ClaimApplicationIntakeException`（或项目统一业务异常），异常信息包含 `claimApplicationNo` 与 `eventId`，不暴露堆栈与内部实现。
12. 消费者捕获业务异常后按现有失败策略处理（log 后原样抛出交 binder 重投），不改变重投语义。
13. 异常日志级别为 `warn`，不升级为 `error`；此场景是可预期的时序问题，不是系统故障。

## 正常场景

1. 签收用例首次创建：缓存未命中 → 回源确认新申请 → 事务内创建 → 标记存在 → 返回 `CREATED`。
2. 签收用例重复且缓存命中：直接返回 `REUSED`，不读聚合、不调用外部系统。
3. 签收用例唯一键冲突：按重复请求解析 → 解析成功返回 `REUSED` → 解析失败重新判定并重试，最多 5 次。
4. 审核交付事件首次到达：判重未消费 → 找到申请 → 状态匹配 → 调审核系统 → 事务内写轨迹与消费登记。
5. 审核交付事件重复到达：判重已消费 → 跳过处理，不写轨迹。
6. Redis 熔断正常态：`tryAcquire()` 返回 true → 走 Redis 路径 → `recordSuccess()` 保持关闭。
7. Redis 熔断打开态：连续失败达到阈值 → `recordFailure()` 打开 → `tryAcquire()` 返回 false → 走降级路径。
8. Redis 熔断半开态：到期后放行一个探测 → 探测成功关闭并置恢复标记 → 探测失败重新打开。
9. 发号器正常态：Redis `INCR` 返回编号 → 格式化为 `A` + `yyyyMMdd` + 8 位序列。
10. 发号器降级态：熔断打开 → 本节点 `synchronized` 串行分配 → 记住已分配尾号保证递增。
11. 发号器恢复态：熔断关闭 → 首次发号抬升计数器到 MySQL 最大尾号 → 继续 `INCR`。

## 异常场景

1. 签收用例重试耗尽：唯一键冲突重试 5 次仍失败 → 抛 `IllegalStateException`，异常信息包含租户编码与外部申请号。
2. 审核交付找不到申请：outbox 事件早于申请落库到达 → 抛业务异常，异常信息包含 `claimApplicationNo` 与 `eventId` → 消费者 log 后原样抛出交 binder 重投。
3. Redis 熔断探测中断：探测线程被 interrupt → 重置探测标记 → 下次请求重新放行探测。
4. 发号器降级中断：`synchronized` 段内线程被 interrupt → 恢复中断标记 → 抛 `IllegalStateException`。
5. 发号器序号超限：当日序列超过 99,999,999 → 抛 `IllegalStateException`，异常信息包含日期。

## 边界条件

1. 测试不依赖 Spring 容器，不使用 `@SpringBootTest`，只使用 JUnit 5 + Mockito。
2. 测试不访问真实 Redis、MySQL、MQ，全部使用 Mock 或内存实现。
3. 测试不依赖时间戳精度，涉及时间的场景使用固定时钟或 Mock 时钟。
4. Local 桩基类的 `pause()` 方法在 `delay` 为 `Duration.ZERO` 时直接返回，不休眠。
5. Local 桩基类的 `pause()` 方法在休眠被中断时恢复中断标记并抛 `IllegalStateException`，不吞中断。
6. 审核交付异常信息不包含证件号、报文原文等敏感信息，只包含业务标识。

## 本次范围

- 补核心路径单元测试：签收用例重试/幂等、审核交付判重、Redis 熔断三态转换、发号器降级恢复。
- 提取 Local 桩公共基类 `AbstractLocalIntegration`，5 个子类删除重复的 `pause()` 方法。
- 审核交付找不到申请时改抛业务异常，异常信息包含业务标识。

## 非本次范围

- 全量测试覆盖：只覆盖核心路径，不覆盖所有分支与边界。
- 集成测试：不启动 Spring 容器，不访问真实外部系统。
- 模板方法空实现：`AbstractTenantProtocolPipeline` 的 `verifySignature`/`decrypt`/`validateTenantRules` 已文档化"示例协议不包含签名，真实租户在子类覆写"，属于模板方法模式的标准用法，不需要修改。
- 进程内缓存过期 entry 清理：`RedisClaimApplicationIntakeSnapshotQuery` 的 `localLookups` 达到条目上限后只拒绝新条目，已有过期 entry 不会主动清理，属于 P2 优化，不在本 Spec 范围内。
- 网关限流配额补齐：`application.yml` 只配了 `tenant-a`，多租户上线前需补齐，不在本 Spec 范围内。

## 验收标准

1. 新增测试文件 4 个：`ClaimApplicationIntakeUseCaseTest`、`AuditDeliveryUseCaseTest`、`RedisAvailabilityCircuitTest`、`RedisMySqlClaimApplicationNoGeneratorTest`。
2. 测试全部通过：`mvn test` 反应堆中 `claim-application-service` 模块的测试全部通过。
3. 测试覆盖核心场景：签收用例 5 个场景（首次创建、重复命中缓存、重复未命中缓存、唯一键冲突重试成功、重试耗尽）、审核交付 4 个场景（首次到达、重复到达、找不到申请、状态不匹配）、Redis 熔断 6 个场景（关闭态放行、打开态降级、半开态探测成功、半开态探测失败、探测超时重置、恢复标记消费一次）、发号器 5 个场景（正常发号、降级发号、恢复后抬升、序号超限、降级串行化）。
4. Local 桩基类提取完成：5 个子类删除各自的 `pause()` 方法，改为调用基类的 `pause()`。
5. 审核交付异常规范化：找不到申请时抛业务异常，异常信息包含 `claimApplicationNo` 与 `eventId`。
6. 项目编译成功：`mvn -B clean package` 全反应堆通过。
7. 运行栈回归：签收黄金路径、审核交付黄金路径、Redis 停机演练均通过，行为与 011 之前一致。

## 与既有 Spec 的关系

- 009 冻结的审核交付消费失败重投语义不变，本 Spec 只改异常类型，不改重投策略。
- 010 冻结的 Redis 熔断三态转换与发号器降级恢复逻辑不变，本 Spec 只补测试，不改实现。
- 006 P1-3 曾以"零引用依赖"删除 sentinel，010 因入口限流重新引入，本 Spec 不回改 006。
