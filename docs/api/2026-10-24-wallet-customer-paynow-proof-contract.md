# 顾客 PayNow 凭证提交

顾客前缀 `/api/v1/public/stores/{storeId}/wallet/collection-proofs`，public为既有顾客API命名，所有接口必须登录顾客会话，private/no-store，写操作要求原钱包同源标记。不得接受tenant/customer/verified/collectionId覆盖。

- GET `/{type}/{id}/context?channel=paynow`：仅本人且本URL门店的topup/bill待付款单据，返回原二维码系统号、金额、应收账户。topup渠道必须与原单一致；bill渠道必须属于当前钱包配置。
- GET `/{type}/{id}/paynow?channel=paynow`：本人待付款源单据，按原商业资格和补付恢复规则出码；已过期未实收充值不出码。仅出码，不确认付款。
- POST `/{type}/{id}/submit` multipart：image、channel、amount、idempotencyKey，固定白名单。PNG/JPEG <=5MiB且完整解码。来源顾客取登录会话，保存到原私有凭证表；检查/OCR复用上一轮规则。响应仅本人提交信息及waiting_confirmation状态，不返回员工备注、人工确认内容、员工上传图或OCR原文。字段不匹配仍作为待复核提交；商户普通确认仍受原阻断规则约束。
- GET `/{type}/{id}`：本人单据且仅本人提交记录，状态waiting_confirmation/confirmed/closed。不要求活跃商业资格，支持历史查看。
- GET `/images/{proofId}`：仅本人提交且仍属于本人源单据的原图，private/no-store、nosniff。员工原图路径与权限不变。

员工GET `/api/v1/stores/{storeId}/wallet/collection-proofs/{type}/{id}/submitted`：wallet.collection.confirm，返回该单据顾客提交且未绑定的凭证供核对。顾客无法调用员工接口、查看员工图或员工内部确认JSON。

员工GET `/api/v1/stores/{storeId}/wallet/collection-proofs/{type}/{id}`：wallet.view，交易详情历史返回该租户、门店、源单据的已绑定收款凭证，以及顾客已提交但尚未绑定的凭证。待核实项的confirmation为空，前端标记“顾客已提交，尚未核实收款”，显示快照应收账户和原图入口，不显示为已收款；只有confirmation.verified=true才显示人工确认的实际收款账户及备注。未绑定的员工扫描草稿不进入此列表。原响应结构、原图员工权限及private/no-store、nosniff保护保持不变。查询、刷新和查看原图均不改变单据、余额、实收或审计记录；无须wallet.collection.confirm权限即可按wallet.view查看，选用和确认仍要求原确认权限。列表支持充值topup和账单bill；源单据不存在404，未登录401，无权限或跨租户/门店403；原图查询仍按租户、门店及凭证ID约束。

提交只保存证据及审计；充值同步标记顾客声明付款，状态仍pending_collection，账单仍未付或部分支付，绝不增加余额、核销积分或生成实收。商户必须明确选择凭证并核实到账，原确认携带proofId，使用原事务及防重复约束。客户或员工均不能借附件切换单据、金额、账号或已使用凭证。

幂等键按顾客身份分区，完整内容改变409，相同请求返回原凭证；顾客不能借猜测员工键重放员工图片。上传后单据状态已变，相同请求仍可在本人归属内安全重放原结果，不重新写审计或声明付款。source/account锁顺序为账户后单据，避免与商户确认死锁。

沿用400 WALLET_REQUEST_INVALID、401 WALLET_UNAUTHENTICATED、403 WALLET_FORBIDDEN、404 WALLET_NOT_FOUND、409 WALLET_PROOF_CONFLICT/WALLET_RECEIPT_USED。V070附加uploaded_by_customer_id及同租户顾客FK、待处理查询索引；V069及历史员工凭证不重写。回退保留新增表列及历史，不恢复旧数据库覆盖资金记录。
