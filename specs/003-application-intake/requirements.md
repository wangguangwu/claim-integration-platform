# 003 理赔申请统一接入与签收需求

## 项目背景与目标

理赔申请由不同租户协议提交，`claim-application-service` 需要在同步请求中完成申请签收、幂等判断和基础数据持久化，并返回平台唯一 `claimApplicationNo`。材料处理、保单确认和审核交付均为后续异步流程，不阻塞本次响应。

本 Spec 的目标是实现统一接入的核心签收能力：租户协议通过协议流水线归一化为平台统一的受理命令，再由签收用例创建 `ClaimApplication` 聚合；首次申请创建 `PENDING_ACCEPT` 理赔申请和首批 Material；重复提交稳定返回已有编号；失败申请可以使用原外部申请号重新申请。

## 业务规则

### 统一接入

- 租户通过 `POST /api/application/v1/claim-applications` 提交申请，必须携带 `X-Tenant-Code`、`X-Protocol-Version`；`X-Trace-Id` 缺失时由网关生成。
- 接入层由 `TenantProtocolPipelineFactory` 按 `tenantCode + protocolVersion` 从 `TenantProtocolPipelineRegistry` 获取流水线。流水线固定按“验签、解密、解析、校验、转换”五步工作：真实租户按协议实现验签、解密，解析器把明文报文反序列化为租户 request，校验器校验必填字段与嵌套结构，转换器把各租户字段归一化为同一份受理命令（平台标准人员与材料字段）；用例据命令创建 `ClaimApplication`，领域及用例层不接收租户私有字段，接入层也不组装聚合。
- 未找到协议流水线、验签失败、解密失败、报文解析失败或字段校验失败时，必须返回明确业务错误，不创建理赔申请、Material 或 OutboxEvent。
- 受理命令与 `ClaimApplication` 至少包含外部申请号、申请人、主被保险人、出险人、领款人和首次材料；四类人员信息独立保存，不得相互覆盖。
- 每个基础人员对象必须包含姓名、性别、证件类型、证件号四个字段；性别统一使用 `MALE`、`FEMALE` 枚举，租户转换器负责把租户报文字段转换为按角色区分的人员值对象——申请人、主被保险人、出险人、领款人各为一个类型（`Applicant`、`PrimaryInsured`、`AccidentPerson`、`Payee`），共同继承人员基类 `PersonInfo`，四类角色不得相互顶替。
- 成功和失败响应默认使用平台统一结构；租户协议要求其它响应结构时，由该租户的响应渲染实现决定响应体、HTTP 状态和响应头，不得在 Controller 中按租户分支。

### 本地联调租户协议

- 本次提供两条只用于本地联调和压测的示例协议流水线，不代表真实合作方协议：`tenant-a/v1`（租户 A）、`tenant-b/v1`（租户 B）。
- `tenant-a/v1` 使用 `requestNo` 作为外部申请号；人员字段为 `fullName`、`sex`、`idType`、`idNo`，人员节点分别为 `applicant`、`insured`、`accident`、`payee`；材料数组为 `attachments[]`，字段为 `type`、`sourceUrl`。
- `tenant-b/v1` 使用 `claimReference` 作为外部申请号；人员节点分别为 `claimant`、`mainInsured`、`injured`、`beneficiary`，字段为 `name`、`genderCode`、`credential.type`、`credential.number`；其中 `1` 转换为 `MALE`，`2` 转换为 `FEMALE`；材料数组为 `evidences[]`，字段为 `documentCode`、`fileDownloadUrl`。
- 两条示例流水线使用模板提供的验签直通与明文直通默认实现，只负责协议解析、字段校验和映射，不实现真实验签、解密或外部文件访问；真实租户接入必须新增独立流水线并覆写验签、解密节点，不得复用联调租户编码上线。

### 签收与幂等

- 理赔申请以 `(tenantCode, externalClaimApplicationNo)` 唯一识别。
- 首次申请生成全局唯一 `claimApplicationNo`，创建状态为 `PENDING_ACCEPT` 的理赔申请和首批 Material，并在同一事务内写入 `CLAIM_APPLICATION_CREATED` OutboxEvent。
- 已存在且状态不是 `ACCEPT_FAILED` 时，重复提交不得覆盖理赔申请、Material 或人员信息，只返回既有 `claimApplicationNo`。
- 已存在且状态为 `ACCEPT_FAILED` 时，允许重新申请：复用既有 `claimApplicationNo`，增加申请尝试次数，覆盖当前理赔申请人员信息，将既有 Material 逻辑删除，创建新的材料批次，并重置状态为 `PENDING_ACCEPT`；同一事务写入 `CLAIM_APPLICATION_REAPPLIED` OutboxEvent。
- 并发首次申请或重新申请只能有一次创建或重置生效；其他并发请求必须返回同一 `claimApplicationNo`，不产生重复数据。

### 材料与状态

- 首次提交的全部 Material 使用同一个新 `batchNo`；每条 Material 必须保存 `materialType`、`originalFileUrl`，`uploadedFileUrl` 在本次签收时为空。
- Material 初始状态为 `PENDING_PROCESS`；材料处理结果不属于本 Spec。
- 本次签收只创建或重置理赔申请到 `PENDING_ACCEPT`，不得推进到 `ACCEPTED`、`UNDERWRITING`、`WAIT_AUDIT`、`ISSUE`、`COMPLETED` 或 `REVOKED`。

### 编号、锁与存在查询

- `claimApplicationNo` 格式固定为 `AyyyyMMdd` 加 8 位日序列，例如 `A2026102700000001`。
- 理赔申请日序列以 Redis 为主计数器。Redis 计数 Key 不存在或 Redis 不可用时，从 MySQL `t_claim_application` 表查询当天已落库的最大 `claimApplicationNo`，尾号加一后生成候选编号；编号允许因事务回滚或进程异常产生空洞，不允许重复。
- Redis 计数器落后于数据库（如 Redis 从旧快照恢复，或故障期间完全由 MySQL 兜底发号）导致唯一键冲突时，必须先把 Redis 计数器抬升到 MySQL 当天最大尾号，再在同一请求内重试，不得把请求当作失败返回。
- `batchNo` 格式固定为 `ByyyyMMdd` 加 UUID 去连字符随机段，例如 `B20261027cfe4a9d8b3e14f5aa22cb812d6c5e901`。
- 同一 `(tenantCode, externalClaimApplicationNo)` 的签收使用 Redisson 可重入锁；获取锁最多等待 30 秒且不传 `leaseTime`，由 watchdog 在持锁 JVM 存活期间自动续期，释放必须位于 `finally`。
- 等待锁超过 30 秒返回 `CLAIM_APPLICATION_INTAKE_BUSY`，不执行签收；Redis 不可用时不阻塞等待，降级为无锁执行，由 MySQL 唯一约束保证幂等。
- 理赔申请是否存在查询使用旁路缓存：先读 Redis，未命中查询 MySQL 并回填；Redis 不可用时直接查询 MySQL 并继续签收。首次创建和重新申请成功后必须失效对应缓存，避免脏读。
- 存在性判断、申请编号生成必须位于数据库事务之外；数据库事务只负责理赔申请、Material、InteractionLog 和 OutboxEvent 的写入。
- 存在性缓存判断与数据库实际数据不一致时，淘汰缓存并在同一请求内重新判断后重试，不得把请求当作失败返回。

### 数据安全与交互记录

- 申请人、主被保险人、出险人和领款人 JSON 必须分别保存，日志不得输出其完整内容、证件号、银行卡号、Token 或密钥。
- 签收受理成功后，必须使用 InteractionLog 行为组记录 `STARTED` 与 `COMPLETED` 或 `REUSED` 事件；`interactionType` 固定为 `CLAIM_APPLICATION_INTAKE`，`claim_application_no` 必填。
- 签收受理前的失败（协议解析、字段校验、存在性准备、编号生成、并发锁）不产生平台申请号，也不写 InteractionLog，只记录应用日志并返回租户错误响应；交互记录从受理成功开始。
- 理赔申请、Material、InteractionLog 和 OutboxEvent 的核心写入必须在既定事务边界内保持一致；失败时不得出现理赔申请已签收但没有对应初始事件的状态。

## 正常场景

1. 首次合法请求经租户协议流水线转换后，系统创建理赔申请、首批 Material 和 `CLAIM_APPLICATION_CREATED` OutboxEvent，返回新 `claimApplicationNo`。
2. 非失败状态的重复请求返回原 `claimApplicationNo`，不新增或覆盖任何业务数据。
3. `ACCEPT_FAILED` 理赔申请重新申请时复用原 `claimApplicationNo`，软删除旧材料，创建新批次 Material，状态重置为 `PENDING_ACCEPT`，写入 `CLAIM_APPLICATION_REAPPLIED` OutboxEvent。

## 异常场景

| 场景 | 期望处理 |
|---|---|
| 租户头缺失 | 由网关拒绝，业务服务不处理 |
| 未找到租户协议流水线 | 返回 `TENANT_PROTOCOL_UNSUPPORTED`，不落库 |
| 验签、解密、解析或字段校验失败 | 返回明确业务错误，不创建业务数据，不写交互轨迹 |
| 外部申请号为空、材料为空或材料字段不合法 | 返回 `CLAIM_APPLICATION_INTAKE_INVALID`，不创建业务数据 |
| 理赔申请唯一约束冲突 | 重新读取既有理赔申请，按其状态执行重复请求或重新申请规则 |
| 存在性缓存与数据库不一致 | 失效缓存后重新判断并重试，不返回错误 |
| 同一申请并发签收等待锁超过 30 秒 | 返回 `CLAIM_APPLICATION_INTAKE_BUSY`，不执行签收 |
| Redis 不可用 | 锁降级为无锁执行，存在查询与编号生成回退 MySQL，签收照常完成，服务可正常启动且就绪探针保持可用 |
| Redis 故障恢复后计数落后 | 唯一键冲突时校准计数器并重试，签收照常完成，无需重启服务 |
| 并发重新申请 | 仅一次重置和新材料批次创建生效，其余请求返回同一 `claimApplicationNo` |
| 数据库或 Outbox 写入异常 | 本地事务回滚，返回统一系统异常，记录应用日志 |

## 边界条件

- 本 Spec 只处理申请签收，不处理文件下载、上传、转存和材料审核。
- 本 Spec 不实现真实租户协议、真实验签或解密；仅提供两条本地联调协议流水线，真实租户由后续 Spec 添加。
- 本 Spec 不实现 Outbox 投递器或 MQ 消费者，只在本地事务中写入待投递事件。
- 本 Spec 不实现保单查询、自动承保、审核交付、审核签收、问题件、补材和撤销。

## 本次范围

- 申请统一接入接口、租户协议流水线路由和平台统一的 `ClaimApplication` 模型。
- 理赔申请、Material 领域模型、状态枚举、DO、Repository 端口与 MapStruct 映射。
- 首次申请、重复请求、重新申请的事务编排和数据库持久化。
- 理赔申请编号、材料批次编号生成端口及本地实现。
- `PersonInfo` 人员基类与 `Applicant`/`PrimaryInsured`/`AccidentPerson`/`Payee` 四个角色值对象、Redisson watchdog 锁、理赔申请存在查询旁路缓存和 Redis/MySQL 编号降级。
- InteractionLog 和 OutboxEvent 的本地事务写入。
- 两条本地联调租户协议流水线与经 `claim-gateway` 对外接口执行的 JMeter 吞吐、幂等并发压测脚本。
- 正常、幂等、并发、回滚和边界场景测试。

## 非本次范围

- 真实租户验签、解密、报文 DTO 和字段映射实现。
- 文件系统调用和 `uploadedFileUrl` 填充。
- Outbox 投递、MQ 消费、材料处理、承保、审核、问题件、补材和撤销。
- CaseRelation 和案件生命周期。
- 前端页面和查询接口。

## 验收标准

- 首次合法申请请求创建唯一理赔申请、首批 Material、InteractionLog 和 `CLAIM_APPLICATION_CREATED` OutboxEvent，并返回 `claimApplicationNo`。
- 同租户、同外部申请号的非失败重复请求不新增理赔申请、Material 或 OutboxEvent，返回既有 `claimApplicationNo`。
- `ACCEPT_FAILED` 重试复用 `claimApplicationNo`、逻辑删除旧材料、创建新批次、写入 `CLAIM_APPLICATION_REAPPLIED` OutboxEvent 并重置为 `PENDING_ACCEPT`。
- 租户协议流水线缺失和所有非法请求均不产生业务数据，返回明确错误。
- 并发提交只生成一条理赔申请；并发重新申请只创建一个新材料批次。
- 同一申请并发签收等待锁超时返回 `CLAIM_APPLICATION_INTAKE_BUSY`；Redis 停机时签收仍可通过 MySQL 降级链路完成，Redis 恢复后计数器自动对齐且无需重启服务。
- 租户定制响应渲染存在时返回租户协议结构，未定制时返回平台统一结构。
- 理赔申请、Material、OutboxEvent 任一写入失败时核心业务数据全部回滚。
- 新增领域、DO、MapStruct 映射、接口和用例测试通过，项目在 JDK 21 下构建通过。
- `tenant-a/v1` 与 `tenant-b/v1` 可分别归一化为同一份受理命令并创建同一个 `ClaimApplication` 模型；JMeter 脚本经 `claim-gateway` 的对外接口执行唯一申请吞吐和同申请号幂等并发测试，并以 HTTP 响应、`X-Trace-Id` 和响应编号一致性验收，不直接操作或查询数据库。

## 评审决策项

1. 首次申请是否必须至少包含一条材料；当前建议为必须，避免空申请进入异步材料处理。
2. 基础人员字段已确定为姓名、性别、证件类型、证件号；证件类型枚举值与人员 JSON 版本仍需在租户协议 Spec 确认。
