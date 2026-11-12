# 员工产品线与模块授权实现报告

## Scope

D:/RPB 本地实现；无提交、推送或生产部署。入口：租户管理 → 员工 → 编辑 → 产品线与模块授权。账号资料与门店访问仍使用原表单；产品权限由独立面板按已保存的员工门店单独保存。创建员工后进入编辑页继续配置。

## Architecture Review

身份/RBAC 保留角色及旧账号权限，新增 Auth 目录和每店有效权限解析。TenantAdmin 独立应用服务/仓储负责普通员工授权命令和审计。产品模块继续消费 CurrentActor 和 AppGate，不引入平行业务执行链。

无显式配置的门店继续使用旧账号权限；显式配置（包括空集合）替换目录内权限，目录外原有权限保持兼容。不自动扩大员工产品权限，不把普通员工变为管理员。每个受保护前端路由刷新 auth/me，基于原始权限及门店覆盖选择当前店权限；后端每个会话请求重新加载，不等待重新登录。

## API Review Report

### Endpoint

GET/PUT `/api/v1/stores/{storeId}/tenant-admin/staff/{staffId}/permissions`，合同见 design 文档。PUT 带 expectedVersion、permissions 和 `X-RPB-Staff-Permissions: 1`。

### Contract / Permission / Compatibility

明确请求及响应记录；400/401/403/404沿用稳定错误，新增409 PERMISSION_VERSION_CONFLICT。只有 tenant_admin 角色及 tenant.admin.manage 可管理，管理员与员工必须同租户且均可访问目标门店；禁止编辑自己、平台管理员、租户管理员及店长。只能新增自身已有且门店已购产品范围内的白名单权限；停用/到期不阻止撤权，授权不替代 AppGate 商业检查。

AuthUserResponse 增量增加 storePermissions；原 permissions 保留为账号基线。路径门店不能被 storeId 查询参数覆盖；仅 `/api/me/apps` 从其既有查询参数选择店权限。

原 POST/PATCH 员工接口增加操作者门店边界检查，保留跨租户/无效门店400兼容；同租户但管理员无权变更的门店403。新建只可分配自身门店；更新要求新旧集合在管理员无权范围内完全相等。原默认门店可保留，新的默认门店须可管理。事务先锁员工账号，防止并发读旧集合后覆盖其他门店关系。

### Replay / Idempotency

事务锁员工账号后比较版本，陈旧且不同内容409；重复相同最终集合返回当前结果且不重复审计。初次显式保存即使与旧集合相同，也建立独立门店配置。审计错误返回 PERSISTENCE_ERROR 并回滚授权。

## Database Review Report

### Migration Scope / Constraints / Index / Tenant Isolation

V087 新增 auth_staff_store_permissions，复用 V083 的账号复合唯一索引。permissions text[] 非空且无null元素；version > 0。主键 account_id/store_id，账号与门店复合外键均包含 tenant_id，另有tenant/store/account查询索引。无业务数据回填或删除。

测试从 target/local-postgres-current.txt 读取 D:/RPB 运行时端口61644，使用独立随机 staff_permissions_test_* schema 执行全部迁移，测试后只移除该测试自身创建的 schema；原 daily_ledger_validation_20261022 schema 不改变。

## TDD Review Report

| Scenario | Evidence |
|---|---|
| 按员工/门店授予与撤销、空集、依赖与白名单 | StaffPermissionsServiceTest、AuthPrincipalStorePermissionsTest |
| 管理身份、租户、门店、本人权限上限及未购产品 | StaffPermissionsServiceTest、StaffPermissionsApiTest |
| 请求来源、错误码、版本冲突及重复提交 | StaffPermissionsApiTest、StaffPermissionsPostgresTest |
| 同一会话生效、A/B店独立、路径优先 | AuthStoreScopeFilterTest、StaffPermissionsPostgresTest |
| 真实PG迁移、跨租户FK、事务审计回滚、并发不同保存 | StaffPermissionsPostgresTest |
| 前后端完整HTTP合同与auth/me映射 | StaffPermissionsPostgresTest.administratorHttpSaveUpdatesExistingEmployeeSessionAndAuthMeMap |
| 存酒只读查顾客历史 | StorageCustomerQueryTest，新测试先出现403失败，再修复 |
| 卡券方案选品依赖与PayNow真实授权契约 | StaffPermissionsServiceTest，先重现断言失败，再修复目录 |
| Vue面板空集/冲突/切店/加载错误及权限同步 | staff-permissions-panel.test.mjs、staff-permissions.test.mjs |
| 商品只读选品不暴露管理员管理入口 | staff-catalog-entry.test.mjs，真实Vue渲染红绿回归 |
| 员工门店分配越权与其他店归属保留 | StaffStoreAccessPolicyTest、StaffPermissionsPostgresTest.staffManagementHttpCannotAddOrRemoveStoresOutsideAdministratorScope、staff-store-assignment.test.mjs |

## Code Review Report

独立只读审查发现并已修复：卡券管理选品缺少 catalog.view；存酒只读无法查询顾客；未消费的 payment.verification.review 假开关；员工不能进入的凭证模板管理开关。复查受影响范围结论 APPROVE，无遗留P0/P1。

最终授权边界复核另发现原员工门店分配接口可将员工加入管理员无权的同租户门店，已补独立策略及账号锁、API真实PG测试、前端只读控制。未裁掉员工其他店的既有归属；受影响范围再次独立复查 APPROVE，无遗留发现。

卡券只增加商品资料读权限，不授予商品写入/发布；商品管理链接保留管理员身份要求。存酒查人继续使用现有受限查询及 tenant/store/view 校验，存入/领取写权限不变。PayNow 的建立与手动确认沿用同一个真实权限，标签明确显示两种能力。

## Verification

最终验证通过：后端定向回归共103项（14个测试类，包括8项真实PostgreSQL测试），0失败/错误；前端7份测试文件31项行为/真实Vue渲染测试，全部通过；`npm run build`（vue-tsc + Vite）通过；`git diff --check`通过。Mockito动态agent/JVM共享类提示及既有换行提示不影响结果。

后端先运行包含 AppGate、身份、历史入口及存酒回归的97项，再在门店边界补丁后重跑29项受影响用例（StaffPermissionsPostgresTest、StaffStoreAccessPolicyTest、StaffPermissionsServiceTest、StaffPermissionsApiTest）；其余未改变代码及环境的结果复用，合计103个不同用例。测试自身创建的 staff_permissions_test_* schema 已确认无遗留。

前端命令：`node --experimental-strip-types --test src/test/frontend/staff-permissions.test.mjs src/test/frontend/staff-permissions-panel.test.mjs src/test/frontend/staff-catalog-entry.test.mjs src/test/frontend/staff-store-assignment.test.mjs src/test/frontend/cards-staff-panels.test.mjs src/test/frontend/product-entry-visibility.test.mjs src/test/frontend/product-history-surfaces.test.mjs`。

前端真实 Vue renderer 是组件行为验证，不代表真实浏览器或移动设备验收。未执行线上账号验收或实际金融交易，未运行全仓全部测试。已保留工作区原有钱包组件及其他未提交材料。

## Remaining Risk

- 管理员身份专属商品写入、存酒模式/地点/保管人等设置不委派。旧账号若人工持有目录外特殊业务权限，本轮保留；本面板“全部取消”只撤目录所列权限。
- 员工停留同一页面时可能短暂看到旧按钮；下一次后端请求会按已更新权限拒绝，路由切换会刷新显示。
- 部署必须先执行 V087 再启动新后端；旧前端不读取 storePermissions，发布时应配套前端并刷新缓存。
- 本轮无线上发布，未修改正式本地业务schema，不能声称线上问题已解决。
