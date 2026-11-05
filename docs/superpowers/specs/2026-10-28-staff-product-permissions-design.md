# 员工产品线与模块授权

用户已授权按代码审计建议落地；本规格和计划不增加审批节点，不包含提交、推送或生产部署。

## 目标与方案

复用 Identity/RBAC、TenantAdmin、AppGate。新增每员工每门店的显式权限集合，按产品线和模块呈现。与只加页面开关、或修改全局账号权限相比，这一方案可表达 A 店可操作、B 店只读，且不扩大管理员可管理的范围。

保留现有账号权限作为未配置门店的兼容基线；有显式配置（含空集合）时替换目录内权限，不改变目录外的身份权限。普通员工没有自动新增权限，已有业务保持原授权直到管理员保存。禁止编辑 tenant_admin、store_manager、platform_admin 或本人，不支持通过本界面晋升角色。

## 边界

- Auth 拥有员工可授予权限目录与有效权限计算；TenantAdmin 拥有授权用例、仓储、审计；产品业务模块继续独立鉴权。
- 管理员和被管理员工均必须可访问路径门店，且同租户。提交只能包含白名单权限；新增权限不得超过管理员本人权限及门店已购产品范围。停用/到期产品仍可撤权，不能绕过 AppGate 开新业务。
- 员工账号门店分配同样限制管理员边界：新建只能分配自身有权门店；编辑不得新增或移除自己无权管理的既有门店；原默认门店可保留，变更默认店需本人有权。更新先锁员工账号，避免并发资料编辑覆盖门店归属。
- 权限依赖显式声明，保存会补齐必要查看权限；前端取消查看时移除依赖它的操作，避免表面关闭后又自动恢复。
- 支持预约排队桌台清台、PayNow、钱包、积分、卡券、存酒、账本；仅管理员身份可执行的设置不伪装为可委派权限。卡券方案管理依赖 catalog.view 只读选品，不包含商品写入。PayNow 建立与手动确认沿用同一真实权限，不展示未使用的 verification.review 开关。存酒仅查看员工可以通过原有受限、租户与门店隔离的顾客搜索读取历史；写操作继续独立检查 receive/deliver。
- PUT 使用 expectedVersion，首存为 0。相同版本冲突返回 409；相同请求重试且权限已一致时返回现状，不重复审计。
- 事务内锁员工账号、检查配置版本、保存、记录操作者及前后权限；审计失败回滚。不记录密码。
- 授权更改在下一次后端会话请求生效；前端登录、路由切换刷新权限映射，以当前门店权限展示入口。

## 数据库

V087 新增 auth_staff_store_permissions：tenant_id/store_id/account_id，permissions text[]，version bigint，created_at/updated_at。主键(account_id,store_id)，复合外键确保账号与门店同租户；复用 V083 的账号(id,tenant_id)唯一索引。无原权限回填、无业务数据删除。

## API 合同

GET/PUT `/api/v1/stores/{storeId}/tenant-admin/staff/{staffId}/permissions`。
PUT body `{expectedVersion: number, permissions: string[]}`，要求 `X-RPB-Staff-Permissions: 1` 且拒绝 same-site/cross-site Fetch Metadata。
Response `{success:true, version:number, inherited:boolean, permissions:string[], groups:Group[]}`。
Group `{key,appKey,labelZh,labelEn,provisioned:boolean,enabled:boolean,permissions:Choice[]}`。
Choice `{code,labelZh,labelEn,requires:string[],grantable:boolean}`。
错误沿用 400 REQUEST_INVALID、401 UNAUTHENTICATED、403 FORBIDDEN/STORE_SCOPE_MISMATCH、404 STAFF_NOT_FOUND，新增409 PERMISSION_VERSION_CONFLICT。
管理员授权端点由 tenant.admin.manage 和角色保护，不依赖待配置产品的运行开关。
AuthUserResponse 增量增加 storePermissions: Map<string,string[]>（仅显式配置门店的完整有效集合）；保留 permissions 兼容字段。

## 验收

测试覆盖授予与撤销、依赖、空集合、遗留回退、角色边界、目录白名单、不可越权授予、跨租户/门店、产品未开通/停用/过期、并发版本和重放、事务审计、同会话生效、前端门店切换。真实 PostgreSQL 验证使用 target/local-postgres-current.txt 所指实例中的独立测试 schema，不修改原业务 schema。保留所有已有工作区改动。
