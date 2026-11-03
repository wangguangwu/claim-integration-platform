# 031 多租户协议流水线接入与网关租户限流（MVP）任务

## T1 标准报文转换与安全空实现

- 新增标准受理报文 DTO 与转换器：解析 JSON、校验标准字段、创建人员与材料值对象并生成标准受理命令。
- 新增默认验签和默认解密接口及空实现；验签只记录日志并放行，解密原样返回明文。
- 新增公共标准命令流水线基类，固定执行验签、解密、标准报文转换，避免六个租户重复实现相同链路。
- 完成条件：合法标准报文可转换为租户编码正确的 `ClaimApplicationIntakeCommand`；错误报文不进入签收用例，日志不含请求正文。

## T2 六租户流水线实现

- 在 `interfaces/web/tenantprotocol/tenants/` 下新增 `picclife`、`xinhua`、`cpic`、`pingan`、`taikang`、`yongchong` 六个包。
- 每个包新增一个继承公共标准命令流水线的 `@Component` 流水线类与包说明。
- 流水线声明对应租户编码与协议版本 `v1`；`supports` 精确匹配并记录租户中文名称、租户编码与协议版本。
- 完成条件：六个 Bean 可被注册中心收集，且每个 Bean 能将标准报文转换为标准受理命令。

## T3 删除租户 A/B 遗留适配

- 删除 `tenants/tenant_a` 包。
- 删除 `dto/request/TenantAIntakeRequest.java`。
- 删除 `pipeline/DefaultTenantProtocolPipeline.java`。
- 删除 `tenantprotocol/stage` 包及其全部类。
- 更新 `tenantprotocol` 与 `pipeline` 包说明，移除默认流水线与五阶段描述。
- 完成条件：代码中不存在租户 A/B、默认流水线与五阶段组件的引用；保留流水线契约与注册中心。

## T4 网关按租户限流

- 在 `claim-application-intake` 接口配额下新增六个租户配置，默认 20 QPS，并提供独立环境变量覆盖。
- 删除 `tenant-a` 配额与 `CLAIM_APPLICATION_RATE_LIMIT_TENANT_A_QPS`。
- 保持路由兜底 `default-qps=50` 与其他路由配置不变。
- 完成条件：配置可被现有属性绑定解析；网关启动日志显示 1 条兜底规则与 6 条租户配额规则。

## T5 单元测试、构建与配置校验

- 新增标准命令转换与六租户路由单元测试，覆盖合法转换、字段校验失败、非法 JSON、六租户精确匹配、默认验签和明文解密。
- 使用 JDK 21 编译 `claim-application-service` 与 `claim-gateway`。
- 校验网关配置的租户配额均在 1 与 50 之间。
- 完成条件：新增测试通过，两个模块编译通过，无残留引用与无用导入。

## T6 验收核对

- 核对六个租户分别命中对应流水线并输出标准受理命令。
- 核对 `tenant-a` 已无匹配流水线。
- 核对网关租户配额按租户独立生效。
- 完成条件：满足 `requirements.md` 第 8 节全部验收标准。
