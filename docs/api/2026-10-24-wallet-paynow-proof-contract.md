# 钱包 PayNow 凭证辅助校对

前缀 `/api/v1/stores/{storeId}/wallet/collection-proofs`；仅员工。复用钱包身份与同源写请求标记，不能覆盖tenant/customer身份。现有快速收款权限和自动匹配接口不变。

- GET `/{sourceType}/{sourceId}/context?channel=paynow`：sourceType=topup|bill，返回systemReference（与原二维码W+UUID前20位一致）、amountCents（充值本金或消费剩余应付）、expectedAccount（当前PayNow代理号）、merchantName。确认权限与原资金恢复条件相同。
- POST `/{sourceType}/{sourceId}/scan`：multipart image、channel、amount、idempotencyKey。5MiB以内真实PNG/JPEG，执行同租户银行模板OCR，只保存附加凭证，不创建支付意图、资金分录或已收款记录。返回id、上下文快照、识别字段、reference/amount/account/status检查、原图URL。OCR不可用时保存原图并返回extracted.ocrStatus=unavailable，全部检查标记missing且明确提示人工核实；缺字段保留原图并允许人工补录；明确不匹配标记mismatch。
- GET `/{sourceType}/{sourceId}`：wallet.view，返回该门店本单已绑定收款的凭证列表，包含识别值和最后确认的bankReference/actualAccount/note/confirmedBy；未绑定上传不进入交易历史。
- GET `/images/{proofId}`：wallet.view或wallet.collection.confirm，严格tenant/store，私有no-store响应、nosniff，返回原图；无顾客公共路径。

原topup confirm、bill collections请求新增可选proofId；省略时保持手工确认。receiptReference仍表示银行Ref，systemReference不能代填银行Ref。proofId只能绑定当前tenant/store/source/channel/金额/收款账户匹配且无明确核对冲突的凭证；必须verified=true，识别与最终人工值均留存。改换金额、账号或Ref不得绕过附件校验；须移除错误附件走既有人工核实或异常处理。已绑定附件不可复用。

上传幂等键限制同tenant/store；相同完整输入重试返回原凭证，变更来源、金额、渠道或图片409。图片摘要用于防止同收款账户重复绑定同一图；最终银行Ref沿用现有tenant+渠道类型+收款账户+Ref唯一约束。资金确认仍由原命令幂等、账户锁和原事务完成，绑定失败回滚资金变更，不增加第二套账本。

400 WALLET_REQUEST_INVALID；401 WALLET_UNAUTHENTICATED；403 WALLET_FORBIDDEN；404 WALLET_NOT_FOUND；409 WALLET_PROOF_CONFLICT/WALLET_RECEIPT_USED。上传权限沿用wallet.collection.confirm，不额外要求payment产品订阅。过期后的既有订单恢复遵守原规则；新业务仍由现有AppGate约束。运行态只新增上述精确HTTP方法，不放宽其他路径。

V069附加表包含tenant/store及复合源单据外键、单据／账号／金额快照、原图及摘要、识别和检查JSON、上传人／时间、收款绑定及人工确认JSON。复合外键禁止跨租户单据／收款绑定。无账本字段修改。应用回退保留附加表和历史，不恢复旧数据库覆盖业务。
