# 理赔业务集成平台

## 一、架构定位

**项目名称：** 理赔业务集成平台
**工程名称：** claim-integration-platform
**平台定位：** 面向多租户理赔场景，统一承接申请接入、案件阶段交互和跨系统协同，并提供 AI 异常诊断能力。

核心业务关系：

- 一个理赔申请可以关联多个案件。
- 一个案件归属于一个理赔申请。

### 1.1 核心能力

- **统一接入**：适配不同租户的协议、报文、字段、签名和加解密方式，统一转换为平台业务模型。
- **申请处理**：完成申请校验、签收、影像处理、自动承保、审核交付等理赔申请链路。
- **案件编排**：按报案、影像、理算等阶段推进案件，支持同步处理、异步回调和中断恢复。
- **全链路追踪**：贯通理赔申请、案件、阶段执行、外部调用、回执及异常记录。
- **异常诊断**：基于运行证据自主调查异常，完成根因分析、影响判断和处置建议。

### 1.2 技术栈

项目技术版本由根 POM、BOM 和前端 package.json 统一管理。

| 分类        | 技术                 | 版本       |
| ----------- | -------------------- | ---------- |
| Java        | JDK                  | 21.0.13    |
| 后端框架    | Spring Boot          | 3.5.16     |
| 微服务      | Spring Cloud         | 2025.0.3   |
| 微服务扩展  | Spring Cloud Alibaba | 2025.0.0.0 |
| 注册 / 配置 | Nacos Server         | 3.2.4      |
| 流量治理    | Sentinel             | 1.8.9      |
| ORM         | MyBatis-Plus         | 3.5.18     |
| 数据库      | MySQL                | 8.4.13 LTS |
| 缓存        | Redis                | 7.4.12     |
| 消息        | RocketMQ Server      | 5.5.1      |
| 调度        | XXL-JOB              | 3.5.0      |
| AI          | Spring AI            | 1.1.8      |
| 前端        | Vue                  | 3.5.44     |
| 前端语言    | TypeScript           | 5.9.3      |
| 前端构建    | Vite                 | 6.4.5      |
| UI          | Element Plus         | 2.14.7     |
| 构建        | Maven                | 3.10.0     |

技术约束：

- 后端统一使用 Java 21 和 Spring Boot 3 技术体系。
- 基础框架及依赖版本由根工程统一管理。
- 文件能力通过 FileStorage 接口隔离具体存储实现，业务代码不依赖具体存储产品。

### 1.3 理赔申请业务域

理赔申请负责理赔申请从外部接入到内部审核交付的完整处理链路。

核心链路：

**申请校验 → 签收落库 → 待受理 → 影像处理 → 受理成功 → 自动承保 → 审核交付 → 签收完成**

同步请求只完成：

- 租户协议转换
- 参数校验
- 幂等判断
- claimApplicationNo 生成
- 理赔申请落库

落库成功后立即返回 claimApplicationNo，后续影像、承保和审核流程异步执行。

关键状态：

- **同步签收成功**：申请完成校验及落库，平台返回 claimApplicationNo。
- **待受理**：理赔申请已进入平台，等待影像处理。
- **受理成功**：影像处理完成，可以进入后续业务处理。
- **受理失败**：影像处理失败，停止后续流程并记录异常。
- **签收完成**：审核系统成功接收申请并返回案件信息，建立理赔申请与案件的关联。

核心职责：

- 租户协议适配
- 申请校验
- 申请幂等
- claimApplicationNo 生成
- 理赔申请状态管理
- 影像处理
- 自动承保
- 审核交付
- 问题件及补材
- 申请撤销
- 案件关联
- 处理轨迹及异常记录

架构原则：

- **同步签收、异步受理**，避免影像处理和内部系统调用阻塞外部请求。
- 影像处理成功作为理赔申请的受理成功边界。
- 自动承保和审核交付属于受理后的内部处理。
- 异步流程通过状态驱动，失败后根据当前状态进行重试、恢复或补偿。

### 1.4 案件业务域

案件负责案件进入平台后的规则判断、阶段编排、外部交付和回执处理。

核心链路：

**案件进入 → 规则判断 → 阶段编排 → 外部交付 → 回执处理**

典型阶段：

**报案 → 影像 → 理算**

不同阶段可以采用不同交互模式：

- **同步阶段**：外部调用完成并确认结果后继续下一阶段。
- **异步阶段**：提交后进入等待状态，收到回调后恢复执行。

例如影像阶段：

**影像提交 → 等待回调 → 回调成功 → 继续后续阶段**

核心职责：

- 案件管理
- 推送规则判断
- 阶段编排
- 调度
- 租户交付适配
- 外部接口调用
- 异步回调处理
- 失败重试及恢复
- 阶段轨迹记录
- 异常记录

### 1.5 AI 异常诊断

构建面向理赔业务异常的 Diagnosis Agent，对理赔申请和案件的运行异常进行自主调查、根因分析和处置判断。

核心链路：

**异常触发 → 构建上下文 → 提出诊断假设 → 调用 Tool 取证 → 验证假设 → 补充查证 → 根因判断 → 处置决策**

Agent 可以根据异常上下文自主查询：

- 理赔申请状态
- 案件状态及当前阶段
- 理赔申请/案件处理轨迹
- 外部调用记录
- 回执记录
- 重试及补偿记录
- 租户错误码
- 协议说明
- 历史相似异常

数据来源分为两类：

- **实时业务数据**：通过 Tool 查询。
- **协议、错误码、历史故障知识**：通过知识库检索。

诊断采用迭代调查模式：

**Observe → Hypothesis → Tool → Verify → Continue / Stop**

每轮根据新增 Evidence 判断当前假设是否成立：

- 证据不足时继续调用 Tool 查证。
- 证据充分时形成根因判断。
- 达到最大调查轮次或置信度不足时转人工处理。

诊断结果包括：

- Root Cause
- Evidence
- Impact
- Confidence
- Suggestion
- Action Level

异常处置由 Policy Engine 控制：

| Action Level | 处理方式                           |
| ------------ | ---------------------------------- |
| AUTO         | 低风险、幂等、可逆操作允许自动执行 |
| APPROVAL     | 存在业务影响的操作需要人工确认     |
| MANUAL       | 高风险操作仅输出诊断结果和处理建议 |

Remediation Tool 负责实际处置，例如：

- 回执反查
- 状态刷新
- 安全的幂等补偿

以下操作需要人工确认：

- 案件补推
- 阶段重新提交
- 可能影响外部业务状态的重新执行

以下操作禁止由 Agent 自动执行：

- 撤销业务申请
- 跳过业务阶段
- 直接修改核心业务数据
- 绕过业务规则强制修改状态

架构边界：

- Agent 负责调查、推理和 Tool 选择。
- Tool 负责查询真实业务数据。
- 知识库负责提供协议、错误码和历史故障知识。
- Policy Engine 负责动作授权。
- Remediation Tool 负责执行允许的处置动作。
- LLM 不直接修改理赔申请、案件或核心业务状态。
- Root Cause 必须关联实际 Evidence。
- 证据不足或置信度过低时转人工处理。

## 二、总体架构

### 2.1 业务链路

#### 理赔申请链路

**外部系统 → 理赔申请 → 申请校验 → 签收落库 → 影像处理 → 自动承保 → 审核交付 → 签收完成**

理赔申请负责外部申请进入平台后的标准化、受理和内部交付。

#### 案件链路

**理赔申请 → 案件 → 规则判断 → 阶段编排 → 外部系统**

典型阶段：

**报案 → 影像 → 理算**

其中异步阶段提交完成后进入等待状态，收到外部回调后恢复执行。

#### 异常诊断链路

**理赔申请/案件 → 运行证据 → Diagnosis Agent → 根因判断 → 处置决策**

### 2.2 Tenant

Tenant 是平台内部的业务隔离单位，主要隔离：

- 数据
- 配置
- 权限
- 协议
- 资源

租户协议通过以下维度选择：

**tenantCode + protocolVersion**

其中：

- 外部系统表示平台之外的系统边界。
- Tenant 表示平台内部的业务上下文。

### 2.3 可靠性原则

平台核心业务统一遵循：

- 幂等
- 状态机
- 消息最终一致性
- 超时恢复
- 失败补偿
- 全链路可追踪

理赔申请的异步受理机制由理赔申请业务域设计文档定义。

案件的阶段等待、异步回调、重试和恢复机制由案件业务域设计文档定义。

## 三、工程架构

整体工程：

```
claim-integration-platform
├─ frontend
│  └─ claim-web
├─ backend
│  ├─ claim-gateway
│  ├─ claim-application-service
│  ├─ claim-case-service
│  ├─ claim-ai-service
│  └─ claim-foundation
├─ specs
│  └─ 001-project-scaffold
├─ deploy
└─ docs
   ├─ architecture
   ├─ environment
   ├─ integration
   └─ testing
```

### 3.1 claim-web

统一前端工程。

主要功能：

- 平台首页
- 申请管理
- 案件管理
- AI 异常诊断
- 租户管理

目录结构：

```
claim-web
└─ src
   ├─ views
   │  ├─ portal
   │  ├─ application
   │  ├─ case
   │  ├─ diagnosis
   │  └─ tenant
   ├─ layouts
   ├─ components
   ├─ api
   ├─ stores
   ├─ router
   └─ utils
```

理赔申请详情主要展示：

- 基本信息
- 签收轨迹
- 参数校验结果
- 影像材料
- 承保结果
- 审核交付结果
- 异常记录
- 关联案件

案件详情主要展示：

- 基本信息
- 来源理赔申请
- 当前阶段
- 阶段轨迹
- 外部调用
- 外部回执
- 重试记录
- 异常记录

### 3.2 claim-gateway

包名：

**com.wangguangwu.claim.gateway**

职责：

- 认证
- Tenant 识别
- 请求路由
- 流量治理
- TraceId
- 基础请求治理

Gateway 只处理入口层能力，不处理租户协议转换及业务规则。

### 3.3 claim-application-service

业务域：

**理赔申请**

数据库：

**claim_application_db**

包名：

**com.wangguangwu.claim.application**

目录结构：

```
claim-application-service
└─ com.wangguangwu.claim.application
   ├─ interfaces
   ├─ usecase
   ├─ domain
   └─ infrastructure
      ├─ persistence
      ├─ adapter
      ├─ file
      └─ mq
```

租户接入适配：

```
adapter
├─ tenant-a
│  ├─ v1
│  └─ v2
├─ tenant-b
│  └─ v1
└─ tenant-c
   └─ v1
```

协议路由：

**tenantCode + protocolVersion → TenantProtocolPipelineFactory → TenantProtocolPipelineRegistry → TenantProtocolPipeline**

TenantProtocolPipeline 固定按“验签 → 解密 → 解析 → 校验 → 转换”执行，负责处理租户接入协议差异；验签与解密提供默认直通实现，真实租户按需覆写：

- 协议解析
- 参数校验
- 报文转换
- 签名验签
- 加解密
- 业务编码映射

请求链路：

**外部请求 → Tenant 识别 → TenantProtocolPipelineFactory → TenantProtocolPipelineRegistry → TenantProtocolPipeline（验签 → 解密 → Parser → Validator → Converter） → 理赔申请用例 → ClaimApplication**

理赔申请内部的聚合、实体、值对象、领域服务、Repository、状态机及表结构由理赔申请业务域设计文档定义。

### 3.4 claim-case-service

业务域：

**案件**

数据库：

**claim_case_db**

包名：

**com.wangguangwu.claim.cases**

说明：`case` 是 Java 关键字，不能作为包名，案件服务统一使用 `cases`。

目录结构：

```
claim-case-service
└─ com.wangguangwu.claim.case
   ├─ interfaces
   ├─ usecase
   ├─ domain
   └─ infrastructure
      ├─ persistence
      ├─ delivery
      ├─ mq
      └─ redis
```

典型阶段链：

**ReportHandler → ImageHandler → SettlementHandler**

执行规则：

- 同步阶段执行成功后直接推进下一阶段。
- 异步阶段提交成功后暂停执行。
- 收到对应回调后恢复阶段链。

租户交付适配：

```
delivery
└─ adapter
   ├─ tenant-a
   │  ├─ v1
   │  └─ v2
   ├─ tenant-b
   │  └─ v1
   └─ tenant-c
      └─ v1
```

协议路由：

**tenantCode + protocolVersion → TenantDeliveryAdapter**

TenantDeliveryAdapter 负责：

- 加解密
- 签名验签
- 报文转换
- 外部接口调用
- 返回码映射
- 回执解析

案件内部的聚合、实体、阶段模型、状态机、领域服务、Repository 及表结构由案件业务域设计文档定义。

### 3.5 claim-ai-service

包名：

**com.wangguangwu.claim.ai**

建议结构：

```
claim-ai-service
├─ agent
├─ tool
├─ evidence
├─ knowledge
├─ policy
└─ remediation
```

各模块职责：

- **agent**：诊断流程、假设生成和 Tool 调度。
- **tool**：查询理赔申请、案件、轨迹、外部调用等实时业务数据。
- **evidence**：证据标准化、聚合和关联。
- **knowledge**：租户协议、错误码和历史故障知识检索。
- **policy**：AUTO / APPROVAL / MANUAL 动作授权。
- **remediation**：执行经过授权的处置动作。

输入：

- 理赔申请异常
- 案件异常
- 外部调用记录
- 回执记录
- Trace
- 重试及补偿记录

输出：

- Root Cause
- Evidence
- Impact
- Confidence
- Suggestion
- Action Level
- Remediation Result

### 3.6 claim-foundation

公共基础模块：

```
claim-foundation
├─ common
├─ tenant
└─ observability
```

职责：

- **common**：公共返回模型、异常、枚举和基础工具。
- **tenant**：Tenant 上下文、租户配置和协议版本。
- **observability**：Trace、Metric、操作日志和审计日志。

## 四、架构边界

### 4.1 业务域边界

核心业务关系：

- 一个理赔申请可以关联多个案件。
- 一个案件归属于一个理赔申请。

核心业务编号：

- 理赔申请：claimApplicationNo
- 案件：caseNo

案件保存 sourceClaimApplicationNo，用于追溯来源理赔申请。

服务边界：

- 理赔申请业务域由 claim-application-service 承载。
- 案件业务域由 claim-case-service 承载。
- 两个业务域独立维护自己的业务状态和数据。
- 不直接访问对方数据库。
- 跨业务域通过消息或服务接口协作。

### 4.2 案件阶段边界

案件维护案件当前阶段，阶段 Handler 负责执行具体阶段逻辑。

典型链路：

**报案 → 影像 → 理算**

阶段分为：

- **同步阶段**：执行成功后直接推进下一阶段。
- **异步阶段**：提交后进入等待状态，收到回调后恢复执行。

例如影像阶段：

**提交影像 → 等待回调 → 处理回调 → 推进下一阶段**

具体阶段模型、状态迁移、回调幂等和恢复机制由案件业务域设计文档定义。

### 4.3 数据边界

数据库按业务域拆分：

- **claim_application_db**：理赔申请业务域数据。
- **claim_case_db**：案件业务域数据。

跨域只通过业务编号、消息或服务接口建立关联。

总架构不定义具体表结构。

### 4.4 Tenant 边界

Tenant 负责平台内部的数据、配置、权限、协议和资源隔离。

协议实现统一通过：

**tenantCode + protocolVersion**

进行路由：

- 理赔申请 → TenantProtocolPipeline
- 案件 → TenantDeliveryAdapter

租户差异应收敛在租户协议接入层，不进入核心业务逻辑。

### 4.5 文档边界

总架构负责定义：

- 系统定位
- 核心业务关系
- 业务域划分
- 服务边界
- 系统协作方式
- 工程结构
- 可靠性原则
- AI 能力边界

业务域内部设计按主题编号单独成 Spec：

```
specs
├─ 001-project-scaffold
│  ├─ requirements.md
│  ├─ design.md
│  └─ tasks.md
└─ 00N-<topic>
   ├─ requirements.md
   ├─ design.md
   └─ tasks.md
```

spec 按三位编号递增，一个 spec 只覆盖一个可独立验收的主题。各业务域在开始实现时单独成 spec，不提前铺开。

业务域设计文档负责定义：

- 聚合
- 实体
- 值对象
- 领域服务
- 状态机
- 领域事件
- Repository
- 数据模型
- 表结构
- 业务规则
- 幂等机制
- 重试及异常恢复

总架构只确定系统边界和协作关系，不展开业务域内部实现。
