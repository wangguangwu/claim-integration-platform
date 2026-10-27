# Case 设计

## 1. 包名说明

总架构定义的包名是 `com.wangguangwu.claim.case`。`case` 是 Java 关键字，不能作为包名或任何标识符使用，该结构无法编译。

本设计采用 `com.wangguangwu.claim.cases`，只增加一个字母，其余分层与总架构一致。总架构文档需要同步修订这一处，避免后续实现按错误包名创建目录。

## 2. 分层与依赖

```
com.wangguangwu.claim.cases
├─ interfaces          接入层：REST、DTO、MQ 监听（含外部回调入口）
├─ usecase             用例层：建案、编排、回调、查询
├─ domain              领域层：聚合、阶段模型、状态机、领域服务、Repository 接口、领域事件
└─ infrastructure      基础设施层：持久化、租户交付适配、MQ、Redis
```

依赖方向：

```
interfaces → usecase → domain ← infrastructure
```

约束：

- 阶段 Handler 属于领域层，只描述「本阶段做什么、结果是继续还是等待」，不包含 HTTP 调用细节。
- 外部接口调用、签名、加解密、报文解析全部收敛在 `infrastructure/delivery`。
- Case 不访问 Application 数据库，来源信息只保留 `sourceSignNo`。

## 3. 领域模型

### 3.1 聚合根 ClaimCase

类名使用 `ClaimCase` 而不是 `Case`，避免与 `case` 关键字在阅读和检索时混淆。

| 字段 | 类型 | 说明 |
| ----------------- | ------------------ | ------------------------ |
| caseNo | CaseNo | 案件编号，唯一 |
| sourceSignNo | SourceSignNo | 来源申请编号 |
| tenantCode | String | 租户 |
| protocolVersion | String | 协议版本 |
| currentStage | StageCode | 当前阶段，未进入阶段链时为空 |
| status | CaseStatus | 案件状态 |
| stageChain | List\<StageCode\> | 规则判定产出的阶段链 |
| version | long | 乐观锁版本 |

聚合行为：

- `intake(...)`：建案，产出 `CaseCreated`
- `decideStageChain(chain)`：规则判定结果落库，产出 `CaseStageDecided`
- `startStage(stage)` / `stageSubmitted()` / `waitCallback()` / `stageSucceeded()`
- `resumeAfterCallback(stage)`：回调成功，推进下一阶段
- `fail(stage, reason)` / `complete()` / `terminate(reason)`

所有迁移先过状态机，非法迁移抛领域异常。

### 3.2 实体

| 实体 | 说明 |
| ------------------- | -------------------------------------------- |
| StageRecord | 阶段执行记录：开始、提交、回调、结果、耗时 |
| DeliveryCall | 外部调用记录：请求、响应、返回码、耗时 |
| CallbackRecord | 回调记录：原始报文、解析结果、幂等键 |
| RetryRecord | 重试记录：阶段、次数、下次重试时间、结果 |
| ExceptionRecord | 异常记录：阶段、错误码、处理状态 |

### 3.3 值对象

- `CaseNo`：不可变，创建时校验格式。
- `SourceSignNo`：来源申请编号，只读。
- `StageCode`：阶段编码枚举，`REPORT` / `IMAGE` / `SETTLEMENT`，支持扩展。
- `StageResult`：`CONTINUE` / `WAIT_CALLBACK` / `FAILED` 三态结果。
- `CallbackKey(caseNo, stageCode, callbackNo)`：回调幂等键。

### 3.4 领域事件

`CaseCreated`、`CaseStageDecided`、`CaseStageSucceeded`、`CaseStageFailed`、`CaseWaitingCallback`、`CaseCompleted`、`CaseTerminated`。

### 3.5 Repository

```java
public interface ClaimCaseRepository {

    Optional<ClaimCase> findByCaseNo(CaseNo caseNo);

    void save(ClaimCase claimCase);

    void update(ClaimCase claimCase);

    List<ClaimCase> findWaitCallbackBefore(LocalDateTime deadline, int limit);

    PageResult<ClaimCase> page(CaseQuery query);
}
```

## 4. 阶段编排模型

```java
public interface StageHandler {

    StageCode stage();

    StageResult execute(StageContext context);
}
```

- `StageContext` 携带案件快照、当前阶段、租户协议键和已产出的证据（如影像材料标识）。
- `StageHandlerRegistry` 按 `StageCode` 装配 Handler，规则判定给出的阶段链必须在注册表中全部存在，否则建案失败并报警。
- 阶段链顺序由规则判定产出，Handler 之间不互相调用，编排统一在 `usecase/orchestrate`。
- 同步阶段：`execute` 返回 `CONTINUE`，编排器直接调用下一阶段。
- 异步阶段：`execute` 返回 `WAIT_CALLBACK`，编排器把案件置为等待回调并停止。
- 失败：`execute` 返回 `FAILED`，写异常记录并按重试策略安排下次执行。

阶段与交互模式不是硬编码关系：同一阶段在不同租户下可以是同步或异步，由交付适配器与规则判定共同决定。

## 5. 状态机

| 当前状态 | 事件 | 目标状态 |
| -------------- | ---------------- | -------------- |
| 待判断 | 判定完成 | 待执行 |
| 待判断 | 判定无需推送 | 已终止 |
| 待执行 | 开始阶段 | 阶段执行中 |
| 阶段执行中 | 阶段同步成功 | 待执行（下一阶段） |
| 阶段执行中 | 阶段转异步 | 等待回调 |
| 阶段执行中 | 阶段失败 | 待执行（重试） |
| 等待回调 | 回调成功 | 待执行（下一阶段） |
| 等待回调 | 回调失败 | 阶段执行中（重试） |
| 等待回调 | 回调超时 | 待执行（恢复） |
| 待执行 | 阶段链结束 | 已完成 |
| 阶段执行中 | 人工终止 | 已终止 |
| 待执行 | 重试耗尽 | 已失败 |
| 已失败 | 人工恢复 | 待执行 |

约束：

- 终态（已完成、已终止）不再处理回调与重试。
- 同一 caseNo 的阶段推进必须串行，回调乱序直接丢弃并记录。
- 已失败是准终态，只接受人工恢复事件。

## 6. 关键流程

### 6.1 建案

```
claim-application-delivered
  → CreateCaseUseCase
      1. 消息幂等校验（t_case_message_consume）
      2. 生成 caseNo，保存 sourceSignNo 与租户信息，状态=待判断
      3. 投递 CaseCreated
  → DecideStageUseCase
      1. 按租户、险种、案件属性判定阶段链
      2. 落库阶段链，状态=待执行
      3. 投递 CaseStageDecided
```

### 6.2 同步阶段

```
CaseStageDecided / CaseStageSucceeded
  → ExecuteStageUseCase
      1. 加载聚合，校验状态与当前阶段
      2. StageHandler.execute → CONTINUE / WAIT_CALLBACK / FAILED
      3. CONTINUE：记录 StageRecord，推进下一阶段
      4. WAIT_CALLBACK：记录提交结果，状态=等待回调，写 Redis 等待索引
      5. FAILED：写异常与重试记录
```

外部调用通过 `TenantDeliveryAdapter` 完成，Handler 只拿结果。

### 6.3 异步阶段与回调

```
外部回调 → CallbackController
  → HandleCallbackUseCase
      1. 解析报文，定位 caseNo 与阶段
      2. 校验案件存在、阶段为等待回调、回调不早于提交
      3. 回调幂等：唯一键 (case_no, stage_code, callback_no)
      4. 成功：更新 StageRecord，状态=待执行，移除 Redis 等待索引，继续阶段链
      5. 失败：状态=阶段执行中，按策略重试
```

回调到达时案件已推进到后续阶段：记录一条「过期回调」并返回成功，避免外部系统无限重试。

### 6.4 超时恢复

```
XXL-JOB → RecoverTimeoutCaseJob
  1. 扫描 Redis 等待索引 / 数据库中超时的等待回调案件
  2. 先执行回执反查
  3. 反查到成功回执 → 走正常回调逻辑
  4. 反查无果 → 重新提交该阶段或转人工
```

补偿任务不直接修改案件状态，只调用用例。

## 7. 幂等与并发

| 场景 | 机制 |
| -------------- | -------------------------------------------------------- |
| 建案消息重复 | `t_case_message_consume.message_id` 唯一 |
| 回调重复 | `uk_callback (case_no, stage_code, callback_no)` 唯一 |
| 阶段重复执行 | 执行前校验当前阶段与状态，非预期直接返回成功并记录 |
| 并发推进 | Redis 锁 `claim:case:lock:{caseNo}` + 数据库 `version` 乐观锁 |
| 外部调用重复提交 | 阶段提交带幂等号 `caseNo + stageCode` 交给外部系统 |

## 8. 重试与恢复

| 场景 | 策略 |
| ------------ | ------------------------------------------------------------ |
| 阶段执行失败 | 3 次，间隔 1m / 5m / 15m |
| 回调失败 | 3 次，间隔 1m / 5m / 15m，耗尽转人工 |
| 回调等待超时 | 默认 30 分钟，先反查后重投 |
| 外部接口超时 | 连接 3s、读取 10s，超时按失败处理 |
| 长时间停滞 | 每小时扫描，超过 2 小时未推进的案件告警并转人工 |

重试必须携带阶段信息，禁止重跑已完成阶段。

## 9. 租户交付适配

```java
public interface TenantDeliveryAdapter {

    TenantProtocolKey key();

    DeliveryResult submit(DeliveryRequest request);

    CallbackResult parseCallback(RawCallback rawCallback);
}
```

- 路由：`TenantProtocolKey → TenantDeliveryAdapter`，缺失即拒绝提交。
- 目录约定：`infrastructure/delivery/adapter/{tenantCode}/{protocolVersion}/`，公共逻辑上提为 `AbstractDeliveryAdapter`。
- 返回码映射：外部错误码统一映射为平台错误分类（可重试 / 不可重试 / 需人工），供重试策略使用。
- 加解密与签名在 `infrastructure/delivery/codec`，HTTP 客户端在 `infrastructure/delivery/client`。

## 10. 数据模型

```sql
CREATE TABLE t_claim_case
(
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    case_no          VARCHAR(40) NOT NULL COMMENT '案件编号',
    source_sign_no   VARCHAR(40) NOT NULL COMMENT '来源申请编号',
    tenant_code      VARCHAR(32) NOT NULL COMMENT '租户编码',
    protocol_version VARCHAR(16) NOT NULL COMMENT '协议版本',
    status           VARCHAR(32) NOT NULL COMMENT '案件状态',
    current_stage    VARCHAR(32) NULL COMMENT '当前阶段',
    stage_chain      VARCHAR(128) NOT NULL COMMENT '阶段链，逗号分隔',
    terminate_reason VARCHAR(512) NULL COMMENT '终止或失败原因',
    version          BIGINT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_case_no (case_no),
    KEY idx_source_sign_no (source_sign_no),
    KEY idx_tenant_status (tenant_code, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='理赔案件';

CREATE TABLE t_case_stage_record
(
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    case_no      VARCHAR(40) NOT NULL COMMENT '案件编号',
    stage_code   VARCHAR(32) NOT NULL COMMENT '阶段编码',
    status       VARCHAR(16) NOT NULL COMMENT '阶段状态',
    submit_at    DATETIME(3) NULL COMMENT '提交时间',
    callback_at  DATETIME(3) NULL COMMENT '回调时间',
    cost_ms      BIGINT      NULL COMMENT '耗时毫秒',
    message      VARCHAR(512) NULL COMMENT '阶段说明',
    trace_id     VARCHAR(64) NULL COMMENT '链路标识',
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_case_stage (case_no, stage_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='案件阶段轨迹';

CREATE TABLE t_case_delivery_call
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    case_no       VARCHAR(40) NOT NULL COMMENT '案件编号',
    stage_code    VARCHAR(32) NOT NULL COMMENT '阶段编码',
    target_system VARCHAR(64) NOT NULL COMMENT '目标系统',
    request_body  MEDIUMTEXT  NULL COMMENT '请求报文',
    response_body MEDIUMTEXT  NULL COMMENT '响应报文',
    result_code   VARCHAR(32) NULL COMMENT '返回码',
    error_type    VARCHAR(16) NULL COMMENT '错误分类',
    cost_ms       BIGINT      NULL COMMENT '耗时毫秒',
    trace_id      VARCHAR(64) NULL COMMENT '链路标识',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_case_stage (case_no, stage_code, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='外部调用记录';

CREATE TABLE t_case_callback
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    case_no        VARCHAR(40)  NOT NULL COMMENT '案件编号',
    stage_code     VARCHAR(32)  NOT NULL COMMENT '阶段编码',
    callback_no    VARCHAR(64)  NOT NULL COMMENT '外部回调序号',
    raw_body       MEDIUMTEXT   NULL COMMENT '原始回调报文',
    parse_status   VARCHAR(16)  NOT NULL COMMENT '解析结果',
    result_code    VARCHAR(32)  NULL COMMENT '返回码',
    result_msg     VARCHAR(512) NULL COMMENT '返回信息',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_callback (case_no, stage_code, callback_no),
    KEY idx_case_created (case_no, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='外部回调记录';

CREATE TABLE t_case_retry
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    case_no       VARCHAR(40) NOT NULL COMMENT '案件编号',
    stage_code    VARCHAR(32) NOT NULL COMMENT '阶段编码',
    retry_type    VARCHAR(16) NOT NULL COMMENT '重试类型',
    retry_count   INT         NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_at DATETIME(3) NULL COMMENT '下次重试时间',
    status        VARCHAR(16) NOT NULL COMMENT '重试状态',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_status_retry (status, next_retry_at),
    KEY idx_case_stage (case_no, stage_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='案件重试记录';

CREATE TABLE t_case_exception
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    case_no      VARCHAR(40)  NOT NULL COMMENT '案件编号',
    stage_code   VARCHAR(32)  NOT NULL COMMENT '阶段编码',
    error_code   VARCHAR(64)  NULL COMMENT '错误码',
    error_msg    VARCHAR(1024) NOT NULL COMMENT '错误信息',
    handle_state VARCHAR(16)  NOT NULL COMMENT '处理状态',
    trace_id     VARCHAR(64)  NULL COMMENT '链路标识',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_case_created (case_no, created_at),
    KEY idx_handle_state (handle_state)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='案件异常记录';

CREATE TABLE t_case_message_consume
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    message_id  VARCHAR(64) NOT NULL COMMENT '消息标识',
    topic       VARCHAR(64) NOT NULL COMMENT '主题',
    consumed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消费时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_id (message_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='消息消费幂等';
```

库名：`claim_case_db`。

## 11. 接口与消息

REST：

| 方法 | 路径 | 说明 |
| ---- | ------------------------------ | ------------------------------ |
| POST | `/api/case/callback/{tenantCode}` | 外部阶段回调（经 Gateway） |
| GET | `/api/case/{caseNo}` | 案件详情 |
| GET | `/api/case/page` | 案件列表 |
| POST | `/api/case/{caseNo}/resubmit` | 人工重新提交阶段 |
| POST | `/api/case/{caseNo}/resume` | 人工恢复失败案件 |

RocketMQ：

| Topic | 方向 | 说明 |
| ------------------------------- | ---- | ------------------ |
| `claim-application-delivered` | 消费 | 建案来源 |
| `claim-case-created` | 生产 | 案件创建 |
| `claim-case-stage-changed` | 生产 | 阶段推进，供诊断与前端订阅 |

## 12. 设计取舍

- **阶段链由规则产出而非固定枚举顺序**：不同租户的推送阶段不同，写死顺序会让租户差异渗进编排逻辑。
- **Handler 只管阶段语义，交付适配器管协议**：同一 Handler 可在不同租户下走同步或异步，避免为协议差异复制阶段逻辑。
- **Redis 只做等待索引与锁**：案件的最终事实在数据库，Redis 丢失后可由数据库超时扫描恢复。
- **回调过期不回退状态**：回退会导致阶段重复执行和外部重复提交，过期回调只记录不处理。
- **来源申请信息不冗余存全量**：只存 sourceSignNo，需要详情时通过服务接口查询，保证单一事实来源。
