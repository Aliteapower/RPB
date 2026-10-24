# 租户常用入口与名称二维码：实现与验收记录

日期：2026-10-23。项目：`D:\RPB`。本轮为本地实现，未提交、推送或部署。

## 已实现

- 租户后台一级导航增加“常用入口与二维码”，按当前授权门店展示。
- 顾客钱包、在线预约、员工工作台、钱包收银台、PayNow 收银、每日营业记录、租户管理后台共 7 类固定入口。
- 每个可用入口提供 URL、复制、打开和名称二维码 PNG 导出；未开通、停用或到期产品显示原因，不提供分发操作。
- 二维码中央取统一 `tenants.display_name`，与门店名分开读取；完整租户名、门店及用途、URL 保留在图片外侧。超长名称中央缩小、换行并在必要时省略，外侧完整保留。
- 使用高容错与四模块静区；叠字后的 QR 和最终整张导出图都必须解码回同一个 URL，未通过时禁用下载。
- 切店、刷新和名称变化清除旧二维码；过期异步响应不会覆盖新门店资料。中英文界面、桌面多列及手机单列布局。
- 员工和管理员入口预选对应登录类别，保留固定门店目标路径；租户子域优先，本地/旧主域的租户提示优先于记住的其他租户账号。原登录缺省行为保留。

操作：租户管理员进入后台 → 常用入口与二维码 → 选门店 → 复制网址或下载 PNG，可保存或打印。

## 模块与 API

- 后端 `tenantadmin`：Controller 只处理 HTTP 与现有权限范围；Service 编排商业开通状态和预约设置；Repository 只按 tenant_id + store_id 查询统一身份。新增响应模型独立承载分发所需资料。
- 前端 `src/modules/tenant-entry-links`：入口目录、查询状态、名称排版、二维码渲染、卡片、页面及双语资源分开。`src/utils/loginEntry.ts` 仅负责已有登录类别和租户提示选择。
- 只读 GET `/api/v1/stores/{storeId}/tenant-admin/entry-links`。复用 `TenantAdminScopeResolver`：tenant_admin、tenant.admin.manage、授权门店，租户身份从会话取得。忽略伪造租户请求头；不允许普通员工或平台身份代读。
- 复用 `AppGate.evaluateCommercial` 与预约配置，覆盖平台、租户、门店开通及单店到期。管理资料读取不会开通产品或赋予产品使用权。
- 无数据库迁移、依赖升级、权限新增或配置变更；无资料复制、计费、订阅或资金写入。原预约、排队、Walk-in、落座、清台和钱包支付流程未改。
- API 约定见 `docs/api/2026-10-23-tenant-entry-links.md`。

## 验证结果

| 检查 | 结果 |
|---|---|
| 新增后端测试 | 8 / 8：身份来源、商业禁用/到期、预约开关、匿名/员工/平台/门店权限、伪造头、只读方法、稳定错误、PostgreSQL 跨租户及删除数据 |
| 最终后端核心与登录回归 | 24 / 24；命令及日志见下 |
| 前端目录、异步状态、名称排版与域名测试 | 10 / 10，含 9 项新增用例和既有 hostContext 测试 |
| 钱包等相关前端回归 | 46 / 46，最终修改后重跑受影响的 10 项仍通过 |
| 扩展后端回归 | 59 / 60；1 项既有静态路由断言过时，见下，不计为通过 |
| `npm run build` | vue-tsc 与 Vite 构建通过 |
| `mvn -q -DskipTests package` | 通过；新增后端业务代码随后无行为变更 |
| 完整 PNG 独立解码 | 页面实际生成 4 张 + 全部 7 类入口及中英文/126 字符长名/Unicode 边界 10 张，共 14 张均与对应 URL 一致 |
| 本地浏览器 | 管理员导航、统一租户名与不同门店名、未开通原因、复制成功反馈、PNG 导出链接、员工/管理员预选类别和租户代码、中英文、390px 窄屏无横向溢出 |
| 保留已有内容 | 原有验收报告和 `rpb信息.txt` 的 SHA256 与本轮开始时一致，未读取私人资料内容 |

核心命令：

```powershell
mvn -q '-Dtest=TenantEntryLinksTest,TenantEntryLinksSecurityTest,TenantEntryLinksPostgresTest,AuthLoginUiValidationTest' test
node --experimental-transform-types --test src/test/frontend/tenant-entry-*.test.mjs src/test/frontend/wallet-*.test.mjs src/utils/hostContext.test.mjs
npm run build
```

本地 PostgreSQL 使用 `target/local-postgres-current.txt` 指向的 61644 端口；PostgreSQL 测试创建独立临时 schema 并应用 57 个既有迁移。浏览器后端使用已有隔离的 `wallet_ui_validation_20261022`，仅查询本地虚拟租户，未变更真实业务数据，未接触生产服务。

证据（本机生成，位于忽略的 target 目录）：

- `target/tenant-entry-links-backend-final.log`，`target/tenant-entry-links-regression.log`
- `target/tenant-entry-links-front-final.log`，`target/tenant-entry-links-front-regression.log`
- `target/tenant-entry-links-build-final.log`，`target/tenant-entry-links-package.log`
- `target/tenant-entry-links-evidence/decoded.json`，`boundary-decoded.json`：完整 PNG 独立解码结果。
- `target/tenant-entry-links-evidence/entry-links-desktop.png`，`entry-links-mobile.png`：本地界面截图。

## 验收中的定位与处理

1. 新页面右上角刷新按钮曾被全局语言切换浮层遮住，已为刷新按钮预留位置；手机布局保持独立换行。
2. 本地/旧主域入口的租户提示可能被记住账号覆盖，已改为合法入口提示优先，租户子域仍固定实际租户。
3. 初次离线解码的 126 字符长名图片失败，定位为浏览器工具读取 data URL 时截断到 200000 字符。改为完整分块保存后 PNG 解码通过；浏览器内 PNG 回读同样通过。代码增加了最终整图校验，不把工具截断误报写成产品缺陷。
4. `TenantAdminStoreSwitchRouteRefreshUiValidationTest` 仍断言 App.vue 必须包含 `<RouterView :key="$route.path" />`，当前既有钱包版本已使用 `routeViewKey($route)`。本轮未改 App.vue、viewKey.ts 或该测试；新页面的切店隔离由新增状态测试、真实 PostgreSQL 两门店与路由元信息共同覆盖。未为消除旧断言而改动钱包工作区生命周期。

## Code Review Report

### Scope

限定为入口分发和登录类别提示，未扩大到账务、租户资料写入、产品开通或权限模型。

### P0 - Must Fix

无发现。

### P1 - Should Fix

本轮变更无未解决项。既有路由静态测试不符当前基线，单独记录，未声称全量回归通过。

### P2 - Improvement

公开分发用的二维码需在实际生产域名页面下载；本地二维码使用 localhost 仅供本机验收。纸张尺寸、光照和真实手机摄像头识别仍应在发布后现场抽验。IAB 工具未回报 data URL 下载事件，已核对下载链接、文件名及完整 PNG 内容，不把系统下载事件计为通过。

### Test Coverage / Final Decision

匿名/权限、跨租户/跨店、产品到期、错误映射、只读性、异步竞态、地址构造、排版与 PNG 像素解码已有证据。APPROVE_WITH_COMMENTS：可交付本地实现，保留上述既有测试和真实手机待验说明；不等同于发布授权。

## Release Notes

- Version / Date：本地工作区实现 / 2026-10-23，未发布。
- New：租户常用入口目录、统一租户名称二维码与 PNG 导出。
- Changed：员工/管理员入口可通过 URL 预选已有登录类别；合法租户提示优先于记住账号。
- Fixed：新页面刷新按钮避免语言浮层遮挡。
- Migration / Permission：无迁移，无新权限；复用租户管理员范围和 AppGate。
- Risk：已导出的静态二维码不实时更新显示名称或撤回；访问时仍须接受现有登录、权限和到期检查。改租户名称后应重新下载，长名中部可能省略，外侧完整保留。
- Rollback Notes：未来若发布后需回退，恢复本轮前前后端构建并重新启动原服务；无新表或财务写入需要回滚，不执行数据库降级。当前未发布，不需生产回退。
