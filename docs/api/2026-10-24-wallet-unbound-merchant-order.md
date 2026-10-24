# 商户先定数值、顾客扫码确认

商户出码不提交顾客或钱包账号；既有 WB/WR 已绑定码和详情接口保留旧页兼容。新 WM 码对应待支付单，未确认前不锁定顾客、不创建资金账单或积分流水。

员工 /api/v1/stores/{storeId}/wallet：POST /merchant-orders {kind:money|points,amount:金额字符串(仅money),quantity:正整数(仅points),description:说明或必填兑换内容,idempotencyKey}；GET /merchant-orders/{id}；POST /merchant-orders/{id}/code 返回code/expiresAt；POST /merchant-orders/{id}/cancel {idempotencyKey}。

顾客 public 基础：POST /merchant-order-code {code} 只预览；GET /merchant-orders/{id} 返回待支付单及本人可用余额、expectedVersion；POST /merchant-orders/{id}/confirm {expectedVersion,idempotencyKey} 明确确认本单确定金额／数量，使用本人登录身份，不能指定其他顾客或账户。

新码120秒，散列保存，刷新后旧码不能再解析；同单只有一位顾客能成功付款或兑换。请求行锁与单事务协调：第一次成功确认才绑定付款人、关联原账单／积分entry；读取和失败不占用单，不产生业务账。重复确认返回本人原结果，其他人确认已完成单404。资金需足额完成本单，余额不足409且不产生账单；原账单的部分付款能力不改。

金额扣款复用既有资金分摊、账单、授权支付与退款；积分仅兑换不抵现金，复用现有积分账本。完成后统一交易记录只显示原业务记录，不另加收入。员工按kind使用wallet.charge.create+view或wallet.points.redeem+view，AppGate和积分开关仍校验；顾客按租户、门店、发行计划及会话身份校验。

非法字段／金额／整数400；未登录401；越权403；不存在、跨范围或他人已完成404；过期、取消、余额不足、版本变化409。确认等待账户锁后再查过期，失败原子回滚。禁止记录明文令牌。

V066只新增wallet_merchant_orders意图表及约束／索引，历史记录不改；回退应用保留该表和已记账的原交易，不能删除流水或回写余额。并发多顾客、同顾客重试、失败后其他顾客付款、跨租户／门店、旧码兼容和界面无手机号均纳入测试。
