# 租户常用入口与名称二维码生产发布

项目：`D:\RPB`，2026-10-23，Asia/Singapore。用户明确授权“提交部署，保留其他改动，再用20000000线上验收各入口和租户名称二维码，确认其他产品线不受影响”。

## Production Readiness Report

### Checklist

- 本轮只发布统一租户名称、按门店生成的常用 URL/二维码、只读身份与产品状态接口，以及员工/管理员登录类别提示；无财务、订阅写入，无迁移、依赖或运行配置变更。
- 同一业务代码已有核心后端 24/24、前端 46/46、构建及完整 PNG 14 张解码通过的证据；相关修改后已重跑受影响用例。扩展后端 59/60，唯一失败为本轮未改动的旧 App.vue 路由静态断言，原因及切店行为证据见实现报告。
- API 会话身份 + tenant.admin.manage + 授权门店，查询同时限定 tenant_id/store_id；复用 AppGate 商业状态和单店到期。未开通入口不可分发，URL 不含凭据或付款授权。
- 已核对生产旧版本 `1658f721d233e7b1ed414c88168533f4a9623812`、jar SHA256 `b9b352f1529caa7747d7375768045e4bbb6306697bb31c9bfdbd14366591532c`；rpb-backend active/running，NRestarts=0，V057/57 成功/0 失败，约 19GB 可用。
- 只提交本轮明确文件；保留既有未跟踪验收文档和私人资料，使用哈希确认不变。生产制品从提交的独立干净归档构建，不包含本地会话或验收夹具。
- 发布前备份旧 jar、完整前端、版本标记、数据库 dump 并验证可读取，记录业务/配置表指纹、公开接口及相邻服务身份。只重启 rpb-backend，不变更 nginx、证书、数据库配置或其他服务。
- 就绪检测使用既有真实认证 `/api/v1/auth/me` 的匿名 401 与 systemd 稳定进程；随后验证线上静态资源、匿名权限边界及已登录测试租户浏览器页面。

### Blocking Issues

无已知本轮代码阻塞。干净构建、备份校验、运行版本及浏览器验收必须完成，才能宣称发布与验收成功。

### Non-blocking Issues

- 既有路由静态测试仍需单独维护，本轮不更改已上线的钱包页面生命周期。
- 既有 npm 依赖审计告警不属于本轮依赖升级范围，干净安装结果另行记录。
- 真机镜头/纸张/光照识别不以浏览器像素解码代替；本轮线上检查入口、生成、下载及解码，不发起充值、付款或其他真实业务操作。

### Rollback Plan

若就绪失败或新进程异常，恢复本次备份的 jar、index.html 和 RELEASE_SHA，重启 rpb-backend，保留旧静态资源和业务数据库。无新迁移，不执行数据库降级；业务表变化先只读核查，不覆盖正常并发业务。数据库备份仅供受控灾难恢复。

### Decision

GO：按用户明确授权执行定向提交、推送、干净构建、备份、部署及只读线上验收。发布完成后补记版本、制品、服务与验收证据。

## Release Notes

- New：租户后台“常用入口与二维码”，7 类入口，统一租户名称二维码，可复制、打开、下载 PNG。
- Changed：门店范围和产品开通/到期状态决定是否可分发；员工和管理员链接预选已有登录类别；合法租户提示优先于记住账号。
- Migration / Permission：无迁移、无新权限、无线上配置变更，复用租户管理员范围与 AppGate。
- Risk：静态二维码改名后需重新下载；访问始终受现有产品、登录、权限和单店到期控制。超长名称中心可能省略，完整名称保留外侧。
- Rollback：整体回退前后端制品，不回退数据库或覆盖其他改动。

## 实际发布结果

2026-10-23 约 11:05（Asia/Singapore）完成前后端切换。已提交、推送并部署运行代码 `c2a82659f9dadc7d23dc17f579dfc8b9ef2eb770`，分支 `codex/paynow-payment-product-line-staging`。后续仅补记本文档，不改变运行代码版本。

### 制品、备份与服务

- 干净 Git 归档 2,157 个源文件与提交 blob 逐一匹配，排除私人资料和本地夹具；独立目录 Maven package、npm ci、Vue 类型检查、Vite 构建及 46 项前端测试通过。后端核心 24 项复用同一业务代码的当前测试证据；扩展回归的旧路由静态断言失败仍保留说明。
- npm ci 仍报告既有 7 项高危告警，未变更依赖及锁文件，未扩展为依赖升级。
- jar SHA256：`69581e6bfc94892851cd68ac3a241e6a715e0df58e885ec5a7791f14d39cbf7c`。
- 前端包 SHA256：`96907422fd1ebd0bfe5899d896f32c8d9625a48f12bd2f412e5b00ae8c0f6073`。上传包与生产制品校验一致。
- 备份 `/opt/rpb/backups/20261023-tenant-entry-c2a82659-before` 保存旧 jar、完整前端、RELEASE_SHA、数据库 dump 及业务指纹。数据库 dump 为 797,341 字节，`pg_restore -l` 可读取。新静态资源发布目录 `/opt/rpb/releases/20261023-tenant-entry-c2a82659`，旧哈希资源保留，未触发回滚。
- rpb-backend active/running，MainPID `746271`，InvocationID `673a46b901c04d3a808baec025c0f44d`，NRestarts=0。就绪后与浏览器验收结束进程一致，新进程 ERROR 日志为 0。
- default profile；Flyway 57 项校验成功，public 保持 V057，无迁移执行。生产环境文件哈希不变，nginx 配置、nginx/PostgreSQL/Redis/Docker 的 PID、InvocationID、运行状态均与发布前一致。
- `booking.yumstone.sg`、`platform.booking.yumstone.sg`、`20000000.booking.yumstone.sg`、`sx.booking.yumstone.sg` 四个站点 HTML 与本次干净构建完全一致；172 个静态资源全部 HTTP 200 且逐字节一致。
- 两个门店的新入口接口匿名请求均为 401 UNAUTHENTICATED，未泄露统一资料。

### 测试租户 20000000 浏览器验收

通过 Chrome 的既有会话只读核验。统一平台租户名为 **食刻租户**，门店为 **RPB 验收门店**（storeId `20000000-0000-0000-0000-000000000983`），与发布前只读数据库查询一致；未把门店名当成租户名。

| 入口 | 结果 |
|---|---|
| 顾客钱包 | 打开本门店“我的钱包”，显示同一发行主体与已有余额；未出码或付款 |
| 在线预约 | 打开本门店预约页，日期、午晚餐时段可读取；未选择提交预约 |
| 员工工作台 | 登录页预选“员工”，URL 固定本租户和门店；用现有会话访问目标工作台，概览完成加载，钱包、账本、PayNow、预约排队及桌台入口可见 |
| 钱包收银台 | 登录页预选“员工”；用现有会话访问目标，充值、扫码收款、账单入口正常；未充值或建单 |
| PayNow 收银 | 登录页预选“员工”；用现有会话访问目标，原终端及营业日设置正常读取，金额 0、生成按钮禁用；未生成收款码或开闭营业日 |
| 每日营业记录 | 登录页预选“员工”；用现有会话访问目标，盘点表加载，显示“尚未暂存 · 第 0 版”；未输入、保存或提交 |
| 租户管理后台 | 登录页预选“租户”；用现有会话访问目标资料表单正常，未保存任何设置 |

五个登录入口只检查类别、域名及固定 redirect，没有重复提交登录或操作滑块。目标页面使用既有已授权会话只读核验，不把它描述为新员工登录流程已完整提交。

- 7 类入口在当前门店均为可使用。7 张线上实际生成的 PNG 已完整分块保存并独立解码，结果逐一等于卡片 URL，域名全部为 `https://20000000.booking.yumstone.sg`，门店路径一致。
- 人工查看 PNG 像素确认中央显示“食刻租户”，外侧完整显示租户、RPB 验收门店、用途及 URL；不是仅验证图片 alt 文本。名称取统一资料，无二维码专用资料副本。
- 复制按钮显示“网址已复制”。Chrome 工具的剪贴板读取返回空字符串，未据此宣称系统剪贴板粘贴验证通过。
- 点击 PNG 下载按钮以及调用浏览器下载接口均未收到下载事件；已核对链接、文件名、完整导出 PNG 与像素解码，不把浏览器系统下载事件计为通过。真实手机镜头、打印及光照仍属现场抽验范围。
- 新入口页面未见控制台错误；未执行任何充值、扣款、补付、退款、预约、账本或资料保存操作。

### 其他产品线与数据保护

- 9 项发布前后公开只读检查语义完全一致：lcd/lsc106 预约入口及 lcd/lsc106/lsc83 门店预约上下文、PayNow/账本匿名边界、员工及顾客钱包身份边界。
- 92 张业务与配置表在切换前、切换后、浏览器验收结束三个快照的行数和指纹全部一致，包含统一租户/顾客、平台计费/订阅、预约排队/桌台、PayNow、每日账本、钱包账户/充值/账单/流水。
- 身份会话、验证码、AppGate 拒绝审计和通用审计表排除出严格业务指纹；auth_accounts 只排除登录时间、更新时间及版本，其余字段比较。只读验收允许身份会话正常续期，不覆盖任何业务数据。
- 原有未跟踪 `PRODUCT_LINE_ACCESS_BROWSER_ACCEPTANCE_20261022.md` 和 `rpb信息.txt` 保留，SHA256 与开始时一致，未纳入提交或制品。未修改私人资料内容。

### 证据索引

- `target/tenant-entry-release-source-proof.json`、`tenant-entry-release-artifacts.json`
- `target/tenant-entry-clean-package.log`、`tenant-entry-clean-build.log`、`tenant-entry-clean-front-tests.log`
- `target/tenant-entry-release-deploy.log`、`tenant-entry-release-assets-proof.json`、`tenant-entry-release-final-service.log`
- `target/tenant-entry-public-before.json`、`tenant-entry-public-after.json`、`tenant-entry-neighbors-before.log`、`tenant-entry-neighbors-after.log`
- `target/tenant-entry-prod-evidence/exports.json`、`decoded.json`：7 张线上名称二维码及完整解码结果。
- `target/tenant-entry-prod-evidence/entry-navigation.json`、`workspaces.json`：7 类入口及 5 个目标后台页面的只读核验。
- `target/tenant-entry-prod-evidence/business-proof.json`、`tenant-entry-business-{before,after,final}.jsonl`：92 表一致证据。
- `target/tenant-entry-prod-evidence/entry-links-production.png`：线上入口页截图；同目录 PNG 为线上实际二维码。

发布成功，已验证范围内未发现其他产品线回归。上述系统下载与真实手机硬件验收限制保留，不计为已通过。
