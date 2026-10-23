# 钱包 API 与数据契约

统一个人码后续契约见 [2026-10-23 个人码付款与恢复](2026-10-23-wallet-personal-payment-contract.md)，旧WC直扣端点已安全关闭；以该补充契约为准。

统一交易查询及旧列表退役见 [2026-10-23 交易记录契约](2026-10-23-wallet-transactions-contract.md)。资金及积分列表均使用 `/transactions`；原业务详情及命令继续保留。

状态：实现契约。规格：[钱包需求](../development/钱包产品线开发需求.md)。不含线上支付接口。

## 认证、作用域与金额

- 员工路径 `/api/v1/stores/{storeId}/wallet` 使用既有员工会话和动作权限，租户来自 `CurrentActor`，全部对象同时校验租户、门店/计划。
- 顾客路径 `/api/v1/public/stores/{storeId}/wallet` 使用 `RPB_CUSTOMER_SESSION`，顾客 ID 只取会话；写操作校验同源，不能从正文选其他顾客。
- 统一顾客认证在邮箱/OAuth 登录和已有会话使用时，检查认证账号及绑定的原 Customer 均 active、同租户且未删除。归档/停用/缺失 Customer 的登录返回既有 HTTP 401 `account_disabled`，不消耗有效验证码、不签发会话、不新建替代顾客；旧会话返回未认证。钱包显示明确的资料停用提示。
- 顾客交易以钱包门店商业状态判断，不伪造员工身份；到期本人读取与原单善后独立于新交易授权。
- 金额为 SGD 十进制字符串，正数且最多两位小数，服务端计算余额与差额。
- 命令使用非空文本 `idempotencyKey`，绑定租户/身份/动作和摘要；同键同参返回原响应，同键异参 409。成功资金写、幂等响应、审计同事务。
- 数据库维护正金额、不负余额、同租户复合外键、外部收款引用唯一、充值/消费授权唯一入账和分录只追加约束。

## 员工用例

GET `/context`：统一门店/主体/Logo、计划、账户查询条件、允许渠道和商业状态。

GET/PUT `/collection-profile`：`wallet.settings.manage`，复用 payment 所属的同一门店收款资料。PUT 含 `version,status,paynowType,paynowMobile,paynowUen,merchantName,issuerVerified,idempotencyKey`；启用时必须明确核实收款账户属于当前钱包发行主体。租户默认资料只预填参考，须保存为门店配置；禁止把未注明发行主体的租户默认收款账户直接作为多个钱包主体的收款依据。保留现有 quick-pay 的 configJson。若同店支付产品有效，修改还需 `payment.settings.manage`，界面明示会影响共用收款资料；不授予支付产品订阅。

GET `/customers?query=`：查询租户共享有效顾客，不以是否拥有登录账号筛选；GET `/accounts/{accountId}`：在允许计划门店内查询账户；流水改用统一 `/transactions`。

PUT `/program`：`version, channels`，发行主体取当前统一门店，配置操作需 `wallet.settings.manage`，不能变更公共资料/订阅。

POST `/accounts`：`customerId, idempotencyKey`，返回原有或零余额账户，验证租户共享有效顾客；新开户须有手机号，缺号返回 409 `CUSTOMER_PHONE_REQUIRED`。历史无号账户保留。顾客登录使用[共享手机号密码登录契约](2026-10-23-shared-customer-phone-auth.md)。

POST `/topups`：`accountId, amount, channel, idempotencyKey`，返回待收款单；POST `/topups/{id}/confirm`：`amount, receiptReference, collectionAccount, terminalCode, note, idempotencyKey`，授权员工人工确认后入账。GET `/transactions?type=topup` 查询。取消仅允许未收款单。

2026-10-23：仅普通现金充值确认允许 `receiptReference` 缺省、null 或空白；服务端按充值单 ID 生成稳定内部编号 `CASH-TOPUP-{topupId}`，满足原收款凭据非空和唯一约束。用户填写的编号仍按原规则校验和保存。PayNow、信用卡、账单补付和异常收款仍必须填写凭证；金额核对、实际收款勾选、终端、权限、租户／门店隔离及幂等不变，不涉及迁移。重试或重复确认不得重复充值。

POST `/bills`：`accountId, amount, idempotencyKey, customerCode?`，创建消费单。扫码时必须携带 `customerCode`，码校验、建单、绑定、审计和幂等快照同一事务；取得锁后检查时效，过期/已用/不匹配的码不留下账单或快照。同键同参重试返回已绑定原单，即使码随后过期；不同键重用同码返回 409。手工选客省略码保持原行为。POST `/bills/{id}/code` 单独签发 10 分钟商户账单码。GET `/bills/{id}`：包含支付分摊/剩余应付；POST `/bills/{id}/bind-code`：`code` 绑定已有账单（旧接口保留，禁止用于扫码新建单的两阶段流程）。POST `/payment-codes/resolve`：识别有效未绑定付款码对应的统一顾客，不授权扣款。POST `/bills/{id}/collections`：三渠道线下补差确认，使用与充值一致的凭证字段和权限。

POST `/refunds`：`sourceType(top_up/bill/collection_exception), sourceId, amount, reason, idempotencyKey`，经理权限；POST `/refunds/{id}/confirm`：`partId, receiptReference, idempotencyKey` 确认已完成的原渠道线下退款；POST `/refunds/{id}/cancel` 仅取消未执行退款并释放冻结，已经退钱包的混合退款不允许取消。GET `/transactions?type=refund` 查询退款，`/refunds/{id}`查询详情，`/reconciliation?start=YYYY-MM-DD&end=YYYY-MM-DD`保留对账。

GET/POST `/collection-exceptions`：经理登记迟到、超收或重复实收；正文为 `sourceType(top_up/bill), sourceId, confirmation, reason, idempotencyKey`。confirmation 为完整三渠道核实事实，金额可与原单不同；始终不加余额、不结清原单。以返回的异常凭证 ID 创建 `collection_exception` 退款，仅可退实际异常实收，累计不超额。

PayNow 普通确认必须填写商户银行实际收款 UEN/手机号（`collectionAccount`），服务端与当前门店配置核对，不把空值默认为已核对账户；不匹配时转经理异常复核。资料停用/变更后，经理仍可按原单填写实际收款标识登记异常实收并退回，不能因此增加钱包余额。无有效门店配置时，服务端拒绝新建 PayNow 充值，不只靠前端禁用。

GET `/customers/{id}`、`/customers/{id}/accounts`：只从租户共享顾客目录查询，不要求事先登录；GET `/logo/{assetId}`：只允许当前统一 Logo，清除后旧 URL 返回 404。确认正文使用 `channel, amount, collectionAccount, receiptReference, terminalCode, note, verified=true, idempotencyKey`；仅普通现金充值的 receiptReference 可省略，其余渠道／用例要求不变，渠道不能由客户端伪造为在线回调。

## 顾客用例

GET `/me`：本人已有账户、余额、门店公共资料；GET `/transactions`：本人统一交易查询；GET `/bills/{id}`：包括到期后本人原单结果，刷新可恢复。GET `/logo/{assetId}`：复用当前统一 Logo。POST `/payment-codes`：`accountId, purpose?`，签发 60 秒动态码，重新签发使旧未绑定码失效，不使用业务幂等键。

GET `/pending-bills`：本人待确认/补付账单；POST `/bill-code`：校验受限账单码并返回本人账单，不泄露其他顾客资料。

旧独立列表 GET `/bills`、`/topups`、`/refunds`、账户流水及积分分录列表已移除，不提供兼容层。筛选、稳定混排分页、全量汇总及只读详情以交易记录契约为准。员工充值详情 GET `/topups/{id}`保留；待付款业务恢复 `/pending-bills`保留。

POST `/bills/{id}/quote`：本人、当前门店，返回钱包拟扣金额、剩余应付、账户/账单版本和默认 60 秒期限。POST `/bills/{id}/pay`：`quoteId, idempotencyKey`，再次本人校验并原子扣款；客户端不能改金额。零钱包余额不生成零分录。

账单金额 80、可用余额 50：扣 50 后 `partially_paid`，`remaining=30`；补差确认后 `paid`。退款独立，不把已结清单重新变成应付。

顾客从当前门店入口出示付款码；如切换门店，使用目标店入口或扫描员工生成的目标账单码完成本人确认。旧 WP 码仍为短时身份绑定媒介；新 UI 以 `purpose=merchant_charge` 出示 WC 扣款授权码，由有收款权限的员工核对金额后一次确认入账，无需顾客二次确认。顾客扫 WB 商户账单码则由顾客一次确认支付，商户无需再次确认钱包部分。

## 错误

401 `WALLET_UNAUTHENTICATED`；403 `WALLET_FORBIDDEN`/既有 App Gate 拒绝；404 `WALLET_NOT_FOUND`（包含不可见对象）；400 `WALLET_REQUEST_INVALID`；409 `WALLET_CONFLICT`、`WALLET_IDEMPOTENCY_CONFLICT`、`WALLET_RECEIPT_USED`、`WALLET_AUTHORIZATION_EXPIRED`、`WALLET_QUOTE_STALE`、`WALLET_REFUND_EXCEEDS_LIMIT`、`WALLET_LEDGER_MISMATCH`。返回 `{code,message}`；不把未知资金结果伪装为失败后自动新建交易。

共享顾客停用新增 409 `CUSTOMER_HAS_RETAINED_ASSETS`，通过顾客生命周期扩展端口检查；数据库触发器同时防止绕过应用直接改归属/合并/删除。

## 权限与上线兼容

`wallet.view / wallet.topup.create / wallet.collection.confirm / wallet.charge.create / wallet.refund.manage / wallet.reconcile.view / wallet.settings.manage`。新老员工按职责分配，普通员工不默认确认/退款；产品注册不自动授予商业订阅。

计费购买/续费新增可选 `storeIds`，钱包必填；旧产品缺省保持原语义。钱包首购按选定店，续费仅更新选定已开通店并保留兄弟店。钱包价格模板默认未激活，不隐式免费开通。

## 员工扫码一次确认（2026-10-22 更新）

详细请求、状态查询、原单扣款、权限、错误和幂等契约见 [WALLET_SINGLE_CONFIRMATION_20261022.md](../development/WALLET_SINGLE_CONFIRMATION_20261022.md)。新增 POST 员工 /scan-charges 和 POST 顾客 /payment-codes/status，沿用原资金与统一资料边界，无迁移。旧 /bills 不授予直接扣款权。
