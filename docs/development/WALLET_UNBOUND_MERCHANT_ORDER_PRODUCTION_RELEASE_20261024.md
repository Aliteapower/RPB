# 商户无需选客出码生产发布记录

2026-10-24，D:\RPB。用户明确授权「提交部署」。

## Production Readiness Report

### Checklist

- GO：商户直接输入消费金额或积分数量／兑换内容出码，无需输入手机号、查询或选中顾客。顾客通过现有扫一扫预览并明确确认，由本人登录会话确定付款人。积分兑换不抵现金。
- 复用开发阶段同一代码状态的后端138项、前端回归127项及末次64项定向检查，均通过；隔离浏览器跑通消费10元及兑换5积分，双方关联同一真实账单／积分流水。详见同日 ACCEPTANCE、REVIEW 和 RELEASE_NOTES。
- 本轮代码提交28个文件；从提交干净归档构建，2351个文件逐一核对Git blob完全一致。Maven package、npm ci、Vue类型检查、Vite构建成功。
- V066已审查并在生产数据库受限副本演练，原113张业务／配置表指纹不变。生产通过Flyway执行，最终066／66项／0失败。
- 无新增依赖、环境变量或权限；复用AppGate、员工门店权限、顾客归属、同源请求标记及跨站保护。仅保存二维码令牌散列，120秒有效。
- 读取码不绑定顾客、不扣款；首次成功确认才绑定本人。真实扣款和兑换使用原资金与积分模块，行锁、原事务和幂等覆盖并发、失败回滚及重试。原WB／WR接口、个人钱包码、扫码收款、充值营销、分摊和退款保留。
- 已备份旧应用、前端、版本及停机边界数据库；受保护切换、健康检查、数据核对与回退脚本准备完成。新进程日志无ERROR。

### Blocking Issues

无未解决发布阻塞。

### Non-blocking Issues

真实手机摄像头、触控及生产规模负载未验证，隔离环境二维码PNG识别不等于真机相机验证。npm ci仍报告既有7项high依赖告警，本轮未更改依赖。生产不代替顾客同意或执行真实资金／积分交易。

### Decision

GO。完成下述发布与独立上线核验。

## 提交及线上版本

代码提交 `92f3fcf6afb2ce3185f9ee2de2184dbb8c29ad77`，已推送 `codex/paynow-payment-product-line-staging`。本记录后续单独提交，不改变运行代码版本。

发布前核实：发布锁空闲，前后端均为67995de6，Flyway065，既有服务身份符合预期。目标仅 ubuntu@43.134.69.75、/opt/rpb、rpb-backend、rpb_acceptance。发布后前后端版本标记均为上述92f3fcf6完整SHA。

- jar SHA256：`bfa8a6efeeac2319ba7a2dbd53a5fb1c21a6fe78df507574605aea88086e33e7`。
- 前端压缩包 SHA256：`16aafb6ab799b7264461d075d71f991156d027e10e3c2b638d2f2ff8413dd205`。
- index SHA256：`be72256fb27438d68fc360515c43d10b7f451cb3e70be3b4f60670d5288249d0`。
- 发布目录：`/opt/rpb/releases/20261024-wallet-unbound-92f3fcf6`。
- 停机边界备份：`/opt/rpb/backups/20261024-wallet-unbound-92f3fcf6-before`，含原jar、完整前端、前后端版本标记及934922字节数据库dump；pg_restore目录验证可读。

保留工作区原有11个换行改动文件、两份未跟踪验收材料及私密文件，均不纳入提交或制品；未读取、上传私密材料。没有启动或停止其他本地开发任务。

## 迁移、切换与数据

V066只附加wallet_merchant_orders意图表、约束和索引，不修改原余额、历史或账本。新流程成功后关联真实账单／积分entry，统一交易查询不把意图重复算收入。

先在`rpb_wallet_unbound_rehearsal_20261024`演练；撤销PUBLIC连接权限，不启动业务应用，15项表约束建立，新表为空，迁移前后113张原表指纹一致。演练备份位于`/opt/rpb/backups/20261024-wallet-unbound-rehearsal`。

持`/home/ubuntu/rpb-ledger-release.lock`执行，校验旧版本／旧文件及上传制品哈希。临时隔离nginx至RPB后端入口，停止RPB并备份数据库；安装新jar，启动后由Flyway迁移。确认健康、原表指纹及新表为空后发布静态资源，再原子切换index及版本标记，保留旧assets。临时入口隔离规则已撤除。

生产最终Flyway066、总数66、失败0。停机边界／切换后／公网核验后的113张原业务与配置表指纹一致（包括既有V065兑换意图表）；排除自然变动的会话、审计、限流和动态码表。新wallet_merchant_orders最终仍为0行。生产仅匿名保护检查和只读核验，没有替顾客创建订单、确认支付或兑换。

## 公网及服务核验

- booking.yumstone.sg、platform.booking.yumstone.sg、20000000.booking.yumstone.sg、sx.booking.yumstone.sg四个域名HTML，以及全部192个静态资源，逐字节匹配本轮干净构建；两个门店管理入口匿名401。
- 新merchant-order接口34项、既有个人授权／付款／交易查询32项、旧积分兑换接口34项，共100项匿名、缺标记及跨站保护检查通过。
- 既有订位入口、门店上下文、支付、日账本和身份等9项公开接口，状态和响应内容逐字节与发布前一致。
- RPB PID1059258，InvocationID `8ee428ebe5aa4e999bde1eecd49786b8`，NRestarts0；部署检查及独立最终核验均稳定。
- nginx、PostgreSQL、Redis、Docker的状态、PID、InvocationID和重启数与发布前一致，没有操作其他项目服务。

公开接口响应比较脚本首次遇到Windows默认GBK解码错误，改用JSON UTF-8字节解析后9项比较通过；服务请求本身均成功，该脚本问题未导致线上变更或重复业务。

## Rollback Plan

未触发回退。若后续需回退，持同一发布锁，隔离RPB入口，停止rpb-backend，从上述停机边界备份恢复reservation-platform.jar、frontend内容及两份版本标记，修复rpb属主权限后启动。确认服务active、auth/me匿名401、PID／Invocation稳定，再撤除入口隔离。现有发布脚本包含失败时恢复旧应用并健康检查的路径；未健康时保持隔离并明确报错。

应用回退保留V066附加表及已经完成的真实资金／积分流水，不恢复旧数据库覆盖真实交易，不删历史、不直接回写余额。业务撤销使用原退款或积分冲正。旧WB／WR页面及接口兼容仍保留。

## 证据

本地`target/wallet-unbound-release-source-proof.json`、`wallet-unbound-clean-backend-build.log`、`wallet-unbound-clean-npm-ci.log`、`wallet-unbound-clean-frontend-build.log`、`wallet-unbound-rehearsal.log`、`wallet-unbound-rehearsal-verification.log`、`wallet-unbound-artifacts.json`、`wallet-unbound-deploy.log`、`wallet-unbound-release-assets-proof.json`、`wallet-unbound-new-auth-proof.json`、`wallet-unbound-existing-auth-proof.json`、`wallet-unbound-legacy-redemption-auth-proof.json`、`wallet-unbound-original-api-proof.json`、`wallet-unbound-final.log`。
