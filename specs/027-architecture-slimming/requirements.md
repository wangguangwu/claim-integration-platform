# 027 架构减负与边界修正

## 1. 背景与目标

对 `claim-application-service` 做 DDD 自审后，确认整体分层与聚合边界成立，但存在六处实现层面的不合理：

1. 应用层直接捕获 Spring 的 `DuplicateKeyException`，用例编排依赖持久化技术异常。
2. 领域模型在完成审核、关闭问题件、软删除材料时直接读取系统时钟，行为不可注入、不可复现。
3. 聚合的 `bindVersion` 承担乐观锁递增算术，持久化仓储又承担“状态与完成时间一致”的业务不变量校验，职责互相越界。
4. `ClaimApplicationCompensationUseCase` 同时编排五条恢复流程、依赖十一个协作者，单类职责过重。
5. 材料批次号只有一个实现，却保留独立端口与实现类，属于无扩展需求的抽象。
6. `TenantProtocolPipelineFactory` 只做一次委派加一次校验，与注册表职责重复。

目标：

- 应用层只依赖与持久化技术无关的冲突信号。
- 领域时间由应用层传入，领域行为只做状态迁移。
- 聚合自身维护版本回填与终态一致性不变量，仓储只做持久化。
- 补偿链路拆为“门面 + 单一职责处理器”，调度入口与恢复语义不变。
- 删除无扩展价值的批次号端口与流水线工厂。
- ArchUnit 增加两条可执行规则，锁定新的边界。

## 2. 业务规则

1. 不改变任何业务规则、状态机、错误码字符串、HTTP 状态映射与响应结构。
2. 不改变 MQ Topic、消费组、事件类型与消息 JSON 结构。
3. 不改变数据库表、字段、唯一约束与读写语义。
4. 签收链路的并发保护必须保留：外部申请号锁、唯一键冲突重试、冲突后重读分类、编号降级同步均不得删除或弱化。
5. 唯一键冲突仍按原语义处理：签收路径重读后按创建/复用分类，问题件路径按幂等返回。
6. 完成时间、问题件关闭时间、材料软删除时间仍使用 `Asia/Shanghai` 业务时区，取值语义与当前 `LocalDateTime.now(Asia/Shanghai)` 一致。
7. 材料批次号格式保持 `B + yyyyMMdd + 32 位无连字符 UUID`。
8. 租户协议“验签 → 解密 → 解析 → 校验 → 转换”五阶段执行顺序、租户匹配与异常码不变。
9. 补偿五条恢复流程的扫描条件、批量上限、超时策略、失败隔离与交互轨迹字段不变。

## 3. 正常场景

- 签收首次创建遇到唯一键冲突时，用例捕获仓储冲突信号并重读分类，行为与当前一致。
- 问题件并发下发遇到唯一约束时，仍按幂等返回“未新创建”。
- 审核签收完成申请时，完成时间由应用层按业务时区生成并写入聚合。
- 问题件关闭、租户补材关闭问题件、重新申请与补材软删除材料时，时间均由应用层传入。
- 持久化层插入与保存成功后直接回填数据库版本号，不再校验版本递增关系。
- 从数据库还原数据时，若 `COMPLETED` 与 `completedAt` 不匹配，立即拒绝并报出申请编号。
- 补偿调度器继续以五个方法触发恢复，门面只做委派，五条流程行为不变。
- 签收用例一次生成材料批次号，同一批材料与重新申请/补材共用该批次号。
- 租户协议注册表直接完成“定位流水线 → 执行 → 跨租户结果校验”，控制器只依赖注册表。

## 4. 异常场景与边界条件

- 应用层或领域层再次引用 `org.springframework.dao` 时，ArchUnit 失败。
- 领域层再次直接调用 `LocalDateTime.now()` 时，ArchUnit 失败。
- 仓储实现之外的任何类捕获 `DuplicateKeyException` 时，视为边界回退并修正。
- 未识别的唯一约束冲突不得被静默吞掉，必须抛出 `RepositoryConflictException` 并保留原始异常为 cause。
- 版本回填只做赋值与空值校验；版本递增算术只能出现在持久化更新语句中。
- 终态一致性校验只允许出现在 `ClaimApplication.restore`，不得放回仓储。
- 补偿门面不得包含恢复流程分支；处理器不得反向依赖门面。
- 批次号格式变更、租户协议异常码变更或对外契约变更均视为行为变更，禁止合入。

## 5. 本次范围

- 新增 `shared.exception.RepositoryConflictException`，仓储实现翻译唯一键冲突，应用用例改捕该异常。
- 领域时间参数化：`completeAudit`、`closeIssue`、`reapply`、`supplement`、`Material.softDelete`、`ClaimApplicationIssue.close`。
- `bindVersion` 简化为版本回填，终态一致性校验迁入 `restore`，删除仓储中的 `validateCompletionState`。
- 补偿用例拆分为门面与五个处理器，新增 `application/process/compensation` 子包。
- 删除 `MaterialBatchNoGenerator` 端口与 `UuidMaterialBatchNoGenerator` 实现，批次号生成内联到签收用例。
- 删除 `TenantProtocolPipelineFactory`，`execute` 合并进 `TenantProtocolPipelineRegistry`。
- 更新受影响的单元测试、ArchUnit 规则与架构、导航、测试文档。

## 6. 非本次范围

- 不实现 `claim-case-service`、`claim-ai-service` 空模块的业务能力，不删除或调整这两个模块。
- 不实现租户协议验签与解密，不删除现有直通阶段接口与默认实现。
- 不调整签收高并发保护链：分布式锁、快照判定、限流降级、号段与重试策略全部保留。
- 不调整聚合边界、不新增聚合、不新增领域事件或 Outbox 机制。
- 不改包名与层级；除新增 `application/process/compensation` 外不调整目录形态。
- 不新增框架、依赖、配置项或数据库迁移脚本。

## 7. 验收标准

1. `application` 与 `domain` 主源码不存在 `org.springframework.dao` 引用；ArchUnit 新增规则通过。
2. 唯一键冲突只在 `infrastructure/persistence` 内捕获并翻译为 `RepositoryConflictException`；签收与问题件并发冲突测试通过。
3. 领域层不存在 `LocalDateTime.now()` 调用；ArchUnit 新增时钟规则通过；完成、关闭、软删除时间仍为业务时区。
4. `bindVersion` 只做空值校验与赋值；`ClaimApplication.restore` 校验 `COMPLETED` 与 `completedAt` 一致性；仓储不再包含该业务校验。
5. 补偿包为 1 个门面 + 5 个处理器；门面仅委派；`ClaimApplicationCompensationScheduler` 调用签名不变；五条恢复流程行为不变。
6. `MaterialBatchNoGenerator` 与 `UuidMaterialBatchNoGenerator` 不存在；批次号格式断言通过。
7. `TenantProtocolPipelineFactory` 不存在；控制器仅依赖 `TenantProtocolPipelineRegistry`；租户协议异常码与链路行为不变。
8. `mvn -pl backend/claim-application-service -am test` 全部通过，既有测试覆盖不因本次改动净减少。
9. 运行栈黄金路径通过：签收 → 材料处理 → 承保/审核交付 → 问题件与补材 → 审核签收 `COMPLETED`；HTTP、MQ 与数据库契约不变。
