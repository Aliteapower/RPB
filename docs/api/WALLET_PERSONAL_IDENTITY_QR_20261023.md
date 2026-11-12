# 顾客个人钱包身份码

统一充值入口顺序为先扫描、核对顾客，再选择钱包充值或积分操作。移除该入口的会员手工搜索，仍保留其他独立收款用例的原选客逻辑。扫码与操作权限独立，识别不扣款、不加钱、不加积分、不创建资金账户。

- POST /api/v1/public/stores/{storeId}/wallet/customer-codes：既有顾客cookie确定租户及顾客，无需已有资金账户；请求为空或{}，未知身份/金额字段400。有效钱包商业授权、门店启用及顾客状态校验后签发WI.加43位随机不透明令牌，type=customer_identity，60秒。返回code/expiresAt，不包含真实顾客ID的可解码内容。只存SHA256；同一租户/计划/顾客重新签发使旧WI码失效。
- POST /api/v1/stores/{storeId}/wallet/customer-codes/resolve：员工会话确定租户，body仅code。需wallet.topup.create或wallet.points.view之一，当前门店访问及钱包App Gate有效。返回统一顾客DTO，不返回资金账户、余额、付款授权或积分余额。WI限定同租户及同发行主体钱包计划；同计划授权门店可识别。兼容有效旧WP个人充值码；拒绝WC扣款码/WB账单码。过期或未知码404，非法格式400，越权403，未登录401。
- 两个写入口保留X-RPB-Wallet及同源CSRF约束、private/no-store。识别为可重复只读查询，不消费码、不绑定账单、无幂等业务写入。签发只更新身份码，重试可能刷新旧码，不产生账务重复。
- V063增加wallet_customer_identity_codes，tenant/program/customer复合外键，哈希唯一及有效期索引；不改旧资金或积分记录。独立身份码不能用于钱包扣款或账单绑定。

测试清单：无资金账户顾客签发和仅积分员工识别；重复识别无资金/积分副作用；过期/刷新/跨租户/跨主体/门店权限及App Gate拒绝；旧WP兼容而WC拒绝；API无账户字段、禁止伪造身份字段；前端先扫后选、切换保留顾客、切店丢弃旧识别响应、个人码不触发扣款或付款轮询。上线另行授权，本轮不部署。
