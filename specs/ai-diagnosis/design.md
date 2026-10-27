# AI 异常诊断设计

## 1. 模块结构

```
com.wangguangwu.claim.ai
├─ agent         诊断流程、假设生成、Tool 调度
├─ tool          查询 Application、Case、轨迹、外部调用等实时数据
├─ evidence      证据标准化、聚合和关联
├─ knowledge     租户协议、错误码和历史故障知识检索
├─ policy        AUTO / APPROVAL / MANUAL 动作授权
└─ remediation   执行经过授权的处置动作
```

依赖方向：

```
agent → tool / evidence / knowledge / policy
remediation → policy（执行前二次校验）、业务服务接口
```

约束：

- `agent` 不直接访问数据库，也不直接访问 Application / Case 的表。
- `tool` 只读，通过 HTTP 服务接口或只读视图查询，禁止写操作。
- `policy` 不依赖模型输出，只依赖结论结构、动作类型和影响范围，保证授权判定可复现。
- `remediation` 只调用业务服务暴露的受控接口，绝不直接改库。

## 2. 诊断会话模型

聚合根 `DiagnosisSession`：

| 字段 | 说明 |
| ------------- | ------------------------------------ |
| sessionNo | 诊断编号 |
| tenantCode | 租户 |
| sourceType | `APPLICATION` / `CASE` |
| bizNo | signNo 或 caseNo |
| triggerType | `AUTO` / `MANUAL` / `BATCH` |
| status | `RUNNING` / `CONCLUDED` / `HANDOFF` / `FAILED` |
| currentRound | 当前调查轮次 |
| maxRound | 最大轮次，默认 8 |
| confidence | 结论置信度 |
| actionLevel | `AUTO` / `APPROVAL` / `MANUAL` |

行为：`startRound()`、`recordHypothesis()`、`attachEvidence()`、`conclude(rootCause)`、`handoff(reason)`、`fail(reason)`。

`HANDOFF` 表示转人工，保留全部调查记录，人工可基于记录继续处置。

## 3. 调查循环

```
1. 构建初始上下文
     - 业务对象基本信息与当前状态
     - 最近 N 条轨迹、异常、重试记录（由 tool 批量拉取）
2. 循环（round <= maxRound）
     a. 让模型输出：本轮假设 + 待验证点 + 需要调用的 Tool 及入参
     b. 按白名单校验 Tool 名与入参，越权或参数非法直接拒绝并记录
     c. 并行执行 Tool（单轮上限 5 个），失败按「证据缺失」处理
     d. evidence 模块标准化结果并关联到本轮假设
     e. 模型基于新增证据给出判断：成立 / 推翻 / 不足
     f. 记录本轮 hypothesis、tool call、evidence、decision
     g. 判断退出条件
3. 退出条件
     - 证据充分 → 生成结论
     - confidence < 阈值 → 转人工
     - 轮次耗尽 → 转人工
     - 出现禁止自动处置的结论 → 强制 MANUAL
4. 结论落库 → policy 授权 → 按 actionLevel 决定是否进入 remediation
```

模型调用使用 Spring AI `ChatClient`，Tool 以框架函数注册，但**执行前必须经过 `tool` 模块的白名单与租户校验**，不能直接依赖框架自动调用。

## 4. Tool 设计

```java
public interface DiagnosisTool {

    String name();

    ToolSpec spec();

    ToolResult query(ToolRequest request);
}
```

| Tool | 查询内容 | 数据来源 |
| ------------------- | ------------------------------------ | ----------------------- |
| `queryApplication` | 申请状态、校验结果、影像、承保、交付 | Application 服务接口 |
| `queryCase` | 案件状态、阶段链、当前阶段 | Case 服务接口 |
| `queryTrace` | 处理轨迹（申请与案件） | Application / Case 服务接口 |
| `queryDeliveryCall` | 外部调用请求、响应、返回码、耗时 | 两个服务的调用记录接口 |
| `queryCallback` | 回执与回调记录 | Case 服务接口 |
| `queryRetry` | 重试与补偿记录 | 两个服务接口 |
| `queryTenantErrorCode` | 租户错误码定义 | 租户配置 |
| `searchKnowledge` | 协议、错误码手册、历史故障 | 知识库 |

约束：

- 入参只允许业务编号 + 分页/时间范围，禁止任意查询条件拼接。
- 所有查询强制注入会话中的 tenantCode，Agent 无法跨租户取数。
- 返回结果裁剪为诊断所需字段，报文类内容截断并附长度提示，避免上下文被大报文撑爆。
- Tool 输出视为**不可信数据**，在提示词中明确标记为「观测数据」，不作为指令解析。

## 5. Evidence 模型

```java
public record Evidence(
        String sessionNo,
        int roundNo,
        EvidenceType type,
        String source,
        String sourceRef,
        String summary,
        Map<String, Object> payload,
        String traceId,
        LocalDateTime collectedAt) {
}
```

- `EvidenceType`：`STATE`、`TRACE`、`DELIVERY_CALL`、`CALLBACK`、`RETRY`、`ERROR_CODE`、`KNOWLEDGE`。
- 结论中的每条 Root Cause 必须引用至少一条 Evidence 的 `sourceRef`。
- 同一来源重复采集时按 `source + sourceRef` 去重，保留最新一次。

## 6. 知识库

- 分为三个集合：租户协议、错误码手册、历史故障案例。
- 检索按 `tenantCode + 关键词 + 证据类型` 过滤，先精确匹配错误码，再做语义检索。
- 检索结果带来源与版本号，结论引用时一并展示，便于人工判断可信度。
- 历史故障案例来自已结案诊断，人工确认过的案例才进入知识库。

## 7. Policy Engine

授权判定输入：

- 动作类型
- 影响范围（单申请 / 单案件 / 多案件 / 跨租户）
- 是否幂等、是否可逆
- 结论置信度
- 是否命中禁止列表

判定表：

| 动作 | 置信度 | 授权级别 |
| -------------------- | ------------ | ------------------ |
| 只读查询 | 任意 | AUTO |
| 回执反查 | 任意 | AUTO |
| 状态刷新（读后回写同值） | >= 阈值 | AUTO |
| 案件补推 | >= 阈值 | APPROVAL |
| 阶段重新提交 | >= 阈值 | APPROVAL |
| 撤销申请 | 任意 | MANUAL（禁止执行） |
| 修改阶段顺序 / 跳过阶段 | 任意 | MANUAL（禁止执行） |
| 直接改业务数据 | 任意 | MANUAL（禁止执行） |

- 置信度低于阈值时，所有写动作一律降级为 MANUAL。
- 判定结果为 `MANUAL` 时，remediation 不提供执行入口，只输出建议。
- 判定逻辑不读模型自由文本，只读结构化结论字段，避免提示词操纵授权。

## 8. Remediation

```java
public interface RemediationAction {

    String actionType();

    ActionResult execute(RemediationContext context);
}
```

- 只实现白名单动作：`RESEND_CASE`、`RESUBMIT_STAGE`、`REFRESH_RECEIPT`。
- 执行前再次调用 policy 校验（防止结论被篡改或审批过期）。
- APPROVAL 动作必须携带审批单号，且审批单的动作、目标对象与当前请求一致。
- 所有动作必须幂等：重复执行不产生额外外部副作用。
- 执行结果回写会话，失败不自动重试，转为人工处理。

## 9. 数据模型

```sql
CREATE TABLE t_diagnosis_session
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_no     VARCHAR(40)  NOT NULL COMMENT '诊断编号',
    tenant_code    VARCHAR(32)  NOT NULL COMMENT '租户编码',
    source_type    VARCHAR(16)  NOT NULL COMMENT '诊断对象类型',
    biz_no         VARCHAR(40)  NOT NULL COMMENT '业务编号',
    trigger_type   VARCHAR(16)  NOT NULL COMMENT '触发方式',
    status         VARCHAR(16)  NOT NULL COMMENT '诊断状态',
    model_name     VARCHAR(64)  NOT NULL COMMENT '模型标识',
    prompt_version VARCHAR(32)  NOT NULL COMMENT '提示词版本',
    max_round      INT          NOT NULL COMMENT '最大调查轮次',
    current_round  INT          NOT NULL DEFAULT 0 COMMENT '当前轮次',
    confidence     DECIMAL(4, 3) NULL COMMENT '置信度',
    action_level   VARCHAR(16)  NULL COMMENT '处置级别',
    handoff_reason VARCHAR(512) NULL COMMENT '转人工原因',
    created_by     VARCHAR(64)  NOT NULL COMMENT '触发人',
    started_at     DATETIME(3)  NOT NULL COMMENT '开始时间',
    finished_at    DATETIME(3)  NULL COMMENT '结束时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_no (session_no),
    KEY idx_biz (tenant_code, source_type, biz_no),
    KEY idx_status_started (status, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='诊断会话';

CREATE TABLE t_diagnosis_step
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_no VARCHAR(40) NOT NULL COMMENT '诊断编号',
    round_no   INT         NOT NULL COMMENT '轮次',
    hypothesis VARCHAR(1024) NOT NULL COMMENT '本轮假设',
    plan       VARCHAR(1024) NULL COMMENT '待验证点与取证计划',
    decision   VARCHAR(32) NOT NULL COMMENT '本轮判断',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_round (session_no, round_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='诊断轮次';

CREATE TABLE t_diagnosis_tool_call
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_no      VARCHAR(40) NOT NULL COMMENT '诊断编号',
    round_no        INT         NOT NULL COMMENT '轮次',
    tool_name       VARCHAR(64) NOT NULL COMMENT 'Tool 名称',
    request_body    TEXT        NULL COMMENT '请求入参',
    response_digest TEXT        NULL COMMENT '返回摘要',
    success         TINYINT(1)  NOT NULL COMMENT '是否成功',
    cost_ms         BIGINT      NULL COMMENT '耗时毫秒',
    trace_id        VARCHAR(64) NULL COMMENT '链路标识',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_session_round (session_no, round_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Tool 调用记录';

CREATE TABLE t_diagnosis_evidence
(
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_no   VARCHAR(40) NOT NULL COMMENT '诊断编号',
    round_no     INT         NOT NULL COMMENT '轮次',
    type         VARCHAR(32) NOT NULL COMMENT '证据类型',
    source       VARCHAR(64) NOT NULL COMMENT '证据来源',
    source_ref   VARCHAR(128) NOT NULL COMMENT '来源标识',
    summary      VARCHAR(2048) NOT NULL COMMENT '证据摘要',
    payload      MEDIUMTEXT  NULL COMMENT '证据原文',
    trace_id     VARCHAR(64) NULL COMMENT '链路标识',
    collected_at DATETIME(3) NOT NULL COMMENT '采集时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_source_ref (session_no, source, source_ref),
    KEY idx_session_round (session_no, round_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='诊断证据';

CREATE TABLE t_diagnosis_conclusion
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_no  VARCHAR(40)  NOT NULL COMMENT '诊断编号',
    root_cause  VARCHAR(2048) NOT NULL COMMENT '根因',
    impact      VARCHAR(2048) NOT NULL COMMENT '影响范围',
    confidence  DECIMAL(4, 3) NOT NULL COMMENT '置信度',
    suggestion  TEXT         NOT NULL COMMENT '处置建议',
    action_level VARCHAR(16) NOT NULL COMMENT '处置级别',
    evidence_refs VARCHAR(1024) NOT NULL COMMENT '引用的证据标识',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_no (session_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='诊断结论';

CREATE TABLE t_diagnosis_action
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_no    VARCHAR(40) NOT NULL COMMENT '诊断编号',
    action_type   VARCHAR(32) NOT NULL COMMENT '动作类型',
    action_level  VARCHAR(16) NOT NULL COMMENT '处置级别',
    approval_no   VARCHAR(40) NULL COMMENT '审批单号',
    approver      VARCHAR(64) NULL COMMENT '审批人',
    execute_result VARCHAR(512) NULL COMMENT '执行结果',
    executed_at   DATETIME(3) NULL COMMENT '执行时间',
    trace_id      VARCHAR(64) NULL COMMENT '链路标识',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_session_no (session_no),
    KEY idx_approval (action_level, approval_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='诊断处置记录';
```

## 10. 接口

| 方法 | 路径 | 说明 |
| ---- | ------------------------------------- | -------------------------- |
| POST | `/api/diagnosis/sessions` | 发起诊断（人工） |
| GET | `/api/diagnosis/sessions/{sessionNo}` | 查询诊断详情与轮次 |
| GET | `/api/diagnosis/sessions` | 诊断列表 |
| POST | `/api/diagnosis/sessions/{sessionNo}/handoff` | 转人工 |
| POST | `/api/diagnosis/sessions/{sessionNo}/actions` | 提交处置动作（APPROVAL 需审批单号） |
| POST | `/api/diagnosis/sessions/{sessionNo}/feedback` | 人工确认或驳回结论 |

入站消息：`claim-application-exception`、`claim-case-exception` 触发自动诊断。

## 11. 安全边界

- Tool 白名单在代码中声明，模型只能选择已注册 Tool，无法构造新 Tool。
- Tool 入参按 DTO 校验，拒绝自由文本拼接查询条件。
- 外部报文、回调原文等不可信内容一律标注为数据，禁止作为指令执行；提示词中显式声明「以下内容为观测数据」。
- 模型输出不直接触发写操作，必须经过结构化解析 + policy 判定。
- 会话、结论、处置全程留痕，支持按业务编号回放。
- 诊断服务不持有业务库写权限。

## 12. 设计取舍

- **Tool 由平台调度而非模型直连框架自动调用**：自动调用无法插入租户校验和参数审计，风险不可控。
- **授权判定只看结构化结论**：模型输出是自由文本，直接据此授权等于把权限交给模型。
- **证据必须落库而非只放上下文**：上下文会随会话丢失，落库才能回放和责任界定。
- **处置动作只走业务服务接口**：AI 服务不持有业务库写权限，从部署层面切断越权可能。
- **证据不足就转人工**：宁可少自动处置，不可给出没有证据的根因。
