# 商户出码与积分兑换码验收

日期：2026-10-24；项目 D:\RPB；开发测试完成，未提交、未推送、未部署。

## 实现及使用
- 商户收银台新增「商户出码」，地址 `/stores/{storeId}/wallet/merchant-code?entry=phone`，消费付款码／积分兑换码书签切换。按手机号查询、核对并确认顾客后出码，二维码绑定本人。
- 消费使用既有金额键盘、账单和 WB 码；顾客扫一扫识别后仍在原账单核对和确认付款，沿用本金赠送分摊、部分付款与补款、退款。个人钱包码免确认授权流程不变。
- 积分填写数量和兑换内容生成 WR 码，顾客从原「扫一扫付款」进入新兑换确认页，明确确认后才扣减积分。积分不抵现金；没有资金账户的顾客也可使用扫一扫办理积分兑换。
- 现有商户扫个人码、手机号充值与积分、营销和统一交易记录保留。新增操作入口不替换既有扫码方向。
- 取消和完成后可办理下一位顾客，清除上位顾客、手机号查询候选、金额、兑换内容及码；提交期间禁止切换。

## 自动化证据
| 检查 | 结果 | 本地证据 |
|---|---|---|
| 个人码／商户出码真实 PostgreSQL | 25 通过 | target/surefire-reports/com.rpb.reservation.wallet.WalletPersonalPaymentPostgresTest.txt |
| 原钱包真实 PostgreSQL | 59 通过 | target/surefire-reports/com.rpb.reservation.wallet.WalletPostgresTest.txt |
| 本地安全精确方法、身份、请求头和 DTO 拒绝覆盖顾客 | 6 通过 | target/surefire-reports/com.rpb.reservation.wallet.WalletLocalRuntimeSecurityTest.txt |
| 营销、积分、交易查询、原 API／请求安全合计 | 相关后端总计142通过、无跳过 | target/wallet-merchant-code-e2e-20261024/backend-test-summary.json |
| 前端钱包回归 | 127通过 | target/wallet-merchant-code-frontend-regression.log |
| 最后界面修正后定向组件／二维码分流 | 57通过 | target/wallet-merchant-code-frontend-final.log |
| vue-tsc + Vite 构建 | 通过 | target/wallet-merchant-code-build-final.log |
| 后端打包 | 通过 | target/wallet-merchant-code-package.log |
| diff whitespace | 通过 | git diff --check |

命令：`node --experimental-transform-types --test --test-timeout=90000 --test-concurrency=1 src/test/frontend/wallet*.test.mjs`。最后修正只影响新出码页与顾客选择提示／重置，复跑覆盖相同组件的定向测试，未重复未受影响的全量测试。后端第二次仅复跑受修正影响的个人码、原钱包过期断言、安全类，其余相关套件复用同一业务实现状态下已通过结果。

修复记录：新控制器错误映射补齐；创建意图的余额及功能检查移到幂等执行内部；确认等待账户锁后复查有效期；旧60秒测试改为已上线120秒规则；下一位顾客 remount 手机号选择组件清除旧候选。未回滚原有工作区改动。

## 隔离浏览器验证
数据库来自 `target/local-postgres-current.txt`：61644/postgres，postgres/空密码。复用本线程持有的隔离 schema `wallet_transactions_0983618362a147a0b77cb19477406228`，只向该 schema 添加 V065；原 daily_ledger_validation_20261022、公用 public 及生产数据未变。

本轮独立服务：8090/5190；保存启动参数及 PID 于 target/wallet-merchant-code-e2e-20261024。原8081后端PID302868、预览PID310188、PostgreSQL PID310268持续保留。

1. 商户查询并确认测试顾客，输入12，生成WB消费码；顾客在现有扫一扫中上传实际生成的二维码PNG，成功进入同一账单。识别前后未扣款，明确确认后成功付款12；本金10.91、赠送1.09，商户同步已完成。账单 bcdd4fa5-c69d-4b6a-9c50-f00d80da5da0。
2. 同一顾客生成5积分、兑换内容「隔离验收兑换咖啡」WR码。顾客相册识别进入兑换确认页，确认前17积分，确认后12积分；钱包仍89.00。意图274f4f9b-1c77-4722-b085-f939061147ef，双方交易详情均关联积分entry 978b2850-05e2-4a85-b0fb-bea5618006c1。
3. 新建1积分意图并取消，再点击更换，手机号、候选顾客和兑换表单全部清空。数据库再次核对钱包8900分、积分12、目标兑换entry数量1。
4. 实际二维码图片识别通过；未使用或验证真实手机相机。不能把相册识别描述成真机相机扫码通过。

证据（均本地隔离测试）：
- target/wallet-merchant-code-e2e-20261024/customer-payment-success.png
- target/wallet-merchant-code-e2e-20261024/customer-points-success.png
- target/wallet-merchant-code-e2e-20261024/merchant-points-success.png
- target/wallet-merchant-code-e2e-20261024/customer-balances.png
- target/wallet-merchant-code-e2e-20261024/next-customer-clean.png
- target/wallet-merchant-code-e2e-20261024/result.json

## 迁移、权限与回退
V065仅增加待确认积分兑换意图表及索引，复用已有积分账本与既有权限，无新权限项。新增API合约见 docs/api/2026-10-24-wallet-merchant-qr-contract.md，审查见同日 REVIEW 文档。旧消费WB、个人WI和业务详情接口保留。未来部署需执行V065；本轮未向原本地服务或生产应用迁移。回退应用时保留新表、审计和已兑换entry；业务撤销通过既有积分冲正，不能删除历史或回写余额。

## 剩余范围
真实手机Safari/Chrome摄像头尚待现场验证。商户消费出码沿用既有绑定顾客及600秒有效期，WR积分码120秒；不提供匿名通用支付单。未修改预约、排队、日记账或其他产品线。

收尾：最后仅调整兑换结果卡片间距，重新构建并复核完成页；已关闭本轮临时浏览器及8090/5190验收进程，原运行环境保留。隔离测试数据和证据保留供复查。
