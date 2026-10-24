# 钱包复用租户顾客管理入口（2026-10-24，未发布）

## 实现

- 钱包收银台增加“顾客管理”卡片，“更多”和“充值与积分”页顶部增加同一入口。
- 手机号查无记录时，显示“新增顾客”，跳转既有 `/stores/{storeId}/admin/customers`，打开原新增表单，带入查询手机号；保存由用户明确操作。
- 所有入口复用原 `TenantAdminCustomersPage`、tenant-admin 顾客 API、CustomerManagementApplicationService 和 customers 表。没有钱包独立档案、独立写接口或第二套去重规则。
- 原管理页接受可选关键词及新增意图，从钱包进入时提供“返回钱包收银台／返回充值与积分”。返回仅接受固定来源枚举，不能注入任意跳转地址。
- 新增链接仅对有 tenant_admin 角色、tenant.admin.manage 权限且获当前门店授权者展示；不扩大普通收银员权限。服务端沿用原管理员、租户及门店隔离。
- 返回后仍需查询、核对并确认选中顾客，维护档案不会直接充值、扣款或增减积分。
- 新的共享导航逻辑位于 `src/modules/customers/navigation.ts`；钱包复用入口组件为 `WalletCustomerManagementEntry.vue`。保留此前所有未提交改动。

## 测试与证据

- 先补入口权限、查无手机号新增跳转用例，基线 73 通过、2 失败；实现后主组件文件 75/75 通过。
- 完整钱包前端回归 153/153，包含共享导航返回白名单、现有手机号格式化、普通收银员隐藏入口、管理员可见、未授权门店隐藏，以及不自动创建账户等覆盖。
- 日志：`target/wallet-customer-entry-regression.log`；干净验证目录叠加当前实际源码 `target/wallet-recharge-separated-validation`。
- `npm run build`（vue-tsc 和 Vite）通过：`target/wallet-customer-entry-build.log`。
- `mvn -q -Dtest=CustomerManagementApplicationServiceTest,TenantAdminCustomerManagementUiValidationTest test`：10 项通过，无失败、错误或跳过；日志 `target/wallet-customer-entry-java.log` 与对应 surefire 报告。
- 遵循 `target/local-postgres-current.txt` 使用 PostgreSQL 61644；仅使用既有隔离 schema `wallet_transactions_0983618362a147a0b77cb19477406228`，临时后端 8091、前端 5191。
- 浏览器实际完成：钱包手机号查询无记录 → 原顾客管理新增表单（手机号已带入）→ 保存 → 编辑姓名 → 返回充值与积分 → 原手机号查到修改后的顾客，尚未确认选中。
- 验证顾客 ID `7cd04b2c-e54d-4476-93db-2d28ba34b550`，隔离测试手机号 +6581112469：tenant-admin 列表和钱包查询返回相同 ID、姓名、手机号；数据库只有 1 份档案，资金账户和积分账户均 0。
- 真实接口验证：重复手机号 409 CUSTOMER_PHONE_CONFLICT；未登录 401；无权门店 403。证据 `target/wallet-customer-entry-e2e-20261024/proof.json`。
- 手机视口 390 px：文档 scrollWidth 与 innerWidth 均为 390，无横向溢出。截图 `target/wallet-customer-entry-e2e-20261024/recharge-entry.png`。
- 验证完成后关闭本轮浏览器页、恢复视口，停止本轮 8091/5191 临时进程；原 8081 后端与 PostgreSQL 进程继续运行。126 个模块／测试源文件及租户顾客页与已通过回归的验证目录一致（换行归一化比较）。

## 审查与发布说明

TDD、UI、API 和 code-review：只复用已有管理权限和 API，不改变接口行为、顾客手机号唯一规则或客户资料归属。列表、维护与钱包办理不合并成新业务服务；无数据迁移、新表或新权限。其他产品线继续引用原顾客档案。

本轮未提交、推送或部署；生产尚无本轮入口。实际手机系统／真机未验证，已完成本地浏览器手机宽度验证。普通收银员没有档案写权限，需由现有租户管理员维护。

回退仅移除本轮钱包入口组件及引用、共享导航辅助函数、原顾客页的钱包来源/返回支持和相应测试文案；保留此前充值直接确认、积分操作与记录分离等改动。不回退或清理历史顾客数据。
