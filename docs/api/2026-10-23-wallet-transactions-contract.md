# 钱包统一交易只读 API

2026-10-23 分类更新：界面改为消费／充值／积分文件夹及全部／收入／支出筛选，参数、方向口径和分页约束见 [交易分类合约](2026-10-23-wallet-transaction-folders-contract.md)。统一入口和业务详情路由不变。

列表 GET `/api/v1/stores/{storeId}/wallet/transactions`，顾客 GET `/api/v1/public/stores/{storeId}/wallet/transactions`。详情在列表地址追加 `/{sourceType}/{id}`。参数与字段遵循 `docs/superpowers/plans/2026-10-23-wallet-transactions.md`。

明确响应：`capabilities:{money,points,refunds,reversals}`；`period:{mode,startDate,endDate,timeZone}`；`events:[{type,occurredAt,label}]`；`related` 为同权限 Item 数组。sourceType 为 bill/topup/refund/points/money_reversal。points 冲正使用 type=reversal；money_reversal 独立赠送退出也使用 type=reversal，但统计分开。

身份来自员工会话或顾客 cookie。顾客只查本人，拒绝 customerQuery/customerId 或未知参数。员工资金 wallet.view、积分 wallet.points.view、退款及赠送退出 wallet.refund.manage。历史无需仍有新增交易商业资格。所有门店属于本人租户且员工已授权或顾客有本人记录；当前门店包含在选项中。顾客响应隐藏手机号及员工名字。

默认当前门店当前月份；日期结束日包含。每笔交易按来源门店时区解释日期边界。completed 消费保持原账单金额；已退款关闭的 paid 账单仍 completed。汇总仅完整筛选中的 completed 记录，不包含分页限制。资金分录作为支付/入账证据，绝不重复成为消费充值主记录。积分不计金额。

游标为服务器过滤摘要与最后一行排序键的编码；修改筛选或身份/权限后旧游标 400。不提供写操作及幂等键；GET 可重放，不产生资金或积分变化。列表、总数与汇总使用 readOnly REPEATABLE_READ 一致视图。详情不受月份限制，关联记录继续执行同等类型/所有权限制。

错误：WALLET_UNAUTHENTICATED 401；WALLET_FORBIDDEN 403；WALLET_NOT_FOUND 404；WALLET_REQUEST_INVALID 400。

已删除旧 GET 列表：员工 `/bills`、`/topups`、`/refunds`、`/accounts/{id}/entries`、`/customers/{id}/points/entries`；顾客 `/bills`、`/topups`、`/accounts/{id}/entries`、`/points/entries`。原 `/:id` 详情、余额、充值申请及 declare-paid/cancel/confirm 等命令保留。旧列表无 alias 或 redirect，本地 security 对旧 GET 返回403；同地址存在 POST 命令时 MVC 原始方法匹配返回405。API 客户端须迁移到统一查询；回退需同时恢复旧前后端入口，无历史数据回写。

员工手机号仅返回后四位掩码，顾客手机号/员工操作人不返回。搜索匹配原姓名/电话。消费说明来自原个人支付 description，只读不返回 code hash/token。钱包程序与账户数据库限定 SGD，投影沿用此账本币种，不随可变门店结算币种变化，不换算货币。

测试清单：真实 PostgreSQL 跨年月/时区/无重复/分页汇总/不可变支付时间/退款关闭账单/积分双向冲正；跨租户、门店、顾客与指定类型拒绝；API 身份覆盖和精确 GET allowlist；详情及关联隐藏未授权类型。


2026-10-24 国际化修正：Item.summary 保持 string|null 类型，仅返回真实业务说明。没有商户订单说明及个人付款说明的bill返回null，topup的默认摘要返回null；Vue根据sourceType使用payment/topup语言键。显式填写的“消费”或“充值”属于业务数据，仍原样返回、展示。退款原因、积分备注和赠送退出原因不变。旧Vue可接受null（短暂省略默认摘要），回退需协调前后端；无新字段、权限、迁移、路由或错误码变动。
