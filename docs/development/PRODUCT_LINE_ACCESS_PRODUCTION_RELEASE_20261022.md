# 产品入口与单店到期控制生产发布记录

日期：2026-10-22（Asia/Singapore）。用户已明确授权“提交部署”。

## Production Readiness Report

### Checklist

- 代码提交 `8a4565718040c606516c22817ac5779d52e7ac28`，已推送 `origin/codex/paynow-payment-product-line-staging`。范围为本轮修复、测试及需求/验证文档；私人文件 `rpb信息.txt` 保留且未提交。
- 同一生产代码状态的 18 类 102 项相关测试全部通过，0 失败、错误、跳过；包含真实本地 PostgreSQL、单店续费、隔离及 HTTP 授权测试。
- 从干净 managed worktree `C:\Users\ZhangXianLi\.codex\worktrees\rpb-product-access-release\RPB` 构建已提交代码。Maven package、npm ci、Vue 类型检查和生产构建通过；前端设置 `VITE_RPB_HOST_PREFIX_BASE_HOST=booking.yumstone.sg`。
- jar 包含共享产品入口 JSON、新授权类、全部 56 个历史迁移及 PostgreSQL Flyway 适配包；无新增/修改 migration、生产配置或权限回填。
- 发布前生产版本 `a5ce76700e89eda99b02cbcf851fe1d399b1ce01`，jar SHA-256 与旧发布记录一致。备份后以应用文件及入口页切换，保留旧哈希资源。
- 服务 `rpb-backend`；jar `/opt/rpb/app/reservation-platform.jar`；前端 `/opt/rpb/frontend`；版本标记 `/opt/rpb/app/RELEASE_SHA`。未操作其他项目或服务。

### Blocking Issues

无本次发布阻塞项。

### Non-blocking Issues

- 本次未执行全仓测试或浏览器人工交互验收；线上检查为 HTTP、静态资源、服务日志、数据库指纹和版本校验。
- npm ci 提示 7 项既有高危依赖告警。依赖锁文件与已发布基线一致，本次未自动升级依赖。
- 检查中发现 `pay.booking.yumstone.sg` 证书域名不匹配；本轮未变更 DNS、证书或 Nginx。支付接口通过已覆盖的根域验证，不能据此宣称 pay 子域证书已正常。

### Rollback Plan

备份 `/opt/rpb/backups/20261022-product-access-8a456571-before` 包含旧 jar、旧 RELEASE_SHA、完整 frontend、经 `pg_restore -l` 验证的 database.dump，以及发布前后业务指纹。

应用回退时恢复该目录的 jar、`frontend/index.html` 和 RELEASE_SHA，再重启 `rpb-backend` 并核对就绪及旧资源。新旧 assets 均保留，无须删除业务数据。数据库 dump 用于灾难恢复，不能覆盖发布后的合法写入。本次没有触发自动回滚。

### Decision

GO，已完成生产发布及下述验证。

## 发布结果

- 线上 RELEASE_SHA 与代码提交一致：`8a4565718040c606516c22817ac5779d52e7ac28`。
- jar SHA-256：`9e168b736876c7b993e340304f3b7f2364df26716e84bf722526497ecc0d8683`；本地、上传包和生产文件一致。
- 前端 tar SHA-256：`38756aade669026130e089a1bbed600a5f05108a08a68ee0f35f5d0f54c6c0d2`。
- `booking.yumstone.sg`、`platform.booking.yumstone.sg`、`sx.booking.yumstone.sg` 的登录页均 200 且与干净构建逐字节一致；134 个生产静态资源全部 200 且内容一致。三个域的匿名 `/api/v1/auth/me` 均 401。
- MainPID=`425373`，InvocationID=`fe3a1346caaf4b4a9676dfb66cd82ff5`，持续 active/running，NRestarts=0；新进程无 error 级日志。启动为 default profile，没有启用本地鉴权或导入 profile。
- Flyway 成功校验 56 项迁移，public schema 已为最新，无迁移执行。

## 数据及商业规则

- 发布切换后立即比对，39 张业务/配置表的数量和完整行指纹全部一致。
- 之后 HTTP 检查期间，最终比对中仅 `auth_accounts` 发生变化。对备份 COPY 数据与当前数据逐字段比较：13 个账号中 1 个账号的 `last_login_at`、`updated_at` 及 `version` 变化，版本递增 1，与 `AuthRepository.recordSuccessfulLogin` 一致；其他字段、账号集合不变。没有覆盖该合法登录状态。
- 其余 38 张表保持一致，覆盖账本、订位、排队、收款、门店、经营主体、订阅、订阅明细与事件、产品、价格、角色、权限和门店访问关系。
- 生产预检存在 1 条“订位产品的目标门店明细已到期、租户整体授权仍有效”的记录，新版本按本次授权需求实施单店到期限制。支付另有 1 条到期明细，但已由整体授权限制；未调整金额、期间或开通状态。
- 营业记录无“仅有旧私有 Logo 而缺公共 Logo”的记录，本次不执行品牌迁移或补数据。

## 旧产品接口检查

- lcd 公开 booking-entry 200，指定门店 booking/context 200。
- lsc106 租户 booking-entry 409（既有多门店选择语义），指定门店 booking/context 200。
- 根域支付 `/api/v1/stores/{storeId}/payments/intents/terminal-config` 匿名 403。
- sx 每日营业记录 settings 匿名 403。
- 没有进行真实支付、提交账目、续费、重置密码或修改租户资料。

## 证据

本地忽略目录：`target/product-access-public-verification.json`、`target/product-access-smoke-verification.json`、发布脚本及构建产物。服务器备份目录包含 `business-before.jsonl`、`business-after.jsonl`、`business-final.jsonl`、`preflight.jsonl` 及数据库备份。
