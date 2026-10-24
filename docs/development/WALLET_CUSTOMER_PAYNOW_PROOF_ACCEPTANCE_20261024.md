# 顾客 PayNow 凭证验收（2026-10-24）

工作目录：`D:\RPB`。本轮扩展前轮商户凭证实现；仅本地实现及验证，未提交、推送或生产发布。其他既有修改、未跟踪资料保持原状。

## 交付行为

- 顾客充值及账单 PayNow 补付复用公共拍照、相册上传组件，支持完整可解码 PNG/JPEG，最大 5MiB。
- 顾客提交仅保存私有凭证、审计；充值附加声明付款时间，不产生实收、不增加余额、不结清账单。识别失败仍保留原图供人工核查。
- 商户收款表单可刷新查看本单顾客待确认凭证，选择后带出银行 Ref 和识别核对结果，仍须核实银行入账并明确确认。沿用原事务、幂等及防重复凭证绑定。
- 顾客只读本人上传记录及原图，不返回员工上传、OCR原文或内部确认备注。完成、关闭后的充值、账单及交易详情保留只读凭证入口。
- 捕获/OCR共用 `WalletProofCapture`；顾客授权及源单据规则独立在 `WalletCustomerProofService`。顾客上传先锁账户再锁单据，与原确认顺序一致。
- 顾客幂等键按会话顾客分区；同一原请求在单据完成后可重放，内容改变拒绝。跨单、金额及渠道改变清空客户端附件；迟到响应不能串单。
- 过期未实收充值及未收款账单不能出码招揽新资金；既有部分付款账单可按原规则补付。既有付款证据声明不自动绕过商户资金确认的商业资格检查。

## 数据库及接口审查

读取 `target/local-postgres-current.txt`，使用 `127.0.0.1:61644/postgres`、postgres、空密码，测试创建随机独立 schema 并清理。未改共享本地运行 schema、原业务行或生产库。

V070 仅附加 `uploaded_by_customer_id`、同租户 customers 外键及待确认查询索引；前轮 V069 不改写。列表只查询元数据，不加载全部附件二进制；图片由单独的会话校验接口读取。原员工 API 保留，新增顾客及员工待确认路径见 `docs/api/2026-10-24-wallet-customer-paynow-proof-contract.md`。

## 测试与证据

| 验证 | 结果 | 证据 |
|---|---|---|
| 真实 PG 钱包交易回归，含顾客充值及账单凭证 | 18/18 | `target/surefire-reports/com.rpb.reservation.wallet.WalletTransactionsPostgresTest.txt` |
| 顾客/商户 multipart API、本地真实安全链、图片及银行号/OCR回归 | 35/35 | `target/customer-proof-backend-final.log` 及对应 Surefire 报告 |
| 前端凭证、营销积分、交易、导航、重试、付款反馈、二维码 | 124/124 | `target/customer-proof-front-delivery.log` |
| vue-tsc + Vite 生产构建 | 通过 | `target/customer-proof-build-delivery.log` |
| diff 空白检查 | 通过；仅既有 CRLF 提示 | `git diff --check` |

后端总计 53 项，无失败、错误或跳过。PG 覆盖上传无资金变化、声明付款标记、顾客/门店/租户隔离、顾客不能读员工图片、无内部备注泄漏、图片校验、幂等冲突及完成后重放、商户选用确认、已关闭单据拒绝、商业过期及部分付款恢复。

## 独立代码审查与修复

按 executing-plans 要求进行一次全功能独立只读审查，初始结论 APPROVE_WITH_COMMENTS，仅发现确认/关闭后顾客无法查看凭证历史的功能缺口；无未修复资金或隔离问题。

该缺口按实际流程影响纳入必要修复：新增独立只读 `WalletCustomerProofHistory`，接入已完成充值、结清/关闭账单及顾客交易详情。回归 `settled or closed bill still exposes own receipt history without context, QR or upload calls` 先失败再通过；证据 `target/customer-proof-review-red.log` → `target/customer-proof-review-green.log`，修复后相关前端全套 124/124 及构建通过。

Final: fixed 顾客完成或关闭后凭证入口消失 — settled or closed bill still exposes own receipt history without context, QR or upload calls RED→GREEN, suite 124/124。

审查者未判断的边界：真实移动端浏览器交互、最终回归结果、生产表现。最终回归已由实施者执行并核实；真实浏览器、真实银行凭证 OCR 准确率及生产环境未验证，不据此宣称线上可用。没有遗留的可选小问题或拒绝修复的审查意见。

## 发布与回退

本轮未执行生产部署。后续发布需将前轮 V069 和本轮 V070、前后端一并交付。附件为私有数据库数据，应随原数据库备份和访问保护策略保存。回退应用可保留新增表列与历史凭证；不得用旧库覆盖已产生的资金或确认记录。不影响预订、排队、入座或清洁流程。
