# 钱包充值营销、自助申请与手动积分 API 设计

2026-10-23。**仅设计，接口尚未实现。**以[总方案](../development/钱包营销与手动积分开发方案.md)的已确认范围和设计默认为准。

## 1. 共同契约

- 员工前缀 `S=/api/v1/stores/{storeId}/wallet`，使用现有员工会话；顾客前缀 `C=/api/v1/public/stores/{storeId}/wallet`，使用现有顾客 Cookie，顾客ID只取会话。
- 保留 `X-RPB-Wallet: 1`、same-origin写操作边界、无敏感缓存、原错误映射。新URL加入本地安全精确白名单，不能放开整个 public 或 wallet 前缀。
- SGD输入为最多两位小数十进制字符串；响应金额为整数分。积分 quantity 为严格正整数JSON数值，拒绝小数截断、负数、0及超上限。
- 有副作用的业务命令使用 `idempotencyKey`（1–100字符），身份+租户+动作+门店+完整请求摘要共同约束；同键同参返回原结果，同键异参409。只在成功事务保存结果；客户端未知结果保留原键重试。
- 报价为有时效的计算凭据，不是资金命令，不使用交易幂等键；报价限流且校验所有者、门店、计划和用途，一份报价只能建立一个充值单。
- 对单档充值DTO启用字段白名单：明确拒绝 offers数组、quantity、客户端赠送金额和不允许的身份字段，不能依赖Jackson默认忽略未知字段。
- 为资金组件改造增加客户端能力 `X-RPB-Wallet-Model: components-v2`。组件底座启用后，受影响的充值确认、扣款和退款写入口缺该能力时返回409升级提示；账户/历史只读继续可用。只是声明不授予身份、角色或额度；各请求仍完整服务端校验。
- 活动管理、顾客申请及积分创建受有效 wallet 商业授权约束。产品到期时成功历史单可通过本人/员工GET恢复结果；命令重放仍遵守身份、权限及原用例商业门禁，不承诺到期后所有新交易写入口都可调用。

## 2. 配置与活动（员工）

| 方法与路径 | 核心输入 / 输出 | 权限及作用 |
| --- | --- | --- |
| GET S/marketing/settings | 基础档位、默认分摊策略、配置版本、功能开关、影响门店 | marketing.view 或 topup.create 的必要只读子集 |
| PUT S/marketing/settings | expectedVersion、基础单档列表、deductionPolicy、idempotencyKey | marketing.manage，校验全部受影响门店范围 |
| GET S/marketing/campaigns | 分页、状态筛选，活动摘要及版本 | marketing.view |
| POST S/marketing/campaigns | 名称、时间、优先级、档位列表、idempotencyKey | marketing.manage，创建草稿 |
| GET S/marketing/campaigns/{id} | 指定活动/版本详情，含历史只读版本 | marketing.view |
| PUT S/marketing/campaigns/{id}/draft | expectedVersion、完整草稿、idempotencyKey | marketing.manage，仅改草稿；已发布版本创建新草稿版本 |
| POST S/marketing/campaigns/{id}/publish | draftVersion、expectedVersion、idempotencyKey | marketing.manage，计划级锁下校验时间及同级重叠 |
| POST S/marketing/campaigns/{id}/disable | expectedVersion、reason、idempotencyKey | marketing.manage，阻止新报价/新单，保留已建单快照 |
| POST S/marketing/campaigns/{id}/archive | expectedVersion、reason、idempotencyKey | marketing.manage，不删除历史引用 |
| GET S/topup-offers | 当前唯一命中活动或基础档位、版本、策略、渠道 | topup.create，不要求收银员拥有配置权限 |
| POST S/topup-quotes | accountId、offerId、offerVersion、channel | topup.create，返回5分钟报价；不改钱包 |

上表权限为 `wallet.` 前缀。每个活动内部金额档位不重复，最多50档；档位的停用/修改产生新版本，不原地修改已引用数据。GET列表默认每页50，最高100，游标包含稳定createdAt/id，不接任意SQL排序字段。

## 3. 顾客自助充值

| 方法与路径 | 核心输入 / 输出 | 约束 |
| --- | --- | --- |
| GET C/topup-offers | 当前活动或基础档位、全部金额组件、允许渠道与说明 | 已登录本人、有效计划；无活动不为空，使用基础档位 |
| POST C/topup-quotes | offerId、offerVersion、channel | 只支持单档；不接受accountId/customerId/amount/quantity/bonus |
| POST C/topups | quoteId、idempotencyKey | 本人零账户必要时同事务开通；活动重新校验；返回本人待单 |
| GET C/topups | 本人充值记录、来源、状态、金额快照、付款声明 | 身份隔离；到期仍可读 |
| GET C/topups/{id} | 原单详情及资金状态 | 非本人/跨计划404 |
| GET C/topups/{id}/paynow | 该待单本金对应PayNow码和商户核实提示 | 继承现有配置/原单善后限制，码不代表到账 |
| POST C/topups/{id}/declare-paid | note?、idempotencyKey | 仅登记本人声明；不设置credited、无资金/赠送流水 |
| POST C/topups/{id}/cancel | reason?、idempotencyKey | 仅本人尚未确认实收的待单；取消与商户确认互斥 |

报价响应示例：

```json
{
  "quoteId": "uuid",
  "offerId": "uuid",
  "offerVersion": 2,
  "campaignId": "uuid",
  "campaignVersion": 3,
  "principalCents": 10000,
  "bonusCents": 1000,
  "creditTotalCents": 11000,
  "channel": "cash",
  "deductionPolicy": "proportional",
  "policyVersion": 1,
  "expiresAt": "2026-10-23T08:05:00Z"
}
```

基础档报价 campaignId/version 为null、bonusCents=0。上述金额和策略都是服务端响应，客户端建单只传 quoteId。报价过期或被活动更新替换时不保留旧价，建单成功后则永久保留订单快照，不受后续活动变更影响。

报价重复使用：同一报价及同一所有者已经生成订单时，恢复该原单或返回含可见原单ID的409；绝不能创建第二笔。不同身份不得据此读取原单。顾客待单唯一冲突统一409并给本人可恢复的pendingTopupId。

建单初始限流建议每顾客/计划每分钟5次、每IP每分钟30次，服务端配置化；同时保证数据库未终结自助申请唯一。报价每身份/计划每分钟30次；不能仅靠按钮disabled。

## 4. 员工充值兼容与实际收款

既有 POST S/topups 扩展为互斥的两种请求：

```json
{"accountId":"uuid","quoteId":"uuid","idempotencyKey":"uuid"}
```

```json
{"accountId":"uuid","amount":"100.00","channel":"cash","idempotencyKey":"uuid"}
```

第一种为单档报价创建，渠道和P/G由报价确定；第二种只用于无活动的原有员工普通充值，赠送必为0。有生效活动但使用旧amount请求时409 `WALLET_TOPUP_OFFER_REQUIRED`，不静默忽略活动或偷偷赠送。互斥字段同时提交返回400。

既有 Topup DTO 保留 `amountCents` 为**应实收本金**；增加 `principalCents,bonusCents,creditTotalCents,offerSnapshot,policySnapshot,origin,customerDeclaredPaidAt,creditedAt`。旧历史缺少组件时映射 principal=amount，bonus=0。订单创建时 source/customer 与快照不可编辑。

POST S/topups/{id}/confirm 保留现有完整收款核实正文：`channel,amount,collectionAccount,receiptReference,terminalCode,note,verified,idempotencyKey`。amount必须等于原单本金，不能等于本金+赠送。确认只接受原渠道；现金凭据选填规则不变，其他渠道仍必填且验证实际收款标识。

确认只能使用已创建单的冻结快照，不查当前活动重算赠送。入账响应保持 TopupResult，Account增加组件字段；同单重试只能保留一次实收、一批权益、一次入账与一次审计。

员工GET S/topups增加origin/customer/status筛选，必须保持稳定分页及原默认列表语义；旧待单补齐legacy_no_campaign快照，不追溯赠送。

## 5. 账户、扣款、报价与退款增量

- Account保留 `balanceCents,reservedCents,version`，增加 `principalBalanceCents,bonusBalanceCents,principalReservedCents,bonusReservedCents`。总余额=两组件之和。
- Entry增加 `principalCents,bonusCents,componentAction,allocations` 或受控详情链接；交易列表显示合计，详情展示真实批次拆分；历史分录映射本金，不能重算为新活动比例。
- Bill增加 `principalPaidCents,bonusPaidCents`，`walletPaidCents`仍为两者合计；外部补付、剩余应付原语义不变。
- Quote增加 `allocationVersion,allocationDigest,principalDebitCents,bonusDebitCents` 和批次方案，客户端不自行传拟扣组件。原顾客quoteId确认只按已冻结方案；服务端校验账户/批次版本及有效期。
- POST S/scan-charges沿用原WC授权、expectedAccountVersion及幂等，执行统一分摊器。商户预览可新增 POST S/charge-previews 读取服务端拟分摊；请求只含accountId/amount/billId?，预览不消费WC、不建账单。实际确认再次检查版本；不能把预览当扣款授权。
- 原WP/WC/WB签发、识别和状态查询协议不变。新增组件不能使WP获得直接扣款权。
- 新增 POST S/refund-previews（refund.manage）：只接受sourceType/sourceId/amount?/mode，返回现金退款、本金返还、赠送返还/撤销、冻结分项及版本。预览不冻结、不发钱。
- POST S/refunds保留旧普通退款，赠送批次退出使用 `mode=remaining_lot_exit` 与预览标识。旧amount为原单退款金额，不能把赠送计成现金；若附送有赠送批次时无mode/组件能力，则409要求新版预览。
- Refund保留 `amountCents`（退款业务金额）和 `walletCents`（钱包权益变动合计），增加 `cashPrincipalRefundCents,principalReturnCents,bonusReturnCents,bonusRevokeCents,reservationComponents`，按source/mode明确返回。例：退本金50并撤赠5，amount=5000、walletCents=5500、cashPrincipalRefund=5000、bonusRevoke=500。
- 若批次只剩赠送、可退本金=0，使用 POST S/funding-lots/{id}/exit-bonus（refund.manage，确认原因/expectedVersion/idempotencyKey），撤销该批次剩余赠送并记录独立权益退出，不创建0金额现金退款或外部退款凭证。
- 已成功旧幂等响应缺少组件字段时，按其当时余额解释为全本金，不把当前账户余额填进历史缓存响应。新读取接口返回最新账户；两种语义分别测试。

## 6. 手动积分（由钱包入口访问独立loyalty用例）

| 方法与路径 | 输入 / 输出 | 权限 |
| --- | --- | --- |
| GET S/customers/{customerId}/points | 积分计划、账户、余额、版本；无账户返回0及accountId=null | wallet.points.view |
| GET S/customers/{customerId}/points/entries | 顾客积分流水及分页 | wallet.points.view |
| POST S/customers/{customerId}/points/grants | quantity、reason、note?、externalReference?、idempotencyKey | wallet.points.grant |
| POST S/customers/{customerId}/points/deductions | quantity、reason、note?、expectedVersion、idempotencyKey | wallet.points.adjust |
| POST S/customers/{customerId}/points/redemptions | quantity、redemptionDescription、note?、externalReference?、expectedVersion、idempotencyKey | wallet.points.redeem |
| POST S/points/entries/{entryId}/reverse | reason、expectedVersion、idempotencyKey | wallet.points.reverse |
| GET C/points | 本人积分余额与计划；无账户显示0 | 本人会话 |
| GET C/points/entries | 本人积分与兑换明细 | 本人会话 |

积分账户可以在首次合法grant/deduction/redemption命令中幂等创建；无账户扣减仍返回余额不足，不留下无意义资金账户。加分不传expectedVersion也必须串行账户锁并检查上限；扣分/兑换/冲正使用页面当前版本，变化时提示重新核对。

命令响应统一为 `PointsResult{account,entry,redemption?}`；积分计划ID由当前门店关联解析，禁止正文改issuerId或programId。兑换不接amount、currency、cashValue、shippingAddress，不改变消费账单支付状态。外部参考号只作商户追溯，不默认全局唯一或作为幂等键。

loyalty维护自己的幂等结果及审计；可复用窄的摘要/锁等技术工具，不依赖WalletCommandRepository或钱包资金流水。错误从loyalty应用层映射到钱包API表示，不能在内部抛钱包仓储异常。

## 7. 错误设计

| HTTP / code（拟新增） | 场景与客户端动作 |
| --- | --- |
| 400 WALLET_TOPUP_SINGLE_OFFER_REQUIRED | 多档、数量、客户端金额/赠送等非法组合；回到单档选择 |
| 409 WALLET_TOPUP_OFFER_REQUIRED | 活动生效时旧普通amount请求；提示升级/选档 |
| 409 WALLET_OFFER_CHANGED | 档位、版本、活动时段变化；刷新档位 |
| 409 WALLET_TOPUP_QUOTE_EXPIRED | 报价超时；重新报价，不能自动改送额后提交 |
| 409 WALLET_PENDING_TOPUP_EXISTS | 本人已有未终结申请；恢复原单 |
| 409 WALLET_CAMPAIGN_CONFLICT | 同级活动时间重叠或配置并发版本变化 |
| 409 WALLET_CLIENT_UPGRADE_REQUIRED | 旧客户端无法表达组件账务；刷新客户端 |
| 409 WALLET_ALLOCATION_STALE | 原分摊/批次变更；重新核对 |
| 409 WALLET_FUNDING_REFUND_NOT_ALLOWED | 非该批次可退本金、活动部分退限制或权益正冻结 |
| 409 POINTS_INSUFFICIENT | 扣减/兑换/撤销加分后将不足 |
| 409 POINTS_LIMIT_EXCEEDED | 超积分数量/账户上限 |
| 409 POINTS_ENTRY_ALREADY_REVERSED | 原记录已冲正，返回可见原冲正参考 |
| 409 POINTS_VERSION_STALE | 积分页面版本变化 |
| 429 WALLET_REQUEST_RATE_LIMITED | 顾客申请/报价超限，含Retry-After |

沿用既有401/403/App Gate/404与400非法请求、409幂等冲突/冻结/账务不一致等错误；积分内部错误通过同一 `{code,message}` 包络返回。冲突时只返回当前身份可见的原单，不暴露其他租户或顾客信息。

## 8. API Review Report（设计阶段）

### Endpoint / Contract Check
新增员工、顾客和积分路径、DTO与响应组件均在本文列明；单档请求、首充本人开户、资金版本兼容和退款字段有明确语义。没有把实体直接作为DTO。

### Permission Check
所有新用例均需wallet商业/原单善后策略与身份范围校验；配置权限、实收确认权、加分/兑换/调整/冲正分别授权。客户自助不伪装员工。

### Error Mapping / Replay / Idempotency
错误表、同键同参、同键异参、旧报价、已建单、唯一待单和原单恢复规则已定义。积分冲正对原entry唯一约束及资金命令原子性见数据文档。

### Compatibility / Missing Items
保留旧字段含义并增加组件能力协商；具体控制器DTO、逐路径本地白名单和OpenAPI实现由开发阶段生成并验证。本轮没有修改运行白名单或声称接口可调用。
