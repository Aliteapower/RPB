# 卡与权益页面生产发布 — 2026-10-27

- 项目：`D:\RPB`，分支 `codex/paynow-payment-product-line-staging`，远端 `Aliteapower/RPB`。
- 功能提交及前端版本：`a184c563b2fc82f13b9129b0751a79a0d234a3b0`，已推送。后续记录提交不改变功能制品 SHA。
- 服务器 `43.134.69.75`，服务 `rpb-backend`，数据库 `rpb_acceptance`。
- 后端版本保持 `11778be10b5be1ae9617fc09c6d16bf7c5e81ae2`；Flyway 保持 `085|85|0`。未重启服务、未执行迁移、未写业务数据、未修改环境配置。

## 构建与保全

仅 8 个本轮文件进入功能提交；其他 12 个未提交文件 SHA256 一致。制品从干净 Git archive 构建，2,892 个源码文件逐一与 Git blob 校验一致；`npm ci`、20 项定向前端测试、Vue 类型检查与 Vite 构建通过。

不可变制品：`/opt/rpb/releases/20261027-card-workspace-ui-a184c563`。
前端归档 SHA256：`40777882a751ae37efa240e39ceb7f5e8ce964ceb0227ac814e878ea0ba5dc6f`。
入口 SHA256：`c941d9161d6f5898b8132df2243fc02aeeca79ca3ec1510c724da0d67d78a760`。

## 备份与回退

备份目录：`/opt/rpb/backups/20261027-card-workspace-ui-a184c563-before`，包含发布前完整前端与 `FRONTEND_RELEASE_SHA`；备份内容与旧前端逐项一致。先复制新哈希资源，再原子切换 `index.html`，保留旧资源。发布脚本在切换后校验失败时恢复旧入口与前端标记。

回退时将备份的 `frontend/index.html` 安装至临时文件并原子移动到 `/opt/rpb/frontend/index.html`，恢复备份 `FRONTEND_RELEASE_SHA` 到 `/opt/rpb/app/FRONTEND_RELEASE_SHA`。不回滚数据库或后端；保留资源以兼容已打开页面。

## 线上验证

- 四个域名 `booking.yumstone.sg`、`platform.booking.yumstone.sg`、`20000000.booking.yumstone.sg`、`sx.booking.yumstone.sg` 的 32 个页面入口内容一致。
- 12 个相关公开资源逐字节匹配；全部 278 个发布文件与实际前端一致，8,238 个旧资源保留且内容一致。
- 24 项匿名接口检查均返回 401，涉及卡方案、持卡、开关、记录、客户及密码邮件设置。
- 153 项业务表/配置指纹前后及最终复查一致。指纹沿用现有发布口径，排除既有 QA 租户和会话、审计等易变内容，不宣称全库逐字节一致。
- `rpb-backend`、nginx、PostgreSQL、Redis、Docker 的状态、进程与 InvocationID 保持一致；环境配置哈希一致；发布观察期内无新增后端 ERROR。
- 后端 PID `2317868`，InvocationID `06f7f80738fb41ffbf41d7f4fb7b808f`。

Production readiness：GO，已部署。桌面及 390px 中英文视觉在演示预览验证；本轮未执行生产登录后的浏览器点击、实际领卡、核销、发信或微信实机相机验收。

证据：本地 `target/card-workspace-ui-release-20261027/`，服务器 `/home/ubuntu/card-workspace-ui-release-20261027/`。
