# AI 异常诊断实施任务

## 已完成（本次骨架）

- [x] `backend/claim-ai-service/pom.xml`：Web、Validation、Spring AI Client、Nacos、OpenFeign、LoadBalancer、foundation 三件套
- [x] 包结构：`agent`、`tool`、`evidence`、`knowledge`、`policy`、`remediation`

## 阶段一：基础能力

- [ ] 服务启动类与 `application.yml`，本地 profile 关闭 Nacos 注册与配置中心
- [ ] 模型客户端装配：`ChatClient`、超时、重试、模型与提示词版本配置化
- [ ] 诊断会话表与状态流转实现
- [ ] 异步执行器：诊断以任务方式运行，人工触发立即返回 sessionNo

## 阶段二：Tool 层

- [ ] `DiagnosisTool` 接口、`ToolSpec`、白名单注册表
- [ ] `tool` 模块与 Application / Case 服务接口对接（OpenFeign），统一携带 tenantCode
- [ ] 8 个只读 Tool 实现：申请、案件、轨迹、外部调用、回调、重试、租户错误码、知识检索
- [ ] Tool 入参校验、结果裁剪与截断、失败降级为「证据缺失」
- [ ] Tool 调用留痕（入参、出参摘要、耗时、traceId）

## 阶段三：证据与知识

- [ ] `Evidence` 模型与类型枚举
- [ ] 证据标准化、去重、按轮次关联
- [ ] 结论与证据的引用关系校验（无证据不得出根因）
- [ ] 知识库三个集合的接入：协议、错误码手册、历史故障
- [ ] 检索过滤（tenantCode + 错误码精确匹配 + 语义检索）与版本记录

## 阶段四：诊断循环

- [ ] `agent` 调查循环：假设 → 取证 → 判断 → 继续/停止
- [ ] 轮次上限、单轮 Tool 数量上限、退出条件实现
- [ ] 结论结构化输出与解析，解析失败转人工
- [ ] 置信度计算与阈值配置
- [ ] 提示词版本管理与观测数据隔离声明（防提示词注入）
- [ ] 会话回放：按 sessionNo 还原每轮假设、Tool 调用与证据

## 阶段五：策略与处置

- [ ] `policy` 判定表实现：动作类型 × 置信度 × 影响范围 × 禁止列表
- [ ] `remediation` 白名单动作：`RESEND_CASE`、`RESUBMIT_STAGE`、`REFRESH_RECEIPT`
- [ ] 执行前二次授权校验、审批单一致性校验
- [ ] 动作幂等与执行结果回写
- [ ] APPROVAL 待办生成、审批通过与驳回链路

## 阶段六：触发与接口

- [ ] 消费 `claim-application-exception`、`claim-case-exception` 自动触发诊断
- [ ] 重复触发合并策略
- [ ] REST 接口：发起、详情、列表、转人工、提交动作、人工反馈
- [ ] 前端诊断详情数据契约（轮次、证据、结论、动作）

## 阶段七：交付验证

- [ ] 已知根因的构造用例：交付返回码映射错误、回调超时、重试耗尽
- [ ] 证据不足场景验证：必须转人工，不输出根因
- [ ] 越权场景验证：跨租户查询被拒绝、禁止动作无法执行
- [ ] 提示词注入验证：恶意报文不改变授权判定
- [ ] 模型不可用降级验证：输出证据汇总与规则建议
