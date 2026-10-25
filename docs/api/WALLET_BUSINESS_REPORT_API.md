# 钱包收银台营业报表 API

`GET /api/v1/stores/{storeId}/wallet/business-report?period=month&date=2026-10-01`

使用现有商户登录。仅 `wallet.reconcile.view` 且具备当前门店访问权的同租户员工可查询；平台管理员身份不允许。积分项还要求 `wallet.points.view`。只读历史遵循钱包现有查询规则，不要求启用新交易权限。响应 `Cache-Control: private, no-store`，不存在公共顾客接口。

参数仅支持 `period=day|month|year` 与严格 ISO `date=YYYY-MM-DD`，日期代表所选自然期间中的一天，年份范围 1900–9999。未知参数、错误日期或期间返回 400 `WALLET_REQUEST_INVALID`；未登录 401 `WALLET_UNAUTHENTICATED`，无权限/跨租户/未授权门店 403 `WALLET_FORBIDDEN`；门店不存在遵循 WalletDirectory 的 404。

响应示例：

```json
{"period":"month","startDate":"2026-10-01","endDate":"2026-10-31","timezone":"Asia/Singapore","currency":"SGD","rows":[{"bucket":"2026-10-25","metric":"topupPrincipal","channel":"","channelName":"","amount":10000,"count":1}]}
```

日/月报 bucket 为当地日期；年报 bucket 为每个月第一天。起止日均包含，数据库查询为门店时区 `[开始日零点,结束日次日零点)`。不分页、不截断。无交易 `rows=[]`。金额单位分，`points*` 为积分整数；count 为各项目实际发生记录数，项目可能是同一交易的主项与子项，不可相加计算总笔数。

主项复用交易历史投影，仅 completed，以实际记账/最终完成时间归期。消费保持原始已付金额，退款独立按退款完成期间列示；充值本金与赠送、权益分离；赠送移除不是现金支出；积分只含与钱包绑定的积分计划。

metric：consumption、walletPayment、spentPrincipal、spentBonus、externalPayment、topupPrincipal、topupBonus、topupCredit、consumptionRefund、topupRefund、bonusReversal、pointsGrant、pointsDeduct、pointsRedeem、pointsReversalIn、pointsReversalOut。

另有 consumptionReceipt / topupReceipt：按钱包实际收款证据的创建时间统计，排除异常收款，按 channel 与历史 channelName 分组。它们反映现金流收款证据，可能与业务最终完成期间不同；不能再次加到消费或充值上。自定义名称原样保留，历史空名称由前端按 channel 类型本地化。

前端大项过滤只操作本次完整查询结果：消费、充值、退款与冲正、积分；表格、卡片、导出同步过滤。无交易项目按 count 判定隐藏，不能仅依据 amount 是否为零。无迁移、写请求、幂等键或重放操作；不改变现有接口。
