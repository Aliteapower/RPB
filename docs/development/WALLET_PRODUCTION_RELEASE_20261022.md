# 钱包产品线生产发布记录

日期：2026-10-22（Asia/Singapore）。工作区 `D:\RPB`。用户明确授权提交部署钱包，并要求保留其他改动、验证其他产品线。

## Production Readiness Report

### Checklist

- `wallet` 为唯一 App Key；V057 注册平台产品目录、默认入口 `/stores/:storeId/wallet` 及月/年价格。现有平台产品管理、价格抽屉和租户计费页读取统一目录，无需另建平台后台。
- 默认价格禁用，不自动新增真实租户订阅、门店授权、顾客账户、余额或收款资料。未接入线上支付或银行回调。
- 203 项相关后端测试通过，含钱包 38 项、真实 PostgreSQL 25 项；前端重试 2 项、类型和生产构建通过。同代码状态的结果复用，证据见本地验收记录。
- V057 已在隔离 PostgreSQL 完整运行，历史 56 项迁移未修改；新增表、复合外键、资金分录只追加约束、Customer 资金保护和职责权限回填经过审查。
- 幂等、审计、稳定错误码、客户/员工认证隔离、同源写入、精确单店到期及原单善后已有测试。租户/门店/顾客资料沿用公共服务。
- 生产预检：`43.134.69.75` 的 `rpb-backend` 正常运行 `8a4565718040c606516c22817ac5779d52e7ac28`；数据库 `rpb_acceptance` 为 V056，无钱包目录、价格或业务表，13 个统一 Customer。磁盘剩余约 20 GB。
- 发布仅切换 `/opt/rpb/app/reservation-platform.jar`、`/opt/rpb/frontend` 的新资源/入口及版本标记；不修改生产配置、DNS、证书或其他服务。前端使用 `VITE_RPB_HOST_PREFIX_BASE_HOST=booking.yumstone.sg`。
- 从提交的 Git archive 在独立构建目录打包，排除本地 fixture、会话及原有私人文件。发布前完整备份并保存旧业务指纹；发布后核对迁移、版本、服务、静态资源和旧产品 HTTP。

### Blocking Issues

当前预检未发现阻塞项。生产切换须完成制品校验及可读取的备份，并按下述检查确认结果。

### Non-blocking Issues

- 7 项 npm 高危依赖告警来自原依赖基线；新增 `jsqr` 无已知审计告警。本轮不扩大为依赖升级。
- 实体手机摄像头、真实银行到账和外部 POS 为现场试点补验；自动化及浏览器测试使用隔离虚构数据。
- 旧验收记录中的其他产品历史缺口不属于钱包变更，不能据此宣称全产品无缺陷；本轮确认变更不破坏其既有业务与授权行为。
- 钱包初始价格禁用，平台需另行确认价格及所选门店订阅后才能开展真实交易。本次不代为设置价格或开通业务。

### Rollback Plan

发布前保存旧 jar、RELEASE_SHA、完整 frontend 和 `pg_restore -l` 可读取的 PostgreSQL dump。保留旧前端哈希资源。

应用启动失败且确认钱包尚无账户/单据/资金时，可恢复备份 jar、frontend/index.html 和版本标记，重启 `rpb-backend`，再验证 401 身份接口、旧产品与静态资源；保留 V057，不执行删表或逆向迁移。若钱包已经产生资金，先限制新交易并保留本人查询/原单核实/退款能力，采用向前修复；不能直接回退到无钱包善后能力的旧应用。

完整数据库备份用于受控灾难恢复，不能覆盖发布后的合法业务写入。

### Decision

GO：允许按已授权范围提交、构建、备份并发布。实际发布及验证结果见后续记录。

## 生产结果

- 16:42（Asia/Singapore）发布完成；代码与线上 RELEASE_SHA 均为 `e839ec4e0f32f1523d4a51a3aed44d4dc9e3382b`，已推送 `origin/codex/paynow-payment-product-line-staging`。
- jar SHA-256：`5675dfc396f1bae62cd34a80c6d887663b86668e9c7aeb894a34592321ac345a`；前端 tar SHA-256：`8a594fedc3b2504e2d6b13638f8b8f164727352587123c8b79969a83a452e6ca`。本地制品、上传文件、线上 jar 均校验一致。
- 已提交 Git archive 的 2082 个源文件逐字节校验；独立目录重新执行 Maven package、npm ci、Vue 类型检查、Vite 生产构建及前端 2 项重试测试成功。Windows tar 对两个中文文档路径报错后，用 Python 按 archive 原字节补齐，并核实其余源文件一致；未更改产品代码或测试基线。
- Flyway 校验 57 项，仅执行新增 V057，用时约 0.55 秒；旧 56 项迁移记录内容不变。启动使用 default profile，没有启用本地鉴权或导入 profile。
- `rpb-backend` MainPID=`481084`，InvocationID=`d408bebaf65c4c878414cb2bbee95ece`，多次核对保持 active/running、NRestarts=0；新进程无 error 级日志。
- `booking.yumstone.sg`、`platform.booking.yumstone.sg`、`sx.booking.yumstone.sg` 登录页均 200 且与构建入口逐字节一致；138 个新构建静态资源全部 200、内容一致，旧资源保留。

## 平台后台与真实业务边界

实际 Chrome 的已登录平台页面 `/platform/settings/product-lines` 刷新后显示四条产品线，包含“储值钱包”、App Key `wallet`、启用状态及默认入口 `/stores/:storeId/wallet`。打开钱包编辑抽屉只读核对：入口选项为钱包，币种 SGD，月/年价格均为 0 且状态停用；关闭抽屉，没有保存设置或定价。

生产核对：钱包订阅、租户授权、门店开关、账户、分录、充值单和消费单均为 0。迁移只新增钱包职责权限：10 个账号获得查看/建单权限，8 个管理员/经理获得核实收款、退款、对账和设置权限；平台管理员钱包资金权限为 0。商业开通仍须有效明确单店订阅，权限回填不会直接放开新交易。

没有新增真实顾客、测试登录身份或资金，没有替商户配置 PayNow，没有执行充值、消费、退款、订阅购买或续费。

## 其他产品线及统一资料核验

- 发布前、切换后及浏览器检查结束，再次比较 **78 张既有表**的完整行数量与指纹，全部一致。包括租户、经营主体、门店、Customer/Auth 身份、支付配置/单据/回单、订位、排队、入座/清台、营业记录及快照、旧订阅/明细/事件、旧产品及价格。
- 指纹比较对产品目录/价格排除新增 `wallet` 行，对权限排除新增 `wallet.*`，对迁移记录排除 V057；会话、验证码和访问审计等运行时表不作业务不变断言。没有排除或修改旧产品业务字段。
- 8 项旧接口发布前后状态和响应内容逐字节一致：lcd 租户预约入口 200、lsc106 多店入口 409、lcd/lsc106/lsc83 指定店预约 context 各 200、PayNow terminal-config 匿名 403、营业记录设置匿名 403、auth/me 匿名 401。钱包顾客 me 匿名 401。
- 既有认证平台会话刷新后可继续读取产品目录和钱包设置。资金闭环、不同主体/租户拒绝、单店到期和兄弟店不互相续期继续复用同代码状态的 PostgreSQL/本地真实浏览器证据；没有通过修改生产订阅或余额来制造验收条件。
- 上述证据未发现钱包发布导致的旧产品回归；不等同于重新执行所有生产人工流程或修复旧报告中的历史缺口。

## 备份、回滚与保留内容

备份目录 `/opt/rpb/backups/20261022-wallet-e839ec4e-before`：旧 jar、旧 RELEASE_SHA、完整 frontend、完整 `database.dump`（`pg_restore -l` 成功）、`business-before.jsonl`、`business-after.jsonl`、`business-final.jsonl`、`preflight.jsonl`、`wallet-after.jsonl`。新前端另存于 `/opt/rpb/releases/20261022-wallet-e839ec4e`。

当前钱包业务为空。如需应用回退，按上文回滚计划恢复该备份 jar、frontend/index.html 和 RELEASE_SHA，重启并核对旧产品；保留 V057，不覆盖或删除数据库。没有触发自动回滚。

本地原有 `PRODUCT_LINE_ACCESS_BROWSER_ACCEPTANCE_20261022.md` 和私人信息文件保留原样，未提交、未上传；已核对 SHA-256 与发布前一致。钱包原需求文档按原内容纳入本次钱包提交。没有清理其他工作区、分支或本地服务。

## 验证证据

本地忽略目录 `target/`：`wallet-production-deploy.log`、`wallet-production-service-sanitized.log`、`wallet-production-assets.json`、`wallet-public-before.json` / `wallet-public-after.json`、`wallet-production-catalog.jpg`、`wallet-production-catalog-detail.jpg`、`wallet-production-settings.jpg`，以及独立构建和发布脚本。

最终结论：**GO，已完成提交、推送、生产发布及只读验证。** 商业价格和真实租户开通未代为决定；现场实体扫码及线下银行/POS 核实仍需试点验收。
