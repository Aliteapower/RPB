# 钱包办理流程与统一顾客入口生产发布

2026-10-24，D:\RPB；用户明确授权“提交部署”。

## 范围与就绪检查

本轮发布三部分未提交改动：充值／积分操作与记录分离及独立积分冲正、商户充值直接确认并自动核价、钱包复用租户顾客管理入口。仅发布前端；无后台、API、权限、依赖、环境变量或数据库迁移变更。

- 功能提交 `aa0d8c83fa20c80e2be60e480f6de224347e7b88`，分支 codex/paynow-payment-product-line-staging，已推送。
- 精确提交 28 个相关文件。10 个原有组件换行改动、其他未跟踪复验资料与私密文件保留；未读取或上传私密文件。
- 干净 Git 归档 2371 个文件逐一校验 Git blob，124 个本轮相关源文件与已通过测试目录一致（换行归一化）。
- 同一实现状态 153 项钱包前端测试、10 项相关 Java 测试通过，原工作区类型检查与构建通过。
- 发布提交干净归档再次 npm ci、vue-tsc 与 Vite 构建通过。npm ci 报告原有 7 项 high 依赖告警，本轮未改动依赖。没有因此执行自动依赖升级。
- 本地隔离 PostgreSQL 与浏览器验收见三个同日 ACCEPTANCE／CONFIRMATION／ENTRY 记录；发布过程不使用真实会话提交充值、扣款、积分或顾客维护。

结论：GO，发布及独立公网核验完成。未验证真实手机硬件。

## 目标、制品与备份

- 目标 ubuntu@43.134.69.75，/opt/rpb/frontend；发布锁 /home/ubuntu/rpb-ledger-release.lock。
- 发布前 FRONTEND_RELEASE_SHA 为 `0fc31a3b8e6b16aa339d5d9b4e798ac43bd2bdb2`，发布后为上述 aa0d8c83 完整 SHA。
- 后端 RELEASE_SHA 始终 `92f3fcf6afb2ce3185f9ee2de2184dbb8c29ad77`；jar SHA256 始终 `bfa8a6efeeac2319ba7a2dbd53a5fb1c21a6fe78df507574605aea88086e33e7`。
- 前端压缩包 533749 字节，SHA256 `8f74aa6475116a7d3a3c936ee5a86afb63bcaad2e5d46178e9179042ca558081`。
- 新 index SHA256 `5b3613a54e1b3635edd663ad51a7a313f8e43eb302c88c57bb38cde63123c38e`。
- 发布目录 /opt/rpb/releases/20261024-wallet-operations-aa0d8c83。
- 备份目录 /opt/rpb/backups/20261024-wallet-operations-aa0d8c83-before，0700；含完整旧 frontend、前端 SHA、944863 字节数据库 dump 及业务指纹，pg_restore 目录检查可读。

持锁后再次核对旧版本、旧 index／jar 及上传包哈希，备份后复制新 assets、保留旧 assets，原子替换 index 和更新前端版本标记。未停止或重启后端、nginx、PostgreSQL、Redis、Docker；未修改其他项目部署。脚本包含失败后原子恢复旧 index 的保护。

## 生产验证

1. booking.yumstone.sg、platform.booking.yumstone.sg、20000000.booking.yumstone.sg、sx.booking.yumstone.sg 四个域名 HTML 与本次准确构建一致。
2. 收银台、充值与积分、租户顾客管理、独立积分冲正、交易记录五个页面地址返回准确 HTML；200 个静态资源逐字节匹配发布制品。
3. 六个既有身份、门店授权、租户顾客、钱包上下文、交易及公共订位上下文接口，发布前后状态与 JSON 语义一致。
4. 顾客维护、充值报价和积分增加九项匿名／缺同源标记／跨站探测按原保护拒绝：租户管理接口均未登录 401，钱包接口匿名 401，缺标记和跨站 403。未使用真实登录会话写线上数据。
5. 114 张业务与配置表发布前、发布后及独立最终核验指纹一致；会话、验证码、审计、限流等自然变化表按原口径排除。
6. Flyway 仍 066，总数 66、失败 0；没有迁移。
7. RPB PID 1059258、InvocationID 8ee428ebe5aa4e999bde1eecd49786b8、NRestarts 0 保持；nginx、PostgreSQL、Redis、Docker 的状态、PID、InvocationID、重启数均保持。

线上业务端到端的充值、积分及顾客新增维护未重做，以避免修改生产业务数据；对应流程已在隔离本地环境验证。首轮生产探测错误预期租户管理跨站请求应返回 403，实际原认证保护返回 401；按原控制器保护规则修正测试预期后通过，未修改产品或认证规则。错误方法／不存在路径的两个基线探测已排除，不计入六项既有接口验证。

## 回退

未触发回退。若需回退，持同一锁，从上述备份恢复资源，先写临时 index 再原子替换，恢复 FRONTEND_RELEASE_SHA 为 0fc31a3b 完整 SHA，验证旧资源与后端健康即可；不重启后端，不恢复数据库覆盖新业务数据。

## 证据

target/wallet-operations-release-preflight.log、wallet-operations-source-proof.json、wallet-operations-clean-npm-ci.log、wallet-operations-clean-build.log、wallet-operations-artifacts.json、wallet-operations-deploy.log、wallet-operations-assets-proof.json、wallet-operations-api-proof.json、wallet-operations-final.log。

本发布记录单独提交，不改变运行的前端功能 SHA。
