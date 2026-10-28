# 002 Application 本地基础设施、数据基线与集成契约需求

## 项目背景与目标

`claim-application-service` 的本地配置已依赖 MySQL、Redis 和 RocketMQ，但仓库尚未提供可重复启动的依赖服务、Application 域数据库基线，以及供后续业务 Spec 共用的消息与 HTTP 契约。

本 Spec 的目标是固定 Application 域后续开发的运行基线：开发者能够启动本地依赖服务；Application 域的基础表和关键约束具有唯一来源；异步交互的 Topic、幂等标识和 HTTP 路径不再由各业务 Spec 自行约定。

本 Spec 不实现申请签收、材料处理、承保、审核、问题件或撤销等业务用例。

## 业务规则

### 本地依赖服务

- 本地开发环境提供 MySQL、Redis、RocketMQ NameServer、RocketMQ Broker 和 RocketMQ Dashboard；服务启动不依赖 Nacos、XXL-Job 或外部业务系统。
- MySQL 初始化后必须存在 `claim_application_db` 数据库和仅供 Application 服务使用的应用账号；服务运行不使用 MySQL root 账号。
- Redis 仅作为后续缓存、分布式协调和幂等辅助能力的预置依赖，本 Spec 不写入任何 Redis 业务数据。
- 执行 `scripts/docker-start.sh` 后，RocketMQ 必须存在本 Spec 定义的 Topic；禁止依赖生产者首次发送时自动创建 Topic。
- 本地编排必须支持 Apple Silicon（`linux/arm64`）；禁止为支持 arm64 的镜像强制指定 `linux/amd64`，避免 Docker Desktop 通过 Rosetta/QEMU 模拟运行。
- 本地服务端口、账号和密码可以通过环境变量覆盖；仓库只提供非生产默认值，不提交生产密钥。

### 数据基线

- Application 域基础表固定为 `t_application`、`t_material`、`t_issue`、`t_case_relation`、`t_interaction_log`、`t_outbox_event` 和 `t_consumed_event`，表名统一使用 `t_` 前缀。
- Application 必须以 `(tenant_code, external_application_no)` 唯一识别同一租户的外部申请；`application_no` 是平台申请编号且全局唯一。
- 同一 Application 可以保留多批材料、多个历史问题件和多个 Case 关联，但同一时刻最多存在一条未关闭 Issue。
- Case 关联必须以 `(tenant_code, application_no, case_no)` 去重。
- Outbox 事件以 `event_id` 唯一；消息消费以 `(consumer_group, event_id)` 去重。
- Material 必须同时保存租户提交的初始文件 URL 与文件上传、转存后的 URL；两者均用于追溯材料流转，不得互相覆盖。
- Issue 必须保存问题件原因和审核人。
- 所有基础表必须具有创建时间和更新时间；涉及逻辑删除的材料记录必须保留删除标记与删除时间，不物理删除审计数据。
- InteractionLog 一条记录只表示一个已发生的交互事件，不在单条记录中拼接发起、响应、处理等多个生命周期时间点。
- 同一业务行为必须使用同一个交互编号关联多条 InteractionLog；例如影像上传开始与影像上传完成分别记录一条日志，二者通过同一交互编号和影像上传类型组成一组行为。
- InteractionLog 必须记录交互编号、交互类型、动作、事件发生时间和处理结果；动作至少支持开始、完成与失败。
- InteractionLog 只记录业务标识、目标系统、处理结果和脱敏错误上下文；不得持久化 Token、密钥、完整证件号、银行卡号或四类人员 JSON。

### MQ 契约

- Topic `claim_application_material_process_v1` 用于触发首次申请或重新申请的材料处理。
- Topic `claim_application_audit_delivery_v1` 用于触发已具备审核条件的 Application 向审核系统交付。
- RocketMQ Topic 名称只能使用字母、数字、`_`、`-`、`%` 和 `|`；版本号使用下划线表达，例如 `_v1`。
- Topic 名称、消费者组名称、事件类型和消息信封属于版本化契约。破坏性变更必须新建版本，不得直接改变 `v1` 消息语义。
- 所有消息必须含有 `eventId`、`eventType`、`eventVersion`、`applicationNo`、`tenantCode`、`occurredAt`、`traceId` 和 `payload`。
- 业务状态、InteractionLog 与待投递 OutboxEvent 必须在同一本地事务中持久化；MQ 投递由后续可靠投递能力完成。
- 本 Spec 只定义 Topic 和消息契约，不实现 Outbox 投递器、MQ 消费者、重试任务和死信处理。

### HTTP 契约

- 租户申请提交、补材和撤销接口，以及承保完成、审核签收、审核问题件回调，统一使用 `/api/application/v1` 前缀并经 `claim-gateway` 路由。
- 所有请求必须携带 `X-Tenant-Code` 与 `X-Protocol-Version`；`X-Trace-Id` 可选，缺失时由网关生成并透传。
- 所有异步回调必须额外携带 `X-Request-Id`；同一来源系统对同一业务通知重试时，`X-Request-Id` 不得变化。
- Application 服务在后续业务 Spec 中使用 `X-Request-Id` 和业务唯一键完成回调幂等；本 Spec 不实现接口控制器或验签逻辑。
- HTTP 方法、路径、成功响应的公共字段和回调关联字段在本 Spec 固定；人员字段、材料字段、验签算法与租户私有字段由 `003` 的租户协议适配 Spec 定义。

## 正常场景

1. 开发者按文档启动本地编排后，MySQL、Redis、RocketMQ NameServer 和 Broker 均处于可用状态。
2. MySQL 首次初始化后，`claim_application_db` 中存在七张基础表、应用账号和全部唯一约束、普通索引。
3. RocketMQ 初始化后，两个 `v1` Topic 均可查询，后续生产者和消费者使用固定 Topic 名称与消费者组。
4. 开发者可以通过 RocketMQ Dashboard 查看 NameServer、Broker 和 Topic 的运行状态。
5. 后续业务 Spec 可以直接引用本 Spec 的 DDL、消息信封和 HTTP 路径，无需修改既有基础约束。

## 异常场景

| 场景 | 期望处理 |
|---|---|
| MySQL、Redis 或 RocketMQ 未就绪 | 对应容器健康检查失败；依赖它的初始化步骤不应报告成功 |
| MySQL 数据卷已存在 | 初始化脚本不得覆盖既有业务数据；新增版本迁移须显式执行 |
| Topic 初始化失败 | 启动脚本失败并输出 RocketMQ 管理命令错误上下文；不得以自动建 Topic 作为替代 |
| Docker Desktop 显示 AMD64 模拟标识 | 检查并移除 Compose 中不必要的 `platform: linux/amd64`，重新拉取对应 arm64 镜像 |
| 消息缺少契约必填字段 | 后续消费者拒绝处理并记录可定位的事件标识，不推进业务状态 |
| 回调缺少租户头或 `X-Request-Id` | 后续接口返回明确参数错误，不进入业务状态迁移 |
| 重复 `eventId` 或重复消费记录 | 由唯一约束拒绝重复记录，后续消费逻辑按幂等处理 |

## 边界条件

- 本地 Docker 编排仅用于开发和集成测试，不作为生产部署方案。
- 仅建立 Application 服务私有库；不创建 Case、AI 或其他服务的数据库和表。
- 不引入数据库迁移框架、不创建业务 Java 实体、不配置 MQ Binder 函数、不发送或消费真实消息。
- 不提供 Nacos、XXL-Job、文件系统、承保系统和审核系统容器；它们的可联调 Stub 由依赖这些能力的后续业务 Spec 决定。
- 不定义人员 JSON 的字段明细、材料业务分类、有效保单口径、审核报文内容或撤销规则。

## 本次范围

- `002` 的 requirements、design、tasks 三份 Spec 文档。
- 本地 Docker 服务编排、环境变量样例、RocketMQ Broker 与 Dashboard 配置、启动停止脚本及说明。
- Application 域数据库初始化 SQL。
- 两个 RocketMQ Topic 的初始化、消费者组、消息信封与事件类型契约。
- Application HTTP 路径、请求头和回调关联字段契约文档。
- Application local profile 的 MySQL、Redis、RocketMQ 可覆盖连接配置。
- MySQL、Redis、RocketMQ、RocketMQ Dashboard 与 Application 健康检查文档。
- Docker、数据库结构、MQ Topic、服务启动、测试和打包验证。

## 非本次范围

- 申请受理业务 Java 代码、自动化测试和消息生产消费实现。
- 租户协议解析、验签、解密、字段映射和响应转换。
- 申请签收、重新申请、材料处理、承保、审核、问题件、补材和撤销的业务实现。
- Outbox 投递器、MQ 消费者、死信队列、补偿任务和监控告警实现。
- 生产环境密钥、容器编排、容量规划、备份和高可用设计。

## 验收标准

- `specs/002-application-integration-foundation/` 下存在且仅包含 `requirements.md`、`design.md`、`tasks.md` 三份互相一致的文档。
- 文档明确列出本地依赖服务、端口、数据卷、健康检查和初始化顺序。
- 文档提供可直接执行的 Application 域建库建表 SQL，且包含七张基础表及本需求定义的唯一约束。
- 文档明确列出两个 Topic、各自的生产事件、消费者组、消息信封和版本策略。
- 文档明确列出所有 v1 HTTP 路径、请求头和回调幂等关联字段。
- Docker 编排、SQL、配置和契约文档与本 Spec 一致；本次不创建任何申请受理业务 Java 代码。

## 评审决策项

以下为本 Spec 提议、需在评审中确认的固定契约：

1. 本地基础服务仅包含 MySQL、Redis、RocketMQ，不纳入 Nacos 和 XXL-Job。
2. MQ 仅保留 `claim_application_material_process_v1` 和 `claim_application_audit_delivery_v1` 两个内部命令 Topic；承保完成、审核签收和问题件保持 HTTP 回调。
3. 回调与租户请求均经网关使用 `/api/application/v1` 前缀，统一携带租户请求头；回调以 `X-Request-Id` 作为传输级幂等标识。
4. Application 基础表在本 Spec 一次性确定，后续 Spec 只能新增必要字段或索引，修改既有约束必须先变更本 Spec。
