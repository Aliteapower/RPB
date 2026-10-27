# Staff Product Permissions Implementation Plan

> 使用 subagent-driven-development 分工实现并审查；不产生新的用户审批节点，不提交或发布。

**Goal:** 租户管理员可按员工、门店管理产品模块权限，撤权真实生效。
**Architecture:** Auth 统一目录及有效权限解析，TenantAdmin 独立授权服务和 API，AppGate 继续检查商业授权。Vue 员工表单挂载独立权限面板。
**Tech Stack:** Java 21 / Spring / PostgreSQL / Vue 3 / TypeScript。
**Spec:** docs/superpowers/specs/2026-10-28-staff-product-permissions-design.md

## Global Constraints

- 保留原工作区，使用 D:/RPB；不改无关钱包组件。不提交推送部署。
- PostgreSQL 只用 target/local-postgres-current.txt 端口，独立 schema。
- 目录、解析、业务鉴权不能产生平行授权体系。先写测试并确认失败，再实现。

## Review Focus

- 显式空权限不会回退成全权限。
- 查询参数不能覆盖路径门店身份。
- 并发撤权不被陈旧保存恢复。
- 不可借管理接口修改另一门店或管理员。
- 原有账号与本地测试身份兼容；更新权限在已有会话生效。

## Task 1 — 授权用例与 API

- [x] 测试目录依赖、越权、租户门店、版本冲突/重试及审计。
- [x] 新建 auth/authorization/StaffPermissionCatalog.java、tenantadmin/application/StaffPermissionsService.java、tenantadmin/persistence/StaffPermissionsRepository.java、tenantadmin/api/StaffPermissionsController.java，以及 V087 migration。
- [x] 目录接口 permissions(): Set<String>，expand(Set<String>): Set<String>，groups(): List<Group>。Group/Choice 字段按规格。业务 API 同规格。
- [x] 记录 API/database/TDD review 和运行必要测试。

## Task 2 — 身份解析

- [x] 测试遗留账号回退、显式空集、A/B 店不同权限、路径优先、会话重新加载。
- [x] AuthPrincipal 增加 storePermissions 映射与兼容构造器；AuthRepository 从新表加载并合并目录外权限。
- [x] AuthSessionFilter 从 /api/v1/stores/{id} 路径或 /api/me/apps 查询门店选择有效权限；auth/me 暴露映射。
- [x] 验证真实 PostgreSQL 迁移及授权接口，回归 AppGate。

## Task 3 — 员工授权 UI

- [x] 独立 API 模块、权限勾选/依赖工具函数、Vue 权限面板，嵌入员工编辑页；创建完成后进入编辑以配置权限。
- [x] 按规格 GET/PUT，处理冲突、空集、失败和已购/停用提示，中英文标签。
- [x] authSession 在路由切换时刷新 auth/me 并按映射选当前门店，所有既有 can() 使用同一有效 permissions。
- [x] frontend 行为测试、构建。

## Task 4 — 集成审查与收尾

- [x] 审查跨模块接口、AppGate、权限依赖、租户门店、数据约束和审计回滚。
- [x] 运行定向后端测试、前端行为测试、npm run build、git diff --check。
- [x] release-note 记录兼容、迁移、验证与回滚限制；报告未验证内容。
