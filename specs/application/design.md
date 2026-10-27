# Application 设计

## 1. 分层与依赖

```
com.wangguangwu.claim.application
├─ interfaces          接入层：REST、DTO、MQ 监听
├─ usecase             用例层：事务边界、跨领域协作
├─ domain              领域层：聚合、值对象、领域服务、状态机、Repository 接口、领域事件
└─ infrastructure      基础设施层：持久化、租户适配、文件、MQ
```

依赖方向：

```
interfaces → usecase → domain ← infrastructure
```

约束：

- domain 不依赖 Spring Web、MyBatis-Plus、Redis 等技术组件，只依赖 claim-foundation-common 中与框架无关的模型。
- usecase 通过 Repository 接口和领域事件对外声明需求，具体实现由 infrastructure 提供。
- 外部请求不直接调用 Repository，跨域协作由 usecase 发起。

## 2. 领域模型

### 2.1 聚合根 Application

| 字段 | 类型 | 说明 |
| ----------------- | ------------------ | ------------------ |
| signNo | SignNo | 业务编号，唯一 |
| tenantCode | String | 租户 |
| protocolVersion | String | 协议版本 |
| externalRequestNo | ExternalRequestNo | 租户侧申请单号 |
| status | ApplicationStatus | 当前状态 |
| applicant | ApplicantInfo | 投保人信息 |
| insured | InsuredInfo | 被保人信息 |
| amount | Money | 申请金额 |
| version | long | 乐观锁版本 |

聚合行为（每个行为内部先做状态机校验，非法迁移抛领域异常）：

- `sign(...)`：落库初始化，产出 `ApplicationSigned`
- `startAccept()` / `acceptSucceeded()` / `acceptFailed(reason)`
- `startUnderwriting()` / `underwritingCompleted(result)`
- `startDelivering()` / `deliverySucceeded(caseInfo)` / `deliveryFailed(reason)`
- `requireSupplement(reason)` / `supplementReceived()`
- `cancel(operator, reason)`
- `reject(reason)`

### 2.2 实体

| 实体 | 说明 |
| ------------------- | ------------------------------------------------ |
| ApplicationMaterial | 影像材料，记录文件标识、类型、处理状态 |
| UnderwritingResult | 承保结果，记录结论、命中规则、判定时间 |
| DeliveryTask | 交付任务，记录请求、响应、返回码、重试次数 |
| TraceRecord | 处理轨迹，只追加 |
| ExceptionRecord | 异常记录，只追加，可标记处理结果 |

### 2.3 值对象

- `SignNo`：不可变，创建时校验格式。
- `TenantProtocolKey(tenantCode, protocolVersion)`：租户协议路由键。
- `ExternalRequestNo`：租户侧单号，与 TenantProtocolKey 组成幂等键。
- `ApplicantInfo` / `InsuredInfo`：证件类型、证件号、姓名、联系方式。
- `Money`：金额与币种，禁止用 double。

### 2.4 领域服务

| 服务 | 职责 |
| ------------------- | ------------------------------------------------------ |
| SignNoGenerator | 生成 signNo，保证全局唯一 |
| IdempotencyGuard | 幂等判断，Redis 快速命中 + 数据库唯一键兜底 |
| AcceptancePolicy | 判定全部影像处理后是否受理成功 |
| UnderwritingPolicy | 承保规则判定，输出结论 |
| DeliveryRetryPolicy | 计算交付重试次数与下次重试时间 |

### 2.5 领域事件

`ApplicationSigned`、`AcceptanceSucceeded`、`AcceptanceFailed`、`UnderwritingCompleted`、`DeliverySucceeded`、`DeliveryFailed`、`SignCompleted`、`ApplicationCancelled`。

事件由 usecase 收集并在事务提交后投递 RocketMQ。

### 2.6 Repository

```java
public interface ApplicationRepository {

    Application findBySignNo(SignNo signNo);

    Optional<Application> findByExternalRequestNo(TenantProtocolKey key, ExternalRequestNo requestNo);

    void save(Application application);

    void update(Application application);

    PageResult<Application> page(ApplicationQuery query);
}
```

## 3. 状态机

迁移表（行 = 当前状态，列 = 事件）：

| 当前状态 | 事件 | 目标状态 |
| -------------- | ---------------------- | -------------- |
| 待受理 | 开始受理 | 受理中 |
| 受理中 | 受理成功 | 受理成功 |
| 受理中 | 受理失败 | 受理失败 |
| 受理中 | 转问题件 | 待补材 |
| 受理失败 | 重新受理 | 受理中 |
| 受理失败 | 撤销 | 已撤销 |
| 受理成功 | 开始承保 | 承保中 |
| 承保中 | 承保通过 | 承保完成 |
| 承保中 | 承保拒保 | 已拒保 |
| 承保完成 | 开始交付 | 交付中 |
| 交付中 | 交付成功 | 签收完成 |
| 交付中 | 交付失败 | 交付中（重试） |
| 交付中 | 转问题件 | 待补材 |
| 待补材 | 补材到达 | 受理中 |
| 待补材 | 撤销 | 已撤销 |
| 待受理 | 撤销 | 已撤销 |

约束：

- 终态（签收完成、已拒保、已撤销）不再接受任何事件，到达的消息直接丢弃并记录。
- 同一状态内允许重复事件时（如交付失败重试），只累加重试计数，不做状态迁移。
- 状态机实现放在 `domain/statemachine`，迁移表以不可变 Map 声明，扩展迁移需改代码并补测试。

## 4. 关键流程

### 4.1 同步签收

```
外部请求 → Gateway（认证 / Tenant 识别 / TraceId）
        → SignApplicationUseCase
            1. 解析 Header 中的 tenantCode + protocolVersion
            2. 路由 TenantAdapter
            3. 验签、解密、报文解析、参数校验
            4. IdempotencyGuard 判断
                 命中 → 返回已有 signNo
            5. SignNoGenerator 生成 signNo
            6. 构建 Application 聚合并落库（唯一键兜底）
            7. 事务提交后投递 ApplicationSigned
        → 返回 signNo
```

幂等冲突（唯一键异常）时回查已有记录并返回其 signNo，不向租户抛错。

### 4.2 异步受理

```
ApplicationSigned
  → AcceptApplicationUseCase
      1. 加载聚合，校验状态为待受理 / 受理失败
      2. 迁移到受理中
      3. 逐份影像：FileStorage 下载 → 校验 → 转存 → 更新材料状态
      4. AcceptancePolicy 判定
           全部成功 → 受理成功，投递 AcceptanceSucceeded
           存在失败 → 受理失败，写异常记录，按退避策略安排重试
```

影像处理以单份材料为最小重试单元，避免整单重跑。

### 4.3 交付与签收完成

```
UnderwritingCompleted
  → DeliverApplicationUseCase
      1. 校验状态为承保完成
      2. 选择 TenantDeliveryAdapter（Application 域内的交付适配器）
      3. 组装报文、签名、加密、调用审核系统
      4. 成功 → 迁移到签收完成，投递 SignCompleted
      5. 失败 → 累加重试次数，DeliveryRetryPolicy 计算下次重试时间
```

`SignCompleted` 由 Case 业务域消费，用于创建案件。Application 只投递事件，不感知 Case 的建案细节。

## 5. 幂等、并发与一致性

- 数据库唯一键 `uk_tenant_external_no (tenant_code, external_request_no)` 是幂等的最终保障。
- Redis 幂等键 `claim:application:idem:{tenantCode}:{externalRequestNo}`，值为 signNo，TTL 10 分钟，仅做快速命中。
- 消息消费幂等：`t_application_message_consume` 以 `(message_id)` 唯一，消费前先插入。
- 状态更新使用 `version` 乐观锁，冲突时重新加载并重新判定状态，不重试整笔业务。
- 与 Case 业务域采用最终一致：Application 交付成功 + 事件投递成功即认为本域完成，不等待 Case 建案结果。

## 6. 重试、超时与恢复

| 场景 | 策略 |
| -------------- | ------------------------------------------------------------------ |
| 影像处理失败 | 3 次重试，间隔 1m / 5m / 15m；耗尽转人工 |
| 交付失败 | 间隔 30s / 2m / 10m / 30m，最多 4 次，耗尽转人工 |
| 受理中超时 | 超过 30 分钟未推进，由补偿任务按当前状态重新投递受理消息 |
| 交付中超时 | 超过 15 分钟未拿到回执，先回执反查，再决定重投 |
| 待补材超时 | 按租户配置时限，到期转人工或关闭 |

补偿任务统一走 XXL-JOB，任务只做「按状态重新投递消息 / 触发反查」，不直接修改状态。所有重试都必须先校验当前状态，已是目标状态则跳过。

## 7. 租户适配

```java
public interface TenantAdapter {

    TenantProtocolKey key();

    SignRequest verifyAndDecode(RawRequest rawRequest);

    SignResponse encode(SignResult result);
}
```

- 路由：`TenantProtocolKey → TenantAdapter`，注册表在启动时装配，缺失即拒绝请求。
- 目录约定：`infrastructure/adapter/{tenantCode}/{protocolVersion}/`，同一租户不同协议版本各自独立实现，公共逻辑上提到 `AbstractTenantAdapter`。
- Adapter 内只做协议差异处理，不做业务规则判定；业务规则一律在 domain / usecase。
- 错误码映射在 Adapter 内完成，平台内部异常不直接透出给租户。

## 8. 数据模型

```sql
CREATE TABLE t_application
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    sign_no             VARCHAR(40)  NOT NULL COMMENT '申请编号',
    tenant_code         VARCHAR(32)  NOT NULL COMMENT '租户编码',
    protocol_version    VARCHAR(16)  NOT NULL COMMENT '协议版本',
    external_request_no VARCHAR(64)  NOT NULL COMMENT '外部申请单号',
    status              VARCHAR(32)  NOT NULL COMMENT '申请状态',
    applicant_name      VARCHAR(64)  NOT NULL COMMENT '投保人姓名',
    applicant_id_no     VARCHAR(64)  NOT NULL COMMENT '投保人证件号',
    insured_name        VARCHAR(64)  NOT NULL COMMENT '被保人姓名',
    insured_id_no       VARCHAR(64)  NOT NULL COMMENT '被保人证件号',
    amount              DECIMAL(18, 2) NOT NULL COMMENT '申请金额',
    currency            VARCHAR(8)   NOT NULL DEFAULT 'CNY' COMMENT '币种',
    supplement_round    INT          NOT NULL DEFAULT 0 COMMENT '补材轮次',
    version             BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    signed_at           DATETIME(3)  NOT NULL COMMENT '签收时间',
    accept_finished_at  DATETIME(3)  NULL COMMENT '受理完成时间',
    completed_at        DATETIME(3)  NULL COMMENT '签收完成时间',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sign_no (sign_no),
    UNIQUE KEY uk_tenant_external_no (tenant_code, external_request_no),
    KEY idx_tenant_status_created (tenant_code, status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='理赔申请';

CREATE TABLE t_application_material
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    sign_no       VARCHAR(40) NOT NULL COMMENT '申请编号',
    material_type VARCHAR(32) NOT NULL COMMENT '材料类型',
    file_id       VARCHAR(64) NOT NULL COMMENT '文件标识',
    file_name     VARCHAR(255) NOT NULL COMMENT '文件名',
    storage_type  VARCHAR(32) NOT NULL COMMENT '存储类型',
    status        VARCHAR(16) NOT NULL COMMENT '处理状态',
    fail_reason   VARCHAR(512) NULL COMMENT '失败原因',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sign_file (sign_no, file_id),
    KEY idx_sign_no (sign_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='申请影像材料';

CREATE TABLE t_application_underwriting
(
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    sign_no        VARCHAR(40) NOT NULL COMMENT '申请编号',
    result         VARCHAR(16) NOT NULL COMMENT '承保结论',
    hit_rule       VARCHAR(128) NULL COMMENT '命中规则',
    decision_note  VARCHAR(512) NULL COMMENT '判定说明',
    decided_at     DATETIME(3) NOT NULL COMMENT '判定时间',
    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sign_no (sign_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='自动承保结果';

CREATE TABLE t_application_delivery
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    sign_no       VARCHAR(40) NOT NULL COMMENT '申请编号',
    target_system VARCHAR(64) NOT NULL COMMENT '目标系统',
    request_body  MEDIUMTEXT  NULL COMMENT '请求报文',
    response_body MEDIUMTEXT  NULL COMMENT '响应报文',
    result_code   VARCHAR(32) NULL COMMENT '返回码',
    result_msg    VARCHAR(512) NULL COMMENT '返回信息',
    status        VARCHAR(16) NOT NULL COMMENT '交付状态',
    retry_count   INT         NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_at DATETIME(3) NULL COMMENT '下次重试时间',
    trace_id      VARCHAR(64) NULL COMMENT '链路标识',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_sign_no (sign_no),
    KEY idx_status_retry (status, next_retry_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='审核交付任务';

CREATE TABLE t_application_trace
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    sign_no    VARCHAR(40) NOT NULL COMMENT '申请编号',
    node       VARCHAR(32) NOT NULL COMMENT '处理节点',
    status     VARCHAR(16) NOT NULL COMMENT '节点结果',
    message    VARCHAR(512) NULL COMMENT '节点说明',
    operator   VARCHAR(64) NULL COMMENT '操作方',
    trace_id   VARCHAR(64) NULL COMMENT '链路标识',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sign_created (sign_no, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='申请处理轨迹';

CREATE TABLE t_application_exception
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    sign_no      VARCHAR(40)  NOT NULL COMMENT '申请编号',
    stage        VARCHAR(32)  NOT NULL COMMENT '发生阶段',
    error_code   VARCHAR(64)  NULL COMMENT '错误码',
    error_msg    VARCHAR(1024) NOT NULL COMMENT '错误信息',
    retry_count  INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    handle_state VARCHAR(16)  NOT NULL COMMENT '处理状态',
    trace_id     VARCHAR(64)  NULL COMMENT '链路标识',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_sign_created (sign_no, created_at),
    KEY idx_handle_state (handle_state)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='申请异常记录';

CREATE TABLE t_application_message_consume
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    message_id  VARCHAR(64) NOT NULL COMMENT '消息标识',
    topic       VARCHAR(64) NOT NULL COMMENT '主题',
    consumed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消费时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_id (message_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='消息消费幂等';
```

## 9. 接口与消息

REST：

| 方法 | 路径 | 说明 |
| ---- | ----------------------------- | -------------------------- |
| POST | `/api/application/sign` | 外部申请接入（经 Gateway） |
| GET | `/api/application/{signNo}` | 申请详情 |
| GET | `/api/application/page` | 申请列表 |
| POST | `/api/application/{signNo}/cancel` | 申请撤销 |
| POST | `/api/application/{signNo}/supplement` | 补材提交 |

RocketMQ：

| Topic | 方向 | 说明 |
| ----------------------------- | ---- | -------------------------------- |
| `claim-application-signed` | 生产 | 签收落库后触发受理 |
| `claim-application-accepted` | 生产 | 受理结果 |
| `claim-application-delivered` | 生产 | 交付成功，Case 域消费建案 |
| `claim-application-callback` | 消费 | 外部交付回执（若租户异步返回） |

## 10. 设计取舍

- **受理成功以影像处理完成为边界**：影像失败时后续承保和交付没有意义，早失败早暴露，也避免脏数据进入审核系统。
- **Application 不存 caseNo**：Case 持有 sourceSignNo，关联关系归 Case 所有，避免双写和跨域数据不一致。
- **幂等用唯一键兜底而非分布式锁**：唯一键是最终事实，Redis 只在热点场景减少一次数据库往返。
- **补偿任务只重投消息**：把状态变更收敛在用例内，避免补偿脚本绕过状态机。
- **租户适配隔离在 infrastructure**：协议差异变化频繁，不能污染领域模型。
