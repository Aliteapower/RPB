# 钱包统一交易查询实施计划

> 使用 writing-plans / subagent-driven-development 执行完整用户规格，独立后端查询和前端展示并行实现后联调。仅开发测试，不提交、不推送、不部署；保留上一轮个人码全部未提交文件。

**Goal:** 商户与顾客只使用“交易记录”列表入口，统一查询资金业务和积分，数据库负责过滤、排序、分页和完整汇总，不新增账本或记账逻辑。

**Architecture:** 独立 wallet.transactions 查询模块，将 bills/topups/refunds/loyalty_entries 及单独bonus_revoke事件映射成只读业务视图。资金分录只提供记账时间/分摊证据，不重复形成充值、消费主记录。前端共享查询组件/类型/URL状态，不拼接旧分页。

**Spec:** 本轮用户完整规格。工作区D:\RPB，HEAD f7f889e1（生产上一轮7eeaf08e）；上轮V064及个人码未提交实现保留。PostgreSQL以 target/local-postgres-current.txt 为准，当前61644。没有正在运行的上轮开发/发布任务，原8081及数据库进程保留。

## 查询契约（实现时补充独立API合约）

GET `/api/v1/stores/{storeId}/wallet/transactions` 与 GET `/api/v1/public/stores/{storeId}/wallet/transactions`。

Query：`period=month|year|range|today`（默认month）、`year`、`month`、`startDate`/`endDate`（ISO本地日期、结束日包含）、`type=all|payment|topup|points_grant|points_deduct|points_redemption|refund|reversal`、`status=all|pending|completed|cancelled`、`reference`（交易UUID/主记录编号）、`customerQuery`（仅员工手机号/姓名）、`targetStoreId`（默认当前门店，可为all或UUID）、`limit`（默认25，上限100）、`cursor`。

返回 `{items,nextCursor,totalCount,summary,stores,period,capabilities}`。`stores:[{id,name,timeZone}]` 是员工授权门店或顾客本人有记录门店（并包括当前上下文门店），不可由顾客参数指定他人。period返回后端规范化起止日期、时区及模式。按各交易门店的时区将日期转换为明确[start,endExclusive)时间范围；默认年月依据当前门店Clock，不使用浏览器计算。

统一记录：`id,sourceType(bill|topup|refund|points|money_reversal),type,status,occurredAt,createdAt,postedAt,storeId,storeName,timeZone,currency,amountCents,quantity,direction(in|out|none),walletDeltaCents,principalCents,bonusCents,creditTotalCents,walletPaidCents,externalPaidCents,refundStatus,customerId,customerName,customerPhone,summary,actorName,originalSourceType,originalId`，各非适用字段可null/0。金额用SGD分，积分独立数量；顾客手机号/操作人按权限必要最小化。支付显示原账单金额，实际资金支付与退款状态独立，不覆盖历史。

summary用明确独立字段：`consumptionCents,walletPaymentCents,externalPaymentCents,topupPrincipalCents,topupBonusCents,topupCreditCents,refundCents,moneyReversalCents,pointsGranted,pointsDeducted,pointsRedeemed,pointsReversed,pointsReversalIn,pointsReversalOut`。只计当前完整筛选结果内completed，不能受cursor/limit限制；不提供“总收入”。商户显示收款/实收，顾客显示付款/钱包权益变化，退款需同时明确现金/钱包方向，积分不可与金额合计。

只读详情 GET 同列表基址 `/{sourceType}/{id}`，返回 `{transaction,related,events}`。详情不受默认月份限制，严格复用同等权限/所有权，events显示创建、实际记账、完成及关联，不靠可变updated_at冒充原支付时间。前端有原业务详情则复用，缺少顾客充值/退款、积分或权益冲正详情时使用简洁只读展示。

## 归并与权限决策

- 一张bill一条消费，一张topup一条充值，一张refund一条退款，一条积分entry一条积分变动。bonus_revoke是独立赠送权益冲正，不混成重复充值或现金退款。
- 已完成bill按实际支付事件（资金分录/非异常实收）时间，充值按credited_at/原入账分录，退款按真实完成/实际退款事件时间，积分按不可变entry.created_at。取消和待处理按创建时间。不得把退款导致的bill.updated_at作为原付款日期。
- paid账单即使因退款closed仍是原完成消费；无付款且closed才是取消；未结清账单通常待处理，汇总只计completed的实际收款，原金额展示不变。
- 员工资金需wallet.view；积分需wallet.points.view；退款/资金冲正沿wallet.refund.manage。all仅包含有权类型，指定无权类型403，capabilities告知界面哪些统计不可见。顾客只能tenant+本人所有权，不允许customerId/customerQuery等身份覆盖参数。
- 门店过滤是已授权集合的交集，指定越权门店403/404，不悄悄扩大到租户全门店；历史查询不要求仍可发新交易，但维持租户/门店/类型权限。
- 混合排序 `occurredAt DESC,sourceType DESC,id DESC`，keyset cursor绑定过滤条件；分页与全量汇总同一只读一致视图。

## 路由与流程清理

只保留商户 `/stores/{storeId}/wallet/transactions`，顾客 `/wallet/{storeId}/transactions`。删除独立列表bills、history、points、topups、refunds及旧points重定向路由，不加alias/redirect；已有bills/:id、topups/:id、refunds/:id等详情可保留。

顾客充值申请是操作流程，原topups页面有表单，迁移为 `/wallet/{storeId}/topup` 操作页（仅表单，不夹带历史列表），充值提交结果跳原充值详情或统一列表定位。商户充值积分操作仍`/topup`，原staff points操作组件嵌入保留但独立points路由移除。菜单/底栏、所有结果/详情返回/通知及测试同步改。筛选保存在URL，跨店详情携带安全returnTo，仅返回同角色transactions地址，不能开放重定向。积分操作页可保留操作及当前结果卡，不再附独立全量历史入口。

## Tasks

- [x] API、数据审查与测试清单先行；记录工作区起始哈希，不回滚上轮改动。
- [x] 后端只读统一query/detail，真实PG覆盖年/月/跨年边界、时区、权限、去重、稳定混排分页、完成统计、退款冲正关联及历史时间；必要时V065仅索引，不改写历史。
- [x] 前端共享查询组件、年/月/期间/今天、类型状态编号顾客门店筛选、完整汇总、只读详情、路由旧引用清理及URL返回；复用原操作与详情。
- [x] 审查并运行钱包/个人码/营销/积分/分摊/退款和相关产品线回归；隔离浏览器跑查询、翻页、详情返回、充值及个人码结果跳转。
- [x] 验收报告包含测试证据、旧地址清单/API迁移回退、时区口径、权限和剩余风险；不集成不发布。

## Review Focus

本金/赠送和积分分开；充值申请/确认/入账不三次计数；退款保持原消费金额；只用不可变记账时间；匿名/跨租户/跨店/他人查询不能泄露。清理列表路由不能误删操作和详情能力，URL查询不得被旧结果跳转丢弃。
