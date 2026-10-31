# 存酒新增顾客与独立设置视图（2026-10-27）

## 需求与实现

- 在“找到顾客”标题右侧增加“＋ 新增顾客”。有当前门店权限的租户管理员可直接填写手机号及可选姓名/称呼/电邮，保存后自动选中继续办理存酒或取酒。
- 复用 customers 模块的 CustomerProfileFields、CountryPhoneField、统一隐私告知及 useCustomerManagement。新增通用 CustomerQuickCreate，createOnly 避免加载无关顾客列表，onCreated 返回完整的统一顾客档案；原管理页面默认行为不变。
- 保留 merchant attestation：沿用商户新增时默认确认已取得同意的规则及统一告知版本，用户取消确认后不能提交；不创建钱包账户、充值、卡或其他资产。
- 手机号重复或结果不确定时保留表单，提供“按此手机号查找已有顾客”；一次保存未返回时禁止重复点击。异步创建返回受门店/组件生命周期保护，不会把原门店结果选入新门店。
- 首页只保留查找、存取酒和记录。页头增加“设置”，进入同一路由的 task=settings 独立视图；位置、酒妹/保管员、存取酒模式及保存规则由独立 MerchantStorageSettingsPage 组合管理。
- 返回相应办理任务，保留根页面的已选顾客与查询条件。刷新 settings URL 仍停留设置视图。关闭产品时保持规则查询，不开放新配置写入和新增顾客入口。

## 模块与 API Review

- 顾客仍由统一 customers 模块拥有，未新建存酒专用顾客表或顾客 API。原 POST /api/v1/stores/{storeId}/tenant-admin/customers 及 customer-privacy-notice 原样复用，entrySource=tenant_admin，保留原租户管理员、门店范围、手机号唯一及同意审计规则。
- 新增顾客入口条件：产品新业务开启、auth.isTenantAdmin（含 tenant.admin.manage）、当前门店在登录授权范围。服务端权限完全不变，不向普通店员额外授予新增顾客权限。
- 存酒设置的保存权限仍由各原组件及服务端负责；位置/模式限租户管理员，保管员、规则按原权限控制。只读规则不因入口移动而隐藏。
- 无后端、DTO、数据库迁移、依赖、运行时或权限配置修改。

## TDD / 排错

- 先更新设置用例并补新增顾客、设置隔离用例，原实现 43 通过、4 失败。
- 引入已有顾客管理逻辑后，测试宿主缺少 document.documentElement，现有 i18n 初始化设置 lang 时失败；补齐模拟 DOM，再复测。顾客 API 使用 text() 解析，模拟响应同时提供 text/json。
- 原设置测试需先点击设置入口；返回按钮的箭头和新增按钮的加号纳入真实可访问文本。
- 增加未同意、重复点击、跨门店晚响应、手机号冲突恢复和设置深链接刷新/关闭产品历史用例。检查时发现初始化会覆盖 task=settings，已使门店初始化尊重白名单任务参数。

## 最终验证

- node --experimental-transform-types --test src/test/frontend/wine-storage-page.test.mjs src/test/frontend/wallet-customer-management.test.mjs src/test/frontend/wallet-marketing-points.test.mjs src/test/frontend/storage-merchant-scope.test.mjs src/test/frontend/storage-locations.test.mjs：158 项通过，无失败或跳过。
- npm run build：vue-tsc/Vite 通过。git diff --check 通过。
- 本地 127.0.0.1:5213 使用内存模拟响应，检查实际构建的中文 393px 首页/新增顾客/设置和约 322px 英文设置，无横向溢出；检查设置页刷新及返回办理入口。未连接生产或创建真实顾客。
- 截图及 verification.json：target/storage-customer-settings-20261027/。构建和测试日志：target/storage-customer-settings-build-final.log、target/storage-customer-settings-tests-final.log。
- 12 项原有无关文件与保留快照 SHA256 一致。

## UI / Code Review 与剩余风险

未发现未解决的 P0/P1。顾客组件属于共享模块且不依赖存酒专属样式；设置组合独立，主页不堆叠管理表单。创建按钮与后端管理员边界一致，原钱包顾客管理回归通过。

初始范围为本地实现；后续用户授权“提交部署”，前端功能提交 bff3ca78 已提交、推送并部署。服务端端点和数据行为未改，因此未重复跑数据库迁移/后端测试；真实后端浏览器创建及手机实机验收尚未执行。

## 后续生产发布

前端功能版本 bff3ca78，后端保持 d6816db5、Flyway 084|84|0，不重启后端或改变业务数据。详见 [生产发布记录](STORAGE_CUSTOMER_SETTINGS_PRODUCTION_RELEASE_20261027.md)。
