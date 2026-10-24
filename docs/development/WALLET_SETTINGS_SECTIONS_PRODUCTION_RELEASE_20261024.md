# 钱包分类设置页面生产发布

2026-10-24，D:\RPB。用户明确授权「提交部署」。

## Production Readiness Report

### Checklist

- 原设置长页面改为功能目录，充值收款方式和PayNow收款账户分别进入独立页面；平台资料仍在统一资料维护。目录展示状态，子页单独保存及返回。
- 同一实现状态的136项钱包前端测试在干净目录通过，69项定向测试通过；隔离PostgreSQL及浏览器验证渠道／PayNow保存、刷新回显、权限、返回导航与中英文375 CSS像素布局。详见同日ACCEPTANCE。
- 本轮只提交12个设置相关文件。干净Git归档2357文件逐一核对Git blob完全一致，117个钱包及前端测试源文件与已通过136项测试的目录一致（只归一化换行）。
- 新提交干净归档npm ci、Vue类型检查及Vite生产构建通过；不修改依赖、环境变量、后台API或权限。
- 仅发布前端，不重启后端，不执行迁移或业务写操作。版本、制品、发布锁和目标机器已核实；旧前端及数据库备份可读，回退路径明确。

### Blocking Issues

无。

### Non-blocking Issues

原工作目录的Vite测试服务曾出现端口冲突／模块加载卡住；同样代码在干净目录完整136项通过，未为此改动产品或原现金测试。npm ci仍报告既有7项high依赖告警，本轮依赖未变。未验证真实手机硬件；移动布局使用浏览器验证。

### Decision

GO。已完成生产发布和独立公网核验。

## 提交、目标和版本

- 分支：codex/paynow-payment-product-line-staging。
- 功能提交：`0fc31a3b8e6b16aa339d5d9b4e798ac43bd2bdb2`，已推送。
- 目标：ubuntu@43.134.69.75，/opt/rpb/frontend；没有操作其他项目服务。
- 发布前后端与前端均为92f3fcf6，发布锁空闲；未发现并行RPB发布。
- 发布后FRONTEND_RELEASE_SHA为0fc31a3b完整SHA；后端RELEASE_SHA仍为`92f3fcf6afb2ce3185f9ee2de2184dbb8c29ad77`，后台代码无变化。
- 前端压缩包SHA256：`eee2bb111caf4f08ed67cf2344fa622ef166bf943c8bdb0fe44256673edce588`，535037字节。
- index SHA256：`b7146ac25c778fb5dfa835c706f98f4978c3fcc34a408b7e84b46b6950fab97a`。
- 运行jar SHA256保持`bfa8a6efeeac2319ba7a2dbd53a5fb1c21a6fe78df507574605aea88086e33e7`。

其他10个原有组件换行改动、原未跟踪验收／复验材料及私密文件保留，不纳入本轮提交或制品。未读取或上传私密文件。后续本发布记录单独提交，不改变运行版本。

## 发布和备份

持`/home/ubuntu/rpb-ledger-release.lock`，校验前后端版本、旧jar／index哈希及上传包哈希，确认后端健康。

- 发布目录：`/opt/rpb/releases/20261024-wallet-settings-0fc31a3b`。
- 备份目录：`/opt/rpb/backups/20261024-wallet-settings-0fc31a3b-before`，0700权限，含完整旧frontend、旧FRONTEND_RELEASE_SHA、942369字节数据库dump和业务表指纹。
- pg_restore目录检查确认dump可读。
- 先复制新assets并保留旧assets，再原子切换index及前端版本标记。没有停止后端、nginx、数据库、Redis或Docker，没有临时阻断入口。

## 生产验证

1. booking.yumstone.sg、platform.booking.yumstone.sg、20000000.booking.yumstone.sg、sx.booking.yumstone.sg共4个域名HTML及194个静态资源逐字节匹配干净发布制品；2个门店管理入口匿名401。
2. 20000000域名的settings、settings/channels、settings/paynow共3个地址返回本次准确HTML，子页对应路由与组件资源已发布。
3. 两个域名的现有设置接口共14项匿名、缺同源标记及跨站保护检查通过。未使用真实登录会话保存线上设置。
4. 既有订位入口、门店上下文、支付、日账本及身份等9项公开接口，状态及JSON响应语义与发布前一致。
5. 114张原业务／配置表在发布前、发布后及独立最终核验的指纹完全一致；排除会话、动态身份码、审计、限流和Flyway等自然变动表。包括现有wallet_merchant_orders、钱包及积分账本、营销和收款配置。
6. Flyway仍066，总数66，失败0；没有迁移或数据库结构变更。
7. RPB PID1059258、InvocationID 8ee428ebe5aa4e999bde1eecd49786b8、NRestarts0保持；nginx、PostgreSQL、Redis和Docker的状态、PID、InvocationID、重启数也均与发布前一致。

## Rollback Plan

未触发回退。若需回退，持同一发布锁，从上述备份恢复frontend内容及FRONTEND_RELEASE_SHA（资源先恢复，index原子替换），确认旧页面和静态资源匹配、服务健康及后端版本未变即可。不重启后端、不恢复数据库覆盖业务数据。切换脚本已包含失败时恢复旧前端及版本标记的处理。

## 证据

本地target/wallet-settings-release-preflight.log、wallet-settings-release-source-proof.json、wallet-settings-tested-source-proof.json、wallet-settings-clean-npm-ci.log、wallet-settings-clean-build.log、wallet-settings-artifacts.json、wallet-settings-deploy.log、wallet-settings-release-assets-proof.json、wallet-settings-auth-proof.json、wallet-settings-original-api-proof.json、wallet-settings-final.log。
