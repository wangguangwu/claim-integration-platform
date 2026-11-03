# 029 接口层瘦身（MVP）

## 1. 背景与目标

`claim-application-service` 的 `interfaces` 层已积累与关键链路无关的间接层：承保回调 HTTP 入口没有真实调用方，错误码声明接口与错误码状态注册表只为全局异常处理器服务，响应渲染器只是响应组装的包装，租户协议流水线为单租户保留了接口、抽象模板与注册中心三层结构，DTO 也未区分请求与响应。

目标：

- `interfaces` 只保留 MVP 关键链路：租户 A 签收、问题件下发/关闭/查询、申请详情查询、异步消息消费、定时调度与统一异常渲染。
- 删除无调用方或无扩展价值的类，减少控制器、流水线与 DTO 之间的间接层。
- 不改变任何业务规则、状态机、消息契约、数据库结构与既有成功响应结构。

## 2. 业务规则

1. 承保完成回调不再提供 HTTP 入口；承保完成用例与补偿链路保持不变，仍可由补偿调度推进滞留的 `UNDERWRITING` 申请。
2. 租户协议保持「验签 → 解密 → 解析 → 校验 → 转换」五阶段顺序；验签与解密使用默认实现，执行时记录一条 INFO 日志并原样放行，日志不得输出请求正文、签名、密钥或证件号。
3. 每个租户注册一条协议流水线，签收入口对所有租户共用；当前只有 `tenant-a` 已接入，其流水线使用默认实现。新增租户只需注册新的流水线，不修改签收入口。
4. 租户协议处理时记录一条 INFO 日志，包含当前 `tenantCode` 与 `protocolVersion`，不输出报文与敏感字段。
5. 未接入的租户或协议版本、转换结果租户与上下文不一致均返回 HTTP 400 失败响应，不进入签收用例。
6. HTTP 请求体校验失败、报文不可解析、业务拒绝统一返回 HTTP 400 失败响应；未预期异常统一返回 HTTP 500 失败响应。
7. 响应外壳保持 `code`、`message`、`traceId`、`data` 四个字段；`code` 只有 `SUCCESS` 与 `FAILURE` 两种取值，成功为 HTTP 200，失败为对应错误状态，失败时 `message` 说明原因且不得包含敏感字段。
8. 签收、问题件、查询三个入口的请求字段与成功响应结构不变。
9. 问题件查询、申请详情查询在缺少租户上下文时返回 HTTP 400 失败响应。
10. MQ Topic、消费组、事件类型、消息结构、消费去重与重投语义不变。
11. 数据库表、字段、唯一约束与读写语义不变。
12. DTO 按请求与响应分目录存放，仅调整包位置，不改变字段。
13. `interfaces` 只承担驱动适配职责：只放 HTTP、MQ、定时任务等入口与协议适配，不得出现业务编排、事务、持久化或跨域聚合；一级目录按入口类型划分为 `web`、`consumer`、`scheduler`。
14. `web` 下按角色分包为 `controller`、`advice`、`dto`、`tenantprotocol`；`dto` 只服务 `web`，随 `web` 存放，不设 `interfaces/common` 公共契约包。
15. `tenantprotocol` 保留五阶段与租户实现；当前 `tenant_a` 平铺解析器、转换器与注册配置三个类，不预先拆 `parser`、`converter`、`assembler` 子包；新增租户在 `tenantprotocol/tenants/<tenant>` 下注册。
16. `consumer` 下消息解析与装配类统一放入 `message` 子包，消费者类保留在 `consumer` 根下。

## 3. 正常场景

- 租户 A 提交签收报文：流水线记录带 `tenantCode` 与 `protocolVersion` 的 INFO 日志，验签与解密各记录一条 INFO 日志，解析、校验、转换后进入签收用例，返回原成功响应结构。
- 审核系统下发或关闭问题件：请求经注解校验后进入用例，返回原问题件响应结构。
- 按案件号查询问题件、按申请编号查询申请详情：响应结构与状态码不变。
- 补偿调度推进滞留的 `UNDERWRITING` 申请：走原承保完成用例，行为不变。
- 各入口发生字段校验失败或未预期异常：分别返回 400 `FAILURE`、500 `FAILURE`，响应外壳不变。

## 4. 异常场景

- 未接入租户提交签收报文：返回 400 `FAILURE`，不进入签收用例。
- 租户转换结果与上下文租户不一致：返回 400 `FAILURE`，不进入签收用例。
- 业务异常：返回 400 `FAILURE`，`message` 为业务拒绝说明。
- 验签或解密默认实现执行失败：视为系统异常，返回 500 `FAILURE`。

## 5. 边界条件

- 缺少租户编码或协议版本的签收请求按未接入租户处理，返回 400 `FAILURE`。
- 租户流水线注册表存在多条匹配流水线时返回 400 `FAILURE`。
- 问题件查询与申请详情查询缺少租户上下文时返回 400 `FAILURE`。
- 承保回调入口删除后，不再接受 `POST /api/claim/v1/callbacks/underwriting-completed`。
- 失败响应不再区分具体错误码，只保留 HTTP 状态与可读说明。

## 6. 本次范围

- 删除承保回调入口及其请求 DTO。
- 删除错误码声明接口、业务错误码枚举与状态映射，唯一异常处理器只区分成功与失败，并调整其包位置。
- 删除响应渲染器，控制器直接返回统一响应外壳。
- 将租户协议流水线收敛为“每租户一条、当前统一使用默认实现”：保留流水线接口与注册中心，删除抽象模板与租户子类，新增默认实现与租户 A 注册配置，保留五个阶段与验签/解密日志。
- DTO 拆分请求与响应子包，并随 `web` 归入 `interfaces/web/dto`。
- 收口 `interfaces` 目录结构：`web/controller`、`web/tenantprotocol/pipeline`、`consumer/message`，去掉 `tenantprotocol/core` 与 `interfaces/dto` 根目录。
- 同步 ArchUnit 规则与必要文档。

## 7. 非本次范围

- `application`、`domain`、`infrastructure` 层的类结构调整。
- MQ 消费者合并；只移动消息解析与装配类，不改装配逻辑。
- 新增 `interfaces/common` 公共契约包，或把 DTO 提升为跨入口共享契约。
- 预拆 `tenantprotocol` 的 `parser`、`converter`、`assembler` 子包。
- 承保完成用例、补偿处理器与调度逻辑调整。
- 新增接口、失败细分码、消息字段或数据库变更。
- 新增业务单元测试（功能未定稿，仅保留 ArchUnit 分层规则）。

## 8. 验收标准

1. 代码中不存在承保回调控制器、承保回调请求 DTO、错误码声明接口、错误码状态注册表、业务错误码枚举与响应渲染器。
2. `POST /api/claim/v1/callbacks/underwriting-completed` 不再返回业务响应（404）。
3. 签收请求日志出现一条带 `tenantCode` 与 `protocolVersion` 的流水线 INFO 记录，以及验签与解密各一条 INFO 记录，日志不含请求正文与敏感字段。
4. 未接入租户、转换结果租户不一致两种异常均返回 HTTP 400 与 `FAILURE`。
5. 字段校验失败与业务拒绝返回 400 `FAILURE`；未预期异常返回 500 `FAILURE`；成功响应返回 200 `SUCCESS`。
6. 签收、问题件、查询的成功响应 JSON 与改造前一致。
7. `interfaces/web/dto` 下只有 `request`、`response` 两个子包及包说明，请求类与响应类归位正确；`interfaces` 根目录不存在 `dto` 包。
8. `interfaces` 目录符合目标结构：`web/{controller,advice,dto,tenantprotocol}`、`consumer/message`、`scheduler`；不存在 `tenantprotocol/core`、`tenantprotocol/tenants/tenant_a/parser|converter|assembler` 与 `interfaces/common`。
9. 所有 `@RestController` 均位于 `interfaces.web.controller`，所有 `@RestControllerAdvice` 均位于 `interfaces.web.advice`。
10. `mvn -pl backend/claim-application-service -am test` 通过，ArchUnit 规则全部通过。
11. 运行栈黄金路径通过：签收 → 自动承保完成 → 问题件下发 → 问题件关闭 → 问题件查询 → 申请详情查询。
