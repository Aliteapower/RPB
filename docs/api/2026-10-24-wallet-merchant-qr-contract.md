# 商户消费出码与顾客确认积分兑换

保留商户扫个人码和手机号办理。新增商户出码页面：先确认顾客；消费复用现有创建账单、WB 出码、顾客扫描／报价／明确确认付款接口；不改变免确认个人码流程。积分码 WR 与金额完全分开，只能由绑定顾客确认兑换，不抵现金。

员工基础 /api/v1/stores/{storeId}/wallet，顾客基础加 public/。

| 方法 | 员工资源 | 内容 |
|---|---|---|
| POST | /points-redemptions | customerId、quantity（正整数≤1e9）、description、idempotencyKey；只创建待确认意图 |
| GET | /points-redemptions/{id} | 状态和关联积分 entryId，无明文令牌 |
| POST | /points-redemptions/{id}/code | 返回 WR 码和 expiresAt，120秒；仅存散列，刷新旧码失效 |
| POST | /points-redemptions/{id}/cancel | idempotencyKey，取消未完成意图 |

顾客 POST /points-redemption-code {code} 只解析本人意图；GET /points-redemptions/{id} 查看本人内容、余额与 expectedVersion；POST /points-redemptions/{id}/confirm {expectedVersion,idempotencyKey} 明确确认后，原子锁定意图并调用既有积分兑换，保存 entryId。已完成重试返回同一 entry，不重复扣分。请求未知字段、非整数及非法值400；归属／租户／门店／计划不匹配404或403；过期／取消409；余额不足或版本变化沿既有积分错误。已完成原交易可在码过期后按 ID 查看，未完成过期不可确认。

员工复用 wallet.points.view、wallet.points.redeem，AppGate、租户／门店与积分开关；顾客仅本人的会话。mutation沿现有钱包同源和请求头保护，精确增加本地安全白名单，不放宽整段路径。消费与积分均需本次顾客明确确认，扫码不自动记账。

V065仅新增 wallet_points_redemption_requests 意图表及租户／门店／顾客索引，不新增积分账本；完成流水仍在 loyalty_entries/redemptions，并由统一交易列表展示。取消／过期意图不算积分支出。回退应用后保留附加表及历史，禁止删除已兑换流水或回写余额；旧 WB、个人码和交易详情接口兼容。
