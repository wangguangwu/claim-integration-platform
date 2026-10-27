# 001 工程骨架

## 1. 目标

在写业务实现之前固定工程边界：模块怎么分、依赖往哪个方向走、目录长什么样、版本在哪里管。骨架可构建、可扩展，后续每个业务域各自出 spec 再填实现。

## 2. 范围

- 多模块 Maven 工程与版本统一管理
- 后端模块划分、包结构与分层规则
- 前端工程骨架
- spec 的编号与组织约定

## 3. 不在本 spec 范围

- 各业务域的聚合、实体、状态机、表结构：实现该域时单独成 spec
- 接口报文、租户协议细节
- 中间件部署与运维编排：`deploy` 目前只占位

## 4. 硬约束

- JDK 21，Spring Boot 3.5.16，技术版本由根 POM 与 BOM 统一管理。
- 服务包名必须是合法 Java 标识符。`case` 是关键字，Case 服务使用 `com.wangguangwu.claim.cases`，总架构文档已同步修订。
- 服务之间不共享数据库，跨域通过消息或服务接口协作。
- 业务代码不依赖具体文件存储产品，统一走 FileStorage 接口。
- 租户差异收敛在 Adapter 层，不进入领域模型。

## 5. spec 组织约定

```
specs
└─ 00N-<topic>
   ├─ requirements.md
   ├─ design.md
   └─ tasks.md
```

- 一个 spec 只覆盖一个可独立验收的主题，用三位编号顺序递增。
- 业务域 spec 在该域开始实现时才创建，不提前铺开。
- 编号一旦使用不复用；作废的 spec 保留目录并在 requirements 中标明作废原因。

## 6. 验收标准

- `mvn -DskipTests package` 从根工程一次通过，全部模块产出构件。
- 目录结构与本文档描述一致，不存在未登记的模块或层级。
- 依赖方向单向：interfaces → usecase → domain ← infrastructure，domain 不依赖框架组件。
