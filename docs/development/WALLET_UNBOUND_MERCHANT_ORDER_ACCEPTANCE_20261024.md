# 商户无需选客出码验收

2026-10-24，D:\RPB；本轮开发和测试完成，未提交、未推送、未部署。线上仍为上一轮67995de6，当前本地HEAD8acf2e27，保留已有无关改动。

## 实现
商户「商户出码」不再有手机号查询／顾客选择。消费直接输入金额及可选说明；积分直接输入数量及兑换内容。两者都生成新WM码，对应尚未绑定付款人的待支付单。顾客通过原扫一扫读取，核对后一次明确确认；消费从自己钱包支付，积分兑换只扣自己积分，不抵现金。

读取／扫码不绑定人、不扣减。首次成功确认才锁定付款人并关联真实业务；余额不足、停用、版本变化、过期等失败不占单、不建业务账。相同人重试只返回原结果，两位顾客同时确认最多一人成功。完成／取消后「下一笔」清空数值与说明，不出现手机号。修正出码及顾客确认页底部导航归属。

## 自动化验证
| 范围 | 结果 |
|---|---|
| WalletPersonalPaymentPostgresTest | 31通过，含新无绑定码、多顾客并发、失败不占单、超时原子回滚与原个人码 |
| WalletLocalRuntimeSecurityTest | 8通过，含新接口精确路径／方法、会话、同源写保护、拒绝customerId/accountId覆盖 |
| WalletTransactionsPostgresTest | 15通过，新增意图关联说明不破坏混合查询、统计或隔离 |
| WalletPostgresTest | 59通过，原钱包业务回归 |
| WalletMarketingPostgresTest | 15通过 |
| PointsPostgresTest | 10通过 |
| 后端合计 | 138通过、0失败、0跳过 |
| 钱包前端回归 | 127通过 |
| 最后组件／二维码／导航定向 | 64通过 |
| Vue类型检查、Vite构建 | 通过 |
| Maven package | BUILD SUCCESS |
| git diff --check | 通过 |

原先按旧启动方式的缺实现失败证据保留后完成实现。前端127项后只新增导航归属的小修改，复跑覆盖该组件及业务的64项定向验证，重新构建并实际刷新页面验证；未无依据重复未受影响的全量验证。

命令与证据：target/wallet-unbound-red.log、wallet-unbound-front-red.log、wallet-unbound-backend-final.log、wallet-unbound-front-regression.log、wallet-unbound-front-final.log、wallet-unbound-build-final.log、wallet-unbound-package.log、wallet-unbound-e2e-20261024/backend-summary.json。

## 隔离浏览器及数据库
遵循target/local-postgres-current.txt，使用127.0.0.1:61644/postgres、postgres空密码；复用本线程既有隔离schema wallet_transactions_0983618362a147a0b77cb19477406228，仅向此schema添加V066。原public及daily_ledger_validation_20261022不变。独立后端8091／前端5191，原8081服务及PID302868、预览310188、PostgreSQL310268保留。

1. 商户首页进入出码，无手机号输入，直接点击10元快捷金额并填「无需选客消费验收」生成码。订单41c636f3-1a18-440a-85a1-91b17ba24fca。
2. 顾客相册识别实际二维码PNG，直接显示10元、门店、说明及自己89元余额；仅一次「确认钱包付款」完成，余额79元。原账单aa9dcfd8-d195-4794-b6b6-63b57d926587，统一交易详情显示同一账单及原说明，商户同步完成并可下一笔。
3. 商户下一笔切积分，直接填5积分及「无需选客积分兑换验收」，无顾客查询，生成订单5ca2b02d-f961-43e9-bda4-2e4f8eb7eeba。
4. 顾客相册识别后仍12积分，点「确认扣减积分并兑换」后7积分。双方链接同一积分entry 0c2bf534-5dbb-4c63-8580-b6cad92b7e48，钱包仍79元。
5. 数据库核对：walletBalanceCents7900、pointsBalance7、completedOrders2、moneyBills1、pointsEntries1；无重复业务。原本金赠送分摊保留，最终本金71.82、赠送7.18。
6. 最后刷新新构建，直接输入数值及底部收银台导航正确。相册图片识别通过；未验证真实手机摄像头或触控，不能称真机相机扫码通过。

本地截图：target/wallet-unbound-e2e-20261024/merchant-money-entry.png、merchant-points-entry.png、customer-money-confirm.png、customer-points-complete.png、final-balances.png。数据库结果result.json，启动元数据processes.json。未读取或上传用户私密材料。

## API、迁移、兼容与回退
API契约见docs/api/2026-10-24-wallet-unbound-merchant-order.md，模块／数据库／代码审查见同日REVIEW。新接口只接受业务数值，不接受顾客或资金账户，复用已有权限，无环境变量／依赖变更。

V066附加意图表，不修改原余额或历史；资金仍走原bill/quote/pay／分摊／退款，积分仍走原loyalty兑换。统一交易记录只展示真实资金账单或积分entry；意图不算第二套账本或额外收入。未成功确认的无绑定意图不作为顾客的账单。

旧WB/WR接口和已绑定详情保留，已打开旧页面兼容；新出码入口不再调用旧绑定创建接口。商户扫码、手机号充值积分和个人码授权规则不变。新无绑定消费需足额支付才完成，余额不足不扣减；原账单部分付款能力不改。

未来部署需要V066。回退应用保留意图表和既有真实业务流水，不删除交易、不回写余额；业务撤销使用既有退款或积分冲正。真实手机相机及生产规模性能未测。本轮不发布。

收尾：本轮8091／5191进程及临时浏览器均已关闭；原三个运行进程保留，隔离数据库与证据保留供复查。未产生新Git提交或发布。
