# 钱包营销、顾客充值与手动积分实际 API

2026-10-23。本文根据当前控制器、应用服务和持久化代码记录接口实现；不替代原有设计文档。验证结果和发布状态由开发交付记录单独说明。

关联：[总方案](../development/钱包营销与手动积分开发方案.md)、[原 API 设计](2026-10-23-wallet-marketing-points-plan.md)、[数据及迁移方案](../development/WALLET_MARKETING_POINTS_DATA_MIGRATION_PLAN_20261023.md)。

## 1. 身份、作用域与共同约定

以下使用两个前缀：

- `S = /api/v1/stores/{storeId}/wallet`：现有员工会话；平台管理员不能作为门店员工操作钱包。
- `C = /api/v1/public/stores/{storeId}/wallet`：现有顾客 Cookie；tenant/customer 从会话取得，不能通过正文或测试请求头覆盖。

门店关联的钱包 program/发行主体由服务端解析。员工必须具有当前租户、门店范围和对应权限；顾客只能访问本租户本人账户及本计划记录。配置影响整个钱包计划，管理命令还会逐一验证计划内全部关联门店的范围和商业授权。

所有钱包写请求要求 `X-RPB-Wallet: 1`。`Sec-Fetch-Site: cross-site` 和 `same-site` 均拒绝；同源请求按原会话与权限继续校验。通过该请求边界的钱包响应设置 `Cache-Control: private, no-store` 和 `Referrer-Policy: no-referrer`；在过滤器中提前拒绝的CSRF请求仅返回403错误。

金额契约为 SGD、最多两位小数的十进制字符串，例如 `"100.00"`；金额响应为整数分。渠道枚举为 `cash`、`paynow`、`credit_card`，还需属于当前计划；档位列表 channels 返回计划允许的渠道（包括尚未完成收款配置的 PayNow），前端结合钱包 context.paynowConfigured 展示禁用状态及配置提示。未配置有效 PayNow 时，服务端拒绝 PayNow 报价和新充值申请；创建单据时再次验证配置。积分 quantity 必须为严格正整数 JSON 数值，拒绝字符串、小数、0、负数和超过 2147483647 的数量。

营销/顾客单档 DTO 使用独立严格 reader；员工建充值单使用显式字段白名单；积分请求使用拒绝未知字段的 DTO。不能传 offers 数组、quantity、客户端赠送、顾客/租户/计划身份覆盖字段。配置中的档位列表是活动配置，区别于每次充值只选一个档位。

## 2. 资金模型能力声明

以下资金写入口要求 `X-RPB-Wallet-Model: components-v2`，缺失或值不符返回 `409 WALLET_CLIENT_UPGRADE_REQUIRED`：

| 方法 | 路径 |
| --- | --- |
| POST | `S/topups/{id}/confirm` |
| POST | `S/scan-charges` |
| POST | `S/refunds` |
| POST | `S/refunds/{id}/confirm` |
| POST | `S/refunds/{id}/cancel` |
| POST | `S/funding-lots/{lotId}/exit-bonus` |
| POST | `C/bills/{id}/pay` |

控制器先解析真实员工/顾客身份，再检查该能力头。能力头不授予会话、权限、门店范围或资金额度。查询、预览、创建待收款单和付款声明不要求该头。

## 3. 营销配置及活动

下列权限省略 `wallet.` 前缀。

| 方法与路径 | 请求 / 响应重点 | 权限及限制 |
| --- | --- | --- |
| GET `S/marketing/settings` | 配置版本、基础档位、策略、功能开关、影响门店 | `marketing.view`，或 `topup.create` 所用只读入口；有效钱包授权 |
| PUT `S/marketing/settings` | `expectedVersion,baseOffers,deductionPolicy,selfServiceEnabled,campaignsEnabled,pointsEnabled,idempotencyKey` | `marketing.manage`；全部计划门店范围与有效商业授权 |
| GET `S/marketing/campaigns` | `status=all/draft/published/disabled/archived`，`limit` 默认50、最大100，`cursor` | `marketing.view`；响应 `{items,nextCursor}` |
| POST `S/marketing/campaigns` | 完整活动正文及 `idempotencyKey` | `marketing.manage`；只建草稿 |
| GET `S/marketing/campaigns/{id}` | 活动与全部历史 revision | `marketing.view` |
| PUT `S/marketing/campaigns/{id}/draft` | 完整活动正文、`expectedVersion,idempotencyKey` | `marketing.manage`；追加不可变 revision |
| POST `S/marketing/campaigns/{id}/publish` | `draftVersion,expectedVersion,idempotencyKey` | `marketing.manage`；计划锁内检查同级重叠 |
| POST `S/marketing/campaigns/{id}/disable` | `expectedVersion,reason,idempotencyKey` | `marketing.manage`；阻止新报价命中，保留原单快照 |
| POST `S/marketing/campaigns/{id}/archive` | `expectedVersion,reason,idempotencyKey` | `marketing.manage`；历史可追溯，无硬删除 |
| GET `S/topup-offers` | 当前最高优先级活动或基础档位 | `topup.create`；无需配置管理权限 |
| POST `S/topup-quotes` | `accountId,offerId,offerVersion,channel` | `topup.create`；无交易幂等键、报价有效300秒 |

设置返回：

```json
{
  "version": 1,
  "deductionPolicy": "proportional",
  "policyVersion": 1,
  "selfServiceEnabled": true,
  "campaignsEnabled": true,
  "pointsEnabled": true,
  "affectedStoreIds": ["store-uuid"],
  "baseOffers": [
    {"id":"offer-uuid","version":2,"principalCents":10000,"bonusCents":0,"creditTotalCents":10000}
  ]
}
```

基础档位提交项为 `{amount:"100.00",bonus:"0.00"}`；amount必须为金额字符串，bonus如提供非null值也必须为字符串，省略/null按0处理；基础赠送必须为0。设置更新必须提交 `expectedVersion` 及完整三个boolean功能开关，不能用缺失值代替false；`offers` 是 `baseOffers` 的兼容别名，`selfTopupEnabled` 是 `selfServiceEnabled` 的兼容别名。策略只支持 `proportional` 和 `principal_first`。

活动正文示例如下。新建也必须明确发送 `expectedVersion: 0` 和整数 `priority`，不能省略primitive字段。编辑提交当前活动版本：

```json
{
  "name":"充值赠送",
  "startsAt":"2026-11-01T00:00:00Z",
  "endsAt":"2026-12-01T00:00:00Z",
  "priority":10,
  "offers":[{"amount":"100.00","bonus":"10.00"}],
  "terms":"活动说明",
  "expectedVersion":0,
  "idempotencyKey":"unique-command-key"
}
```

`startAt/endAt` 也可作为 `startsAt/endsAt` 的兼容字段；活动名称最长120字、说明最长5000字，结束时间必须晚于开始时间。每组1–50档、同组本金金额不能重复。时间使用带时区 ISO 8601 Instant。

活动返回包含 `id,name,status,version,currentPublishedRevision,revisions,createdAt`，以及供编辑页使用的 `draftVersion,startsAt,endsAt,priority,terms,offers` 扁平字段。扁平字段取最新草稿 revision；当前生效版本取 `currentPublishedRevision`，两者可能不同。发布后再保存草稿时，原发布版本继续有效；最新草稿版本未发布时仍可执行 publish。

生效条件是已发布、开始时间已到且结束时间未到；取最高 priority。同 priority 已发布时间段采用半开区间，重叠时拒绝。发布必须选择最新草稿且尚未过期；禁用/归档等命令与报价共享计划配置锁。分页游标为服务端返回的稳定 createdAt/id 位置，客户端原样回传，`nextCursor=""` 表示无下一页。

初始化只写基础档100/300/500/1000元与草稿赠送模板100送10、300送50、500送80、1000送200；不自动发布，不给历史充值追溯赠送。

## 4. 单档报价与员工充值

`GET .../topup-offers` 返回 `campaignId,campaignVersion,campaignName,deductionPolicy,policyVersion,channels,offers,selfServiceEnabled`。档位为 `id,version,principalCents,bonusCents,creditTotalCents`。无活动返回基础档，campaign字段为空，赠送0。

报价响应包含 `quoteId,offerId,offerVersion,campaignId,campaignVersion,principalCents,bonusCents,creditTotalCents,channel,deductionPolicy,policyVersion,expiresAt`。报价绑定租户、计划、门店、身份及用途；员工报价额外绑定指定账户，顾客报价绑定本人。报价过期、档位/活动/配置已变化或渠道不再允许时必须重新报价，不能静默变更赠送。

员工创建充值单保留 `POST S/topups`，严格互斥两种正文：

```json
{"accountId":"uuid","quoteId":"uuid","idempotencyKey":"uuid"}
```

```json
{"accountId":"uuid","amount":"100.00","channel":"cash","idempotencyKey":"uuid"}
```

有活动时第二种返回 `409 WALLET_TOPUP_OFFER_REQUIRED`。普通金额模式赠送0；报价模式不可同时包含 amount/channel，客户端赠送、quantity、数组和未知字段一律400。建单只创建待收款状态，不产生余额、资金批次或赠送入账。

`POST S/topups/{id}/confirm` 权限为 `wallet.collection.confirm`，正文沿用 `channel,amount,collectionAccount,receiptReference,terminalCode,note,verified,idempotencyKey`。amount 必须等于原单实收本金，渠道必须为原渠道；赠送与策略使用建单快照。现金原有凭据选填规则、非现金实际收款凭据规则保持。

充值响应保留 `amountCents` 为实收本金，增加 `principalCents,bonusCents,creditTotalCents,deductionPolicy,origin,customerDeclaredPaidAt,creditedAt,offerSnapshot,policySnapshot`。`origin` 为 `staff` 或 `customer`。

员工 `GET S/topups` 使用 offset分页，每页100，status 为 `all/pending/completed/closed`，可加 `origin=staff/customer` 和 `customerId=uuid`。兼容 `customer=uuid`，两个顾客参数同时出现必须一致。仍按当前tenant/store筛选，保留原 createdAt/id 排序；offset 范围0–100000。

## 5. 顾客自助充值

| 方法与路径 | 关键请求 / 行为 |
| --- | --- |
| GET `C/topup-offers` | 本人登录、有效钱包计划和自助开关；禁用时返回明确错误 |
| POST `C/topup-quotes` | `offerId,offerVersion,channel`；不接 accountId/customerId/amount/bonus/quantity |
| POST `C/topups` | `quoteId,idempotencyKey`；必要时同事务幂等开本人零账户 |
| GET `C/topups` | offset/status；每页100，按createdAt/id稳定顺序返回本计划本人记录 |
| GET `C/topups/{id}` | 本人原单详情；跨顾客/计划记录不可读取 |
| GET `C/topups/{id}/paynow` | 仅本人待收款PayNow单；按原门店与本金生成码，附核实提示 |
| POST `C/topups/{id}/declare-paid` | `note?,idempotencyKey`；仅登记本人首次声明时间 |
| POST `C/topups/{id}/cancel` | `reason?,idempotencyKey`；仅尚未核实收款的待单 |

新申请、报价和付款声明/取消按各自入口检查有效商业授权；到期本人历史GET仍可读。尚无实收的充值单，产品到期后不能继续展示新收款二维码。PayNow码和顾客付款声明不代表到账；顾客不能确认实收。

每计划每顾客只能有一笔未终结自助申请；exception/cancelled/credited不占待单额度。冲突返回 `409 WALLET_PENDING_TOPUP_EXISTS` 和本人可恢复的 `pendingTopupId`。同报价已生成本人订单时恢复该单，不创建第二单；员工重复消费报价返回409及可见原单标识。取消与商户确认通过原单锁互斥。

限流配置：报价每身份/计划每分钟30次，申请每顾客/计划每分钟5次、每IP/计划每分钟30次；属性分别为 `rpb.wallet.marketing.quote-per-minute`、`apply-per-minute`、`apply-ip-per-minute`。超限429并返回 `Retry-After: 60`。

## 6. 资金组件预览与权益退出

| 方法与路径 | 正文 / 权限 |
| --- | --- |
| GET `S/accounts/{accountId}/funding-lots` | `wallet.view`；读取当前账户批次 |
| POST `S/charge-previews` | `accountId,amount,billId?`；`wallet.charge.create`，有效商业授权 |
| POST `S/refund-previews` | `sourceType,sourceId,amount?,mode?`；`wallet.refund.manage` |
| POST `S/funding-lots/{lotId}/exit-bonus` | `accountId,expectedVersion,reason,idempotencyKey`；`wallet.refund.manage`及组件能力头 |

扣款预览返回 `accountId,accountVersion,principalCents,bonusCents,allocations`；accountVersion 是本次预览对应的账户版本，客户端确认时将其作为 expectedAccountVersion 提交，并在版本变化时重新预览。amount 是拟扣总额，实际可用不足时预览可扣部分；指定 billId 时核对该账户、账单剩余及未应用钱包状态。预览不建账单、不消费WC、不产生分录。

退款预览 `sourceType` 为 `top_up/bill/collection_exception`。混合支付账单按 `WalletMoney.allocate` 在钱包及各原渠道剩余可退额度之间按比例分配，再把钱包部分按原消费批次分配拆回本金/赠送；与实际退款创建使用相同规则，不能优先把整笔退款都视为钱包返还。返回 `amountCents,walletCents,cashPrincipalRefundCents,principalReturnCents,bonusReturnCents,bonusRevokeCents,externalRefundCents,accountId,accountVersion,mode,fundingLots`。

创建退款 `POST S/refunds` 使用：

```json
{
  "sourceType":"top_up",
  "sourceId":"topup-uuid",
  "mode":"remaining_lot_exit",
  "expectedAccountVersion":8,
  "reason":"退出剩余充值权益",
  "idempotencyKey":"unique-command-key"
}
```

普通金额退款还提交 amount 字符串。API要求 expectedAccountVersion，由预览的 accountVersion 取得；创建时再次核对版本。活动充值退出须 `remaining_lot_exit`，本金取该批次当前可退剩余，赠送随退出撤销，不能作为现金退回。普通本金充值和账单退款保留原金额模式。创建退款仍需原渠道凭证可退额度；确认/取消接口沿用 `/refunds/{id}/confirm` 和 `/refunds/{id}/cancel`。

预览没有独立 previewId，也不代替退款授权；提交按账户版本及锁内实时余额重校。批次本金为0、没有冻结且仍有赠送时，使用 exit-bonus，只撤赠并记权益退出，不产生0元现金退款。历史本金池只能按真实旧原单额度和池内可用本金退款。

账户响应增加本金/赠送余额及冻结；Entry、Bill、退款响应增加组件字段。合计金额与现金实收分别表示，消费退款按原批次分配恢复，不使用当前活动比例。

## 7. 独立手动积分

| 方法与路径 | 请求 / 权限 |
| --- | --- |
| GET `S/points/context` | `wallet.points.view`；返回当前是否可写及停用原因 |
| GET `S/customers/{customerId}/points` | `wallet.points.view`；无账户返回余额0、accountId=null |
| GET `S/customers/{customerId}/points/entries` | `wallet.points.view`；offset，固定每页50 |
| POST `S/customers/{customerId}/points/grants` | `quantity,reason,note?,externalReference?,idempotencyKey`；`wallet.points.grant` |
| POST `S/customers/{customerId}/points/deductions` | `quantity,reason,note?,expectedVersion,idempotencyKey`；`wallet.points.adjust` |
| POST `S/customers/{customerId}/points/redemptions` | `quantity,redemptionDescription,note?,externalReference?,expectedVersion,idempotencyKey`；`wallet.points.redeem` |
| POST `S/points/entries/{entryId}/reverse` | `reason,expectedVersion,idempotencyKey`；`wallet.points.reverse`，且角色为tenant_admin/store_manager并有原发生门店范围 |
| GET `C/points` | 本人顾客会话 |
| GET `C/points/entries` | 本人顾客会话；offset，固定每页50 |

积分计划由当前钱包计划同租户同发行主体绑定解析，不接收正文 programId/issuerId。新建积分命令要求有效wallet授权、启用积分开关、门店/计划有效；首次开户要求共享顾客已有手机号。扣减/兑换余额不足时事务回滚，不留下无意义的空账户。

积分quantity和版本使用严格整数解析；reason最长500字、note最长2000字、externalReference最长200字、兑换说明最长1000字。扣减、兑换和冲正要求当前expectedVersion；grant不需要版本但串行账户锁校验余额上限。流水offset范围0–1000000，按accountVersion倒序。

命令统一返回 `{account,entry,redemption}`；account包含 `accountId,programId,walletProgramId,tenantId,issuerId,customerId,balance,version`。冲正是追加相反流水，不能冲正冲正记录；同一原记录至多冲正一次。外部参考号仅追溯，不是幂等键。兑换不接货币、现金价值、配送地址，不改变消费账单支付状态。

积分历史读取不受产品到期或积分开关关闭影响。原记录冲正属于经理善后，当前实现允许到期/功能停用后继续，但仍检查角色、权限、租户、计划和原门店范围。资金退款、充值或消费不自动加扣积分。

## 8. 幂等、错误与实现边界

钱包有副作用命令使用现有 wallet_commands；积分使用独立 loyalty_commands 和 loyalty_audit。幂等键为1–100字符；按tenant、身份、操作、key串行，同键同参恢复已保存成功结果，同键异参409。钱包摘要包括当前store及完整命令，积分摘要还显式包括program。失败事务不保存成功快照；权限、商业授权和功能开关仍按具体入口执行，不能依赖过期后的写命令重放绕过门禁。

报价/预览不使用交易幂等键；报价不是充值授权。客户未知提交结果应保留原幂等键重试。查询最新账户与读取旧幂等结果是两种语义，旧成功响应不会用当前余额替换历史余额。

错误包络为 `{code,message}`；唯一待单冲突增加 `pendingTopupId`，限流增加 Retry-After 响应头。核心错误：

| HTTP / code | 处理方式 |
| --- | --- |
| 400 `WALLET_TOPUP_SINGLE_OFFER_REQUIRED` | 删除非法字段，仅提交单档报价或合法普通金额 |
| 400 `WALLET_REQUEST_INVALID` / `POINTS_REQUEST_INVALID` | 校正必填、金额、版本或参数 |
| 401 `WALLET_UNAUTHENTICATED` | 使用对应员工/顾客会话重新登录 |
| 403 `WALLET_FORBIDDEN` / App Gate原因 | 检查身份、门店、权限、产品状态 |
| 403 `WALLET_SELF_TOPUP_DISABLED` | 顾客自助尚未开启 |
| 404 `WALLET_NOT_FOUND` / `POINTS_NOT_FOUND` | 记录不存在或不在可见范围 |
| 409 `WALLET_IDEMPOTENCY_CONFLICT` / `POINTS_IDEMPOTENCY_CONFLICT` | 同键内容改变；先恢复原交易 |
| 409 `WALLET_TOPUP_OFFER_REQUIRED` | 活动生效，选择档位后报价 |
| 409 `WALLET_OFFER_CHANGED` / `WALLET_TOPUP_QUOTE_EXPIRED` | 刷新档位并重新报价 |
| 409 `WALLET_TOPUP_QUOTE_CONSUMED` | 报价已建单；恢复本人可见原单 |
| 409 `WALLET_PENDING_TOPUP_EXISTS` | 恢复pendingTopupId |
| 409 `WALLET_CAMPAIGN_CONFLICT` | 刷新版本或修正同级时间重叠 |
| 409 `WALLET_CLIENT_UPGRADE_REQUIRED` | 刷新支持组件资金模型的客户端 |
| 409 `WALLET_ALLOCATION_STALE` / `WALLET_FUNDING_REFUND_NOT_ALLOWED` | 重做预览、核对批次/冻结与退款模式 |
| 409 `WALLET_POINTS_DISABLED` / `CUSTOMER_PHONE_REQUIRED` | 启用积分或先补统一顾客手机号 |
| 409 `POINTS_INSUFFICIENT` / `POINTS_LIMIT_EXCEEDED` | 检查积分余额与数量上限 |
| 409 `POINTS_VERSION_STALE` / `POINTS_ENTRY_ALREADY_REVERSED` | 刷新积分版本或读取原冲正记录 |
| 429 `WALLET_REQUEST_RATE_LIMITED` | 按Retry-After重试 |

业务状态冲突、数据库并发约束冲突、账务不一致另沿用原钱包/积分错误。客户端不能将任意409自动换新键重发，避免重复实际收款或退款。

当前实现的范围边界：功能开关不是新增订阅；活动不跨发行主体；无积分现金兑换、自动加扣积分、线上支付回调或自动实收确认。使用新组件资金写入后，回退应用必须仍理解本金/赠送及批次模型。

## 9. 源码入口与静态审查要点

- 营销：`wallet.marketing` 下控制器、配置服务、报价服务、顾客申请服务、严格请求reader及repository。
- 资金：`wallet.funding`、`WalletTopupService`、`WalletRefundService`、`WalletStaffController`、`WalletModelGuard`。
- 积分：`wallet.integration.WalletPointsAdapter` 负责wallet授权及计划映射，`loyalty` 自己维护账户、流水、幂等及审计。
- 本地安全：`LocalRuntimeSecurityConfiguration` 按HTTP方法和具体路径放行至控制器，服务端继续校验身份/权限；`WalletRequestSecurityFilter` 保留CSRF与无敏感缓存边界。
- 数据：V059资金组件、V060营销与报价、V061独立积分。跨tenant/program/account/lot通过复合约束，活动发布、资金操作、积分操作各有自己的事务锁域。

静态审查应继续关注：参数绑定先于控制器身份执行时的非法正文错误差异；代码外部调用资金服务必须保留事务及账户锁；幂等重放仍受各入口当前门禁约束；feature配置与既有program/channel配置须避免未约定的并发修改；SQL完整性约束及旧数据切换需要真实PostgreSQL证据，不能由接口文档推导运行结论。

## 充值体验优化（2026-10-23）

顾客选择单一档位及付款方式后直接点击“确认充值”。前端在同一次操作内调用既有报价 API，再使用 quoteId 创建待核实充值单，不展示独立获取报价按钮或报价有效期。报价失败不得创建充值单；网络不确定重试复用未过期 quoteId 和对应幂等键。门店、档位或付款方式变化后，旧响应不能用于新申请。商户端保留原有报价预览；所有入账仍须商户核实本金实收。接口路径、权限、金额字段和数据库结构不变。
