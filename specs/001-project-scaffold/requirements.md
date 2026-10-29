# 001 工程骨架

## 1. 功能目标

在写业务实现之前固定工程边界：模块怎么分、依赖往哪个方向走、目录长什么样、版本在哪里管。骨架可构建、可启动、可扩展，后续每个业务域各自出 spec 再填实现。

## 2. 业务规则

骨架阶段的规则是结构规则，全部可直接检查：

1. 模块归属唯一：每个后端模块都在根 POM 的 `<modules>` 中登记，不存在未登记模块；不单独建 BOM 模块，第三方版本只写在根 POM 的 `dependencyManagement`。
2. 骨架只固定到一级包：理赔申请/案件为 `interfaces`、`usecase`、`domain`、`infrastructure`；AI 服务为 `agent`、`tool`、`evidence`、`knowledge`、`policy`、`remediation`；网关为 `auth`、`tenant`、`filter`、`route`、`config`。二级及以下包由对应业务域 spec 定义后再建，骨架不预置。
3. 基准只有三样：POM、实际存在的文件、依赖关系。目录以文件中声明的包为准，foundation 里的子包只因类已存在而存在。
4. 依赖方向单向：`interfaces → usecase → domain ← infrastructure`；`domain` 不依赖 Spring Web、MyBatis-Plus、Redis；foundation 不依赖任何服务模块；服务模块之间没有 Maven 依赖。
5. 包名必须是合法 Java 标识符：`case` 是关键字，案件服务使用 `com.wangguangwu.claim.cases`。
6. 租户差异只允许出现在 `infrastructure` 下的 adapter；骨架不预置 `tenant-a` 一类示例目录。
7. 服务各自持有数据库，不跨库直连；跨域只通过消息或服务接口协作。
8. 文件能力统一走 FileStorage 接口，业务代码不依赖具体存储产品。
9. spec 按三位编号递增，一个 spec 只覆盖一个可独立验收的主题，编号不复用。

## 3. 正常场景

1. 首次克隆仓库，在根目录执行 `mvn -DskipTests package` 一次通过，产出全部可执行 jar。
2. 以 `local` profile 启动任一服务，Nacos 注册与配置中心关闭，端口按约定监听。
3. 网关收到同时带 `X-Tenant-Code`、`X-Protocol-Version` 的请求，按路径转发到对应服务。
4. `frontend/claim-web` 执行 `npm install && npm run build` 通过，产出静态资源。

## 4. 异常场景

| 场景 | 期望 |
| -------------------------- | ---------------------------------------------------------------------------------------- |
| JDK 低于 21 | 构建或启动失败并报 class 版本不兼容，不静默降级 |
| 网关请求缺少租户头 | 返回 400，响应体 `{"code":"TENANT_HEADER_MISSING","message":"缺少租户标识或协议版本"}`，请求不进入业务服务 |
| 下游服务未启动 | 网关返回 503，而不是 500，也不缓存错误结果 |
| 本地未启动 MySQL / Redis | 服务进程正常启动，健康检查为 DOWN，既不启动失败也不伪造 UP |

## 5. 边界条件

- 骨架不定义任何业务表、聚合、状态机；出现此类内容即视为 spec 越界。
- 骨架不预建二级及以下包，也不预置业务接口与租户适配实现，只保留一级包与约定。
- 本地 profile 只覆盖地址与注册开关，生产配置不在本 spec 落地。
- 运行需 JDK 21 及以上；PATH 上的 `java` 为 17 时启动失败，属预期。

## 6. 本次范围

- 多模块 Maven 工程与版本统一管理
- 后端模块划分、包结构、分层规则
- 各服务启动类、本地 `application.yml` 与公共 starter 自动装配
- 网关路由与租户识别、TraceId 过滤器
- 前端工程骨架与可执行构建
- spec 的编号与组织约定

## 7. 非本次范围

- 各业务域的聚合、实体、状态机、表结构：实现该域时单独成 spec
- 接口报文、租户协议细节
- 中间件部署与运维编排：`deploy` 目前只占位
- CI 流水线与代码格式检查工具接入

## 8. 验收标准

- `mvn -DskipTests package` 从根工程一次通过，Reactor 全部模块 SUCCESS，产出 7 个可执行 jar。
- 网关以 `local` profile 启动后 `/actuator/health` 返回 200。
- 无租户头的请求返回 400，响应体 code 为 `TENANT_HEADER_MISSING`。
- 携带租户头的 `/api/application/**`、`/api/case/**`、`/api/diagnosis/**` 均能匹配路由，下游未启动时为 503。
- `npm run build` 在 `frontend/claim-web` 通过，产出 `dist` 静态资源。
- 目录结构与 design 一致，不存在未登记的模块或层级。
- 依赖方向单向：`domain` 编译期不引入 Web、MyBatis-Plus、Redis。
