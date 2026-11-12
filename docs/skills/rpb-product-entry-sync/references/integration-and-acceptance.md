# RPB 集成位置与验收清单

从RPB根目录定位以下文件；实现可能演进，先核对现有代码，不把本清单当作固定API或迁移规格。

## 拥有者与复用位置

| 关注点 | 当前入口/拥有者 |
| --- | --- |
| 产品定义与入口注册 | `src/main/resources/product-line-entries.json`、`ProductLineEntryRegistry` |
| 商业/身份/门店授权 | `src/main/java/com/rpb/reservation/appgate/application/AppGateService.java`，相关产品Access服务 |
| 员工产品目录 | `src/api/meAppsApi.ts`、`src/types/meApps.ts`、`src/composables/useStoreVisibleApps.ts` |
| 管理员导航 | `src/components/tenant-admin/TenantAdminNav.vue`；保留预约/PayNow等现有分组，不生成重复主入口 |
| 员工首页/底部导航 | `src/pages/StoreStaffHomePage.vue`、`src/components/staff/StaffBottomNav.vue`及`staffBottomNavItems.ts` |
| 生命周期刷新 | `src/composables/useVisiblePageRefresh.ts`；业务busy/请求序号仍由调用者管理 |
| 员工钱包跨产品入口 | `src/modules/wallet/components/WalletCashierTools.vue` |
| 顾客钱包与酒水入口 | `src/modules/wallet/useCustomerWallet.ts`、`src/modules/wallet/pages/WalletCustomerHome.vue`、`src/modules/wine-storage/WalletStorageSummary.vue` |
| 顾客酒水页 | `src/modules/wine-storage/customer/useCustomerStorage.ts`；`/home`的newBusinessEnabled与顾客自己的记录 |
| 历史导航与提示 | `src/modules/tenant-products/ProductHistoryLinks.vue`、`ProductHistoryNotice.vue`、`locales.ts` |
| 钱包只读链路 | `src/modules/wallet/components/WalletBottomNav.vue`、`WalletPointsReversalAction.vue`、`src/modules/wallet/transactions/query.ts` |
| 员工顶栏窄屏 | `src/components/staff-home/StaffHomeTopBar.vue`；排查负边距、nowrap和固定宽度，不全局overflow:hidden掩盖问题 |

顾客钱包与酒水接口分别为 `/api/v1/public/stores/{storeId}/wallet/me`、`/api/v1/public/stores/{storeId}/wine-storage/home`。普通员工API和顾客API不能互换。以后新增产品按其实际拥有者补齐映射，不能只在注册目录加一项就宣称全端接入。

## 必要行为矩阵

为管理员、普通员工（含权限受限账号）、顾客分别填写“通过/失败/未验证/不适用”，给出证据位置和原因。不要默认每个产品三端都有入口。

| 用例 | 实际断言 |
| --- | --- |
| 初始未开通 | 无越权业务入口；既有历史资格由后端决定；顾客酒水无业务且无自身记录时隐藏 |
| 同店开通 | 保持页面不重载、不点击刷新，等待既有轮询或事件，业务入口出现 |
| 自然到期 | 仅合成订阅设置未来到期并等待；入口自动移除，后端新业务立即拒绝，允许的历史仍可读 |
| 手动停用 | 同店自动同步；兄弟店不受影响；不得修改真实订阅/开关 |
| 续期/恢复 | 自动恢复允许入口；另行手动关闭的开关不被自动打开 |
| 切店与竞态 | A有业务/B只有历史或不同产品；实际切到B，无A链接；人为延迟A响应，B→A→B后仍只有B范围 |
| 身份变化 | 员工/顾客会话分离；登出/换顾客/权限撤销/切角色使旧结果失效，不沿用旧顾客数据 |
| 无权范围 | 未授权同租户门店、外租户及权限不足直达URL均受后端限制；历史读取同样校验身份和归属 |
| 只读链路 | 实际点击底栏、今天、筛选、分页、详情及返回；显式历史标记不丢失，不显示新增/冲正入口 |
| 有效产品的历史视图 | 仍然只读，文案不错误宣称关闭；正常业务和顾客既有路径不被改写 |
| 请求生命周期 | 慢响应不叠加同范围请求；失败清空相关入口；隐藏页不轮询，返回立即刷新，卸载后无更新 |
| 窄屏/语言 | 桌面、约320/390实际CSS宽度；中文/英文，导航、门店选择、历史链接可操作，无整页溢出/遮挡 |

只读页面没有写按钮不能替代API拒绝测试。测试拒绝路径时使用正确的身份、Content-Type及模块模型头，避免把CSRF/模型头错误的403当作订阅到期403；核对实际错误码和业务表指纹。不得为“测试支付”执行真实资金动作。

空交易列表只能证明列表/入口可读；没有实际详情行时，详情/冲正声明为组件测试覆盖，不冒充浏览器点击完成。顾客没有门店选择器时使用现有门店网址或入口切换，报告其实际方式，不临时新增选择器。

## 合成环境保护

1. 确认 `target/local-postgres-current.txt` 属于当前RPB工作区，服务监听及schema匹配。连接方式：`jdbc:postgresql://127.0.0.1:<pointer port>/postgres?stringtype=unspecified`，postgres空密码。失效时查 `docs/development/LOCAL_RUNTIME_QUICK_RESTART_GUIDE.md`。
2. 复用现有合成夹具；需要变化时克隆至本轮自有schema，启动回环地址的独立服务/代理，保留其他验收进程。先查脚本的参数、副作用和导入时执行代码，避免误触旧phase/seed。
3. 至少区分A/B门店、未授权同租户门店、外租户、受限员工及独立顾客。新增QA会话、空配置或历史样例仅在隔离环境；不得复制真实cookie、密码或顾客资料。会话/token只放私有target，不进入技能、日志或Git。
4. 夹具初始化后记录保护表的行数及内容摘要；清楚列出允许变化的合成订阅/门店开关。验收前后比较钱包、酒水、工资、付款和商业事件等相关表，不把夹具初始化本身谎报为“零数据库变更”。
5. 线上只能在当前授权下做只读核查。端口、schema、测试数量、版本、进程及SHA均本轮核实；不固定沿用2026-10-31的值。

## 测试与浏览器证据

优先复用 `src/test/frontend/` 中的 `product-entry-visibility`、`product-history-surfaces`、`wine-storage-page`、`wallet-marketing-points`、`wallet-transactions`、`customer-access-navigation`、`wallet-navigation`、`tenant-products-page`、`management-navigation` 等实际相关测试。后端参考 `StoreProductAccessPostgresTest`、`MeAppsControllerTest`及产品自己的归属/写入测试。

当前Node 22环境涉及TS参数属性时，既有测试可用：

```powershell
node --experimental-transform-types --test src/test/frontend/product-entry-visibility.test.mjs src/test/frontend/product-history-surfaces.test.mjs src/test/frontend/wine-storage-page.test.mjs src/test/frontend/wallet-marketing-points.test.mjs src/test/frontend/wallet-transactions.test.mjs
```

先核对当前Node和项目工具链；`--experimental-strip-types`并不支持所有既有语法。行为修复保留失败→通过证据，检查退出码；按受影响范围运行类型/构建。代码、配置与环境不变的有效结果可以复用，不固定要求重跑全部历史测试。

浏览器证据记录：角色、入口/路由、夹具phase与时间、实际DOM链接/只读提示、实际CSS宽度、clientWidth/scrollWidth、截图路径和未验证项。自然到期自动同步不能靠重载来证明；轮询日志不等于精确DOM首次变化时间。

视口能力可能绑定旧标签，用户缩放也会改变CSS宽度。对当前验收标签取得对应能力并现场测量，不能硬编码某次213或260的工具输入。文件名写320但实测390时按390记录并补测；不能裁剪桌面截图充当窄屏。截图应对应最终代码；结束前恢复临时视口（工具安全中止后不继续界面操作）。

浏览器截图失败先核对绑定与工具文档，不反复盲试或绕过网址安全检查。工具要求停止时停止界面操作；已授权的非界面验证可继续，但报告截图/交互缺口。DOM通过、截图完成、线上交互、实体手机和微信是不同证据层级。

## 发布边界

本技能不默认提交或部署。若当前会话已授权发布，遵循项目production-readiness和release-note；保留已有无关改动，用干净候选构建并证明范围内源码/制品与提交一致。仅前端变更不顺带发后端或迁移；核对实际依赖后选回退范围，不能因历史案例前端可回退就假定新迁移也安全。

前端发布核验新index、受影响JS/CSS（组件CSS可能抽到名称不同的共享chunk）、匿名/越权拒绝及当前版本。保留旧不可变资产，避免旧页面失去依赖。需要首刷取得新JS与随后业务状态自动同步是两件事。资源哈希通过不等于登录浏览器通过。

## 已有经验依据

需要追查原始缺陷或验收边界时再读以下项目文档；它们不是下一轮运行环境或发布授权：

- `docs/development/PRODUCT_NAVIGATION_SYNC_FIX_20261031.md`：遗漏产品入口、同店停留不刷新、动态注册及共享刷新。
- `docs/development/PRODUCT_NAVIGATION_BROWSER_ACCEPTANCE_20261031.md`：自然到期、切店迟到响应、历史标记丢失和恢复后错误文案。
- `docs/development/PRODUCT_ENTRY_STAFF_CUSTOMER_SYNC_20261031.md`：顾客酒水单次watch、身份隔离、员工负边距及最终截图缺口。
