# 商户消费出码与顾客确认积分兑换发布

2026-10-24，D:\RPB。用户明确授权「提交部署」。

## Production Readiness

GO：本轮恢复商户出消费付款码、增加顾客确认积分兑换码，并连接顾客现有扫一扫。保留商户扫码、手机号充值／积分、营销、个人授权和原交易查询。消费复用既有资金模块，积分兑换经钱包适配器调用既有积分账本，不抵现金。

复用相同业务代码状态的后端142项、前端回归127项、末次定向57项和隔离浏览器证据，见 WALLET_MERCHANT_QR_ACCEPTANCE_20261024.md。提交归档2337文件逐一校对Git blob完全一致；干净归档 Maven package、npm ci、Vue类型检查及Vite构建通过。最后仅页面间距调整已构建和浏览器复核。npm既有7项high告警，本轮未更改依赖。

无新增环境变量或权限，复用AppGate、wallet.charge.create及wallet.points.view/redeem、租户／门店／顾客归属和同源写保护。身份及积分余额保持独立。真实手机相机与生产规模负载未验证；二维码图片识别及完整交易使用本地隔离数据验证。

## 发布范围及运行版本

代码提交 `67995de6a1fc6be28dbd3df75b3f2774493a69f2` 已推送 `codex/paynow-payment-product-line-staging`。仅提交本轮33个文件，保留原有11个换行改动文件、两份未跟踪验收材料及私密文件，不纳入制品。

发布前线上后端cb400bf7、前端acb9b206，发布锁空闲，与预期完全一致。目标 ubuntu@43.134.69.75、/opt/rpb、rpb-backend、rpb_acceptance。当前前后端 RELEASE_SHA 均为本轮完整代码提交。后续本发布记录文档提交不改变运行版本。

- jar SHA256：889f3493dfac14b5864ad798065556044854b6397316c51d6c9b9eb917840f25。
- 前端包 SHA256：d85d30e50035a9054e5bc18a1d1850523e97535745dadeb44476393b7c156d75。
- 发布目录：/opt/rpb/releases/20261024-wallet-merchant-67995de6。
- 停机边界备份：/opt/rpb/backups/20261024-wallet-merchant-67995de6-before，含原jar、完整前端、前后端版本及927926字节数据库dump；pg_restore目录验证可读。
- 持发布锁并校验旧版本、旧制品及上传哈希；临时隔离RPB后端入口，备份并切换jar，Flyway完成后再更新前端。先发布assets、后原子替换index，保留旧assets。健康、数据及邻近服务检查通过后开放流量，临时规则已撤除。

## 迁移及数据

V065附加wallet_points_redemption_requests意图表、约束和索引，不改既有账本。先在生产受限副本rpb_wallet_merchant_rehearsal_20261024中演练成功，PUBLIC连接已撤销，不启动业务应用；原业务指纹一致。

生产由Flyway成功应用065，最终65项、0失败。切换前／后／最终112张原业务及配置表指纹一致，排除自然变动的会话、动态码、审计和限流表；新意图表为空。未替真实顾客出码、同意授权、充值、付款或兑换；生产验收仅匿名保护与只读核对。

## 公网及服务核验

- 4个公网域名HTML、全部190个静态资源逐字节等于本轮干净构建；2个门店管理入口匿名401。
- 新积分兑换接口34项，以及既有个人授权／付款／交易查询32项匿名、缺标记及跨站保护检查通过。
- 原订位、支付、日账本、身份等9项公开接口状态及JSON语义保持一致；顾客未登录响应仅Map字段顺序变化，非业务差异。
- RPB新PID1046709、InvocationID a9b77d252b3044cd85882c424ae6ba3d，NRestarts0，部署后及独立最终核验稳定；新进程日志无ERROR。
- nginx、PostgreSQL、Redis及Docker的PID、InvocationID、状态和重启数发布前后及独立最终核验不变。没有操作其他项目服务。

## 回退与限制

未触发回退。若需回退，恢复停机备份jar、前端及版本，确认旧服务健康后开放入口；保留V065附加表、审计和已生成兑换流水，不自动恢复数据库覆盖真实交易，不回写余额。兑换撤销使用既有积分冲正。

完整交互来自隔离测试，相册二维码识别通过，真实手机相机尚未验证；生产不代替顾客执行确认或真实交易。消费码继续绑定已确认顾客并沿用既有600秒，积分码120秒。

本地证据：target/wallet-merchant-release-source-proof.json、wallet-merchant-clean-*、wallet-merchant-rehearsal.log、wallet-merchant-artifacts.json、wallet-merchant-deploy.log、wallet-merchant-release-assets-proof.json、wallet-merchant-new-auth-proof.json、wallet-merchant-existing-auth-proof.json、wallet-merchant-original-api-proof.json、wallet-merchant-final.log。原本地服务和数据库运行环境保留。
