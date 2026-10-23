# 统一个人钱包码 API 与持久化契约

个人限额规则已由[顾客自主上限契约](2026-10-23-wallet-customer-limit-contract.md)取代：一次授权持续沿用，管理员配置接口已移除，以下原管理员政策描述为历史设计，以新契约为准。

本地开发实现；未经授权不提交、推送或发布。补充 [钱包契约](2026-10-22-wallet-contract.md)。

## 认证及通用约束

员工基础路径 `/api/v1/stores/{storeId}/wallet`；顾客基础路径 `/api/v1/public/stores/{storeId}/wallet`。身份取现有会话，tenant/store/program/发行主体沿现有钱包隔离，顾客ID和账户ID不得由请求指定。所有新POST/PUT必须 `X-RPB-Wallet: 1` 和 `X-RPB-Wallet-Model: components-v2`，拒绝跨站/同站不同源浏览器提交；响应 `Cache-Control: private, no-store`。金额amount为SGD十进制字符串，限额为整数分，范围1..9999999999999。未知请求字段、非整数限额、字符串形式限额、非法金额返回400。

新交易执行AppGate；本人的状态查询、原员工结果恢复允许商业权限到期后读取。员工消费和恢复均需 `wallet.charge.create` 与 `wallet.collection.confirm`；政策需 `wallet.settings.manage`。

## 接口

| 方法与资源 | 请求 | 响应 |
|---|---|---|
| GET 员工 `/personal-payment-policy` | 无 | `{limitCents:long|null,version:int}` |
| PUT 同路径 | `{limitCents:long|null,version:int,idempotencyKey:string}` | Policy |
| GET 顾客 `/personal-payment-consent` | 无 | `{configured,policyVersion,limitCents,consentedLimitCents,paymentEnabled,requiresConsent,ruleVersion:1}` |
| POST 同路径 | `{policyVersion:int,limitCents:long|null,accepted:boolean,idempotencyKey:string}` | Consent |
| POST 顾客 `/personal-codes` | `{}` | `{type:'customer_personal',code:'WI.…',expiresAt,paymentEnabled,limitCents,consentedLimitCents,policyVersion}` |
| POST 顾客 `/personal-codes/status` | `{code:string}` | `{state:'active'|'expired'|'used'|'confirmation_required',bill:Bill|null}` |
| POST 员工 `/personal-scan-charges` | `{customerCode:string,amount:string,description?:string|null,idempotencyKey:string}` | `{status:'paid'|'confirmation_required',bill:Bill,account:Account,storeId:uuid}` |
| GET 员工 `/personal-scan-charges/result?idempotencyKey=…` | 无 | 原ChargeResult，包括恢复写入的not_submitted结果 |
| POST 员工 `/personal-scan-charges/reconcile` | `{idempotencyKey:string}` | 原结果，或 `{status:'not_submitted',bill:null,account:null,storeId:uuid}` |

`Bill`、`Account`沿既有DTO，说明最长200字符，绑定到个人码消费记录；既有账单业务无需增加说明字段。

## 限额与同意

政策默认null、版本0，不能默认无限额。首次配置、null重新启用和任何限额提高均更新 `consent_required_version`，必须顾客再次同意当前版本/金额；即使先降后升且没有超过较早同意金额，也不能沿用旧同意。降低立即生效。accepted=false撤销付款，并永久关闭已出示码的付款快照，后续同意不会复活旧码。规则版本持久化且须等于当前1。

码60秒，签发不要求资金账户；刷新撤销原码。身份识别、充值、积分沿WI识别，不消耗付款。实际付款同时检查当前政策、当前同意、发行时码快照，限额取三者最小；码快照不可随政策提高而升级。未启用付款的码只能识别顾客。

限额内需要足够全额可用余额，原子调用既有资金分摊/账单/审计；失败回滚账单、分录和幂等记录。超限只创建并唯一绑定原待确认账单，保留余额，不生成消费分录。顾客用既有quote/pay明确确认确定的整笔金额；该类账单余额不足时不得部分扣款。刷新/过期不隐藏已绑定账单，status优先返回账单；原账单付款后状态used。

旧 `/scan-charges` 端点仍存在，但双权限/AppGate校验后稳定返回409 `WALLET_PERSONAL_PAYMENT_CONFIRMATION_REQUIRED`，旧客户端应进入既有逐笔确认付款或刷新使用新WI接口。WC签发/查询和WP接口保留；旧明确确认quote/pay保持兼容。未路由的旧服务方法仅用于历史行为对照回归。

## 幂等与不确定结果恢复

消费命令按tenant/principal/operation/key唯一，摘要包含门店、码哈希、金额和说明，不存明文令牌。成功响应与资金、审计同事务。同键同内容重放原响应，同键不同内容409。不同请求编号重复使用同码返回409，超限也只能产生一张原账单。

GET result只读取已提交结果，404不能证明原请求永远不会到达。客户端持久保留原请求编号及金额/说明等不含令牌的交易意图；二维码令牌只在内存，不持久保存完整请求。刷新缺少令牌或GET404后调用reconcile。reconcile与原命令使用完全相同数据库事务锁：原单已完成则返回原单；不存在则写入安全关闭结果，之后晚到的同键消费不能执行。只有得到not_submitted才清理该请求并要求重新扫码。响应与恢复均校验原员工、tenant及store；其他门店已存在的结果返回404，不泄露也不关闭原单。

## 错误及数据库

400 `WALLET_REQUEST_INVALID`；401 `WALLET_UNAUTHENTICATED`；403既有钱包/AppGate代码；404 `WALLET_NOT_FOUND`；409包括 `WALLET_POLICY_STALE`、`WALLET_PERSONAL_PAYMENT_DISABLED`、`WALLET_ACCOUNT_REQUIRED`、`WALLET_INSUFFICIENT_FUNDS`、`WALLET_PERSONAL_CODE_EXPIRED`、`WALLET_PERSONAL_CODE_USED`、`WALLET_IDEMPOTENCY_CONFLICT`、`WALLET_CLIENT_UPGRADE_REQUIRED`。数据冲突仍映射既有安全通用409，不返回SQL或明文码。

V064新增政策/同意表及WI付款快照/账单唯一绑定；tenant/program/customer与账单门店采用复合外键，金额/版本/状态有边界，保留创建更新时间和既有wallet_audit。原资金分摊、账本及积分表结构不变。锁序为命令key→个人政策program锁→identity advisory锁→account→新bill；身份刷新共用原identity锁；最终账户等待后重新校验当前时间。原逐笔pay继续account→bill，不反向获取个人政策或identity锁。

回退须前后端同时回退；新增表列可保留，不回滚账务数据。发布另行授权。
