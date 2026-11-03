# 031 多租户协议流水线接入与网关租户限流（MVP）设计

## 1. 改动模块

| 模块 | 改动 |
| --- | --- |
| `claim-application-service` | 新增六个标准命令租户流水线、默认验签/解密与标准报文转换；删除租户 A 适配、旧默认流水线、旧五阶段组件与租户 A 请求 DTO；更新租户协议包说明 |
| `claim-gateway` | 签收接口按六个租户配置配额，移除 `tenant-a` 配额 |
| `specs/031-tenant-protocol-onboarding` | 需求、设计与任务 |

## 2. 目录结构

```text
interfaces/web/tenantprotocol
├─ pipeline
│  ├─ AbstractStandardCommandTenantProtocolPipeline.java
│  ├─ TenantProtocolPipeline.java
│  ├─ TenantProtocolPipelineRegistry.java
│  └─ package-info.java
├─ security
│  ├─ DefaultTenantProtocolDecryptor.java
│  ├─ DefaultTenantProtocolSignatureVerifier.java
│  ├─ TenantProtocolDecryptor.java
│  └─ TenantProtocolSignatureVerifier.java
├─ standard
│  ├─ StandardClaimApplicationIntakeCommandConverter.java
│  └─ StandardClaimApplicationIntakeRequest.java
└─ tenants
   ├─ picclife
   │  ├─ PicclifeProtocolPipeline.java
   │  └─ package-info.java
   ├─ xinhua
   ├─ cpic
   ├─ pingan
   ├─ taikang
   └─ yongchong
```

不恢复旧的 `tenantprotocol/core`、`default` 或 `stage` 包。

## 3. 核心链路

```text
外部请求（X-Tenant-Code + X-Protocol-Version）
  → 网关 TenantGlobalFilter：请求头缺失直接 400
  → 网关租户配额：命中租户配额且超额时返回 429
  → 应用服务 TenantContextFilter：写入 TenantContext 与 MDC
  → ClaimApplicationIntakeController
  → TenantProtocolPipelineRegistry.getPipeline(tenantInfo)
  → 对应租户 ProtocolPipeline.execute(rawPayload)
  → 默认验签（空实现）→ 默认解密（明文放行）
  → 标准报文解析、Bean Validation 与标准命令转换
  → 注册中心校验命令租户编码
  → ClaimApplicationIntakeUseCase
```

- 注册中心、控制器与租户上下文过滤器的契约不变。
- 默认验签与解密不读取报文字段；标准转换器读取报文但绝不记录正文与敏感字段。

## 4. 租户流水线

| 中文名称 | 租户编码 | 流水线类 | 包 |
| --- | --- | --- | --- |
| 人保健康 | `PICCLIFE` | `PicclifeProtocolPipeline` | `tenants.picclife` |
| 新华保险 | `XINHUA` | `XinhuaProtocolPipeline` | `tenants.xinhua` |
| 太平洋保险 | `CPIC` | `CpicProtocolPipeline` | `tenants.cpic` |
| 平安保险 | `PINGAN` | `PinganProtocolPipeline` | `tenants.pingan` |
| 泰康保险 | `TAIKANG` | `TaikangProtocolPipeline` | `tenants.taikang` |
| 永诚保险 | `YONGCHONG` | `YongchongProtocolPipeline` | `tenants.yongchong` |

每个类：

- 继承公共标准命令流水线，使用 `@Component` 注册。
- 私有常量 `TENANT_CODE` 与 `PROTOCOL_VERSION = "v1"`。
- `supports` 判断租户编码与协议版本是否精确匹配。
- `execute` 记录流水线日志，调用默认验签、默认解密与标准转换器后返回 `ClaimApplicationIntakeCommand`。

流水线日志格式：

```text
<租户中文名称>租户协议流水线开始处理标准报文，tenantCode=<编码>，protocolVersion=v1
```

## 5. 删除清单

| 路径 | 原因 |
| --- | --- |
| `interfaces/web/tenantprotocol/tenants/tenant_a/` | 租户 A 无真实报文接入，由六个实际租户替代 |
| `interfaces/web/dto/request/TenantAIntakeRequest.java` | 仅服务租户 A 解析 |
| `interfaces/web/tenantprotocol/pipeline/DefaultTenantProtocolPipeline.java` | 删除租户 A 后无任何入口使用 |
| `interfaces/web/tenantprotocol/stage/` | 旧泛型五阶段端口与实现仅被旧默认流水线使用，由标准报文转换与安全空实现替代 |

保留：

- `TenantProtocolPipeline`：六个租户流水线的统一契约。
- `TenantProtocolPipelineRegistry`：按租户编码与协议版本路由。

## 6. 标准受理命令转换

标准报文由 `StandardClaimApplicationIntakeRequest` 表达，字段与 `ClaimApplicationIntakeCommand` 一一对应。`StandardClaimApplicationIntakeCommandConverter` 使用 Spring `ObjectMapper` 解析 JSON、使用 Bean Validation 校验字段，再创建人员值对象与 `MaterialSubmission`，最终注入当前流水线的租户编码。

- 报文为空、不是 JSON 对象或 JSON 结构非法时抛出 `BusinessException`，异常与日志均不得携带原报文。
- Bean Validation 失败时返回字段路径与约束原因，不进入签收用例。
- 人员值对象与材料值对象继续负责其自身不变式，避免转换层绕过领域校验。
- `claimApplicationNo` 为空白时规范为 `null`，其余字符串由领域模型和用例继续校验。

默认验签器仅记录“租户协议验签通过（默认实现，不校验签名）”后放行；默认解密器仅记录“租户协议解密完成（默认实现，明文放行）”并原样返回报文。二者通过接口注入到公共流水线，接入真实协议时可替换而不影响控制器与注册中心。

## 7. 网关租户限流

在 `claim-gateway/src/main/resources/application.yml` 的 `claim-application-intake` 接口下配置：

```yaml
tenants:
  PICCLIFE: ${CLAIM_APPLICATION_RATE_LIMIT_PICCLIFE_QPS:20}
  XINHUA: ${CLAIM_APPLICATION_RATE_LIMIT_XINHUA_QPS:20}
  CPIC: ${CLAIM_APPLICATION_RATE_LIMIT_CPIC_QPS:20}
  PINGAN: ${CLAIM_APPLICATION_RATE_LIMIT_PINGAN_QPS:20}
  TAIKANG: ${CLAIM_APPLICATION_RATE_LIMIT_TAIKANG_QPS:20}
  YONGCHONG: ${CLAIM_APPLICATION_RATE_LIMIT_YONGCHONG_QPS:20}
```

- 租户编码作为 `X-Tenant-Code` 的精确匹配值，大小写必须与请求头一致。
- 每个租户默认 20 QPS（单节点），可用对应环境变量独立覆盖。
- 路由兜底阈值保持 `default-qps=50`，租户配额不得超过该值。
- 删除原 `tenant-a` 配额与 `CLAIM_APPLICATION_RATE_LIMIT_TENANT_A_QPS` 环境变量。
- 现有 `ClaimApplicationRateLimitConfiguration` 的校验与 Sentinel 规则注册逻辑无需修改。

## 8. 异常与日志

- 流水线、默认验签与解密只记录租户标识和阶段，不记录报文、签名、密钥与证件号。
- 标准报文解析或校验失败时由转换器抛出业务异常，不在日志中附带原始报文。
- 网关配额非法时由现有启动校验直接终止启动。
- 无匹配流水线、重复匹配与空命令继续使用既有业务异常语义。

## 9. 对现有功能的影响

- 六个新租户在接入真实协议前使用统一标准报文即可完成签收；真实协议接入时，仅替换对应租户流水线的报文解析、验签、解密与字段转换。
- 删除租户 A 后，`tenant-a` 请求不再有匹配流水线，返回“未找到匹配的租户申请协议流水线”。
- 网关签收接口的租户限流从单租户变为六租户，路由兜底与其他接口限流不变。
- 签收用例、领域模型、数据库、消息契约、审核与承保链路不受影响。
