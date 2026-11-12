# 阶段6：独立补发及法定缴纳 API

D:/RPB；后端实现中。前缀 `/api/v1/stores/{storeId}/workforce`。金额 decimal string，未知 null；状态和余额不得互相代替。精确字段以 `SupplementalDtos.java`、`SettlementDtos.java` 为准。本契约供6B开发，不代表运行验证完成。

## 审批与来源

既有 `PUT /pay-change-requests/{id}` 的 Write 新增可选 `supplemental: Proposal[]`；Content同名字段，缺省不输出，旧hash保持兼容。沿用既有 `/actions` submit/approve/reject/withdraw，无新审批引擎。每个 Proposal 对应同公司同员工一个贡献月，多个原月分成多个 Proposal，保留同一申请聚合。新内容必须有明确 effectiveDate；其他字段沿用既有 Write。批准Proof.kind=supplemental、subjectId=proposal.id、month=settlementMonth。

Proposal：`{id,originMonth,settlementMonth,source,items,evidenceReference}`。

- source=`{kind:"normal"|"opening"|"employment-zero",batchId,openingVersion,snapshotHash}`，从下述来源接口取得。normal必须原已发布，opening必须原实际，有来源hash；employment-zero由服务端验证离职后当月原工资为零、原公司服务期／CPF身份／政策，允许在合同到期月处理AW，不接受客户端手填零。不能用新普通batch替代opening。
- items使用既有PayrollDtos.Item，`kind=wage_event`、正额、full/省略proration、完整eventEvidence。业务类别可bonus/aws/retroactive_increment/omitted_wages；贡献月=originMonth，结算月=settlementMonth。原已到期遗漏用omitted_wages及原应付日期；后来新追溯权利用retroactive_increment及决定日期。旧普通月路径继续禁止跨期／改已冻结月份。
- 原已经入账但未付收入必须继续原单付款。Proposal仅表达未入账新差额，不能复制旧事项或既有eventId。审批原因和完整年度依据不进入员工工资单。

`GET /supplemental-sources?companyId&employeeId&month` → SourcePage（source/grossPay/originalCashRemaining/issues）。

## 独立补发

| 路由 | 正文／返回 |
| --- | --- |
| GET `/supplemental-batches?companyId` | Batch[]；可显示批准、批次状态及calculation的null/issues。 |
| GET `/supplemental-batches/{id}` | Detail={batch,sourceChanged,payments,paidAmount,remainingAmount}。 |
| PUT `/supplemental-batches/{id}` | Write={commandId,expectedVersion,companyId,employeeId,requestId,revision,hash,proposalId} → Detail；后端只读精确批准内容，不收客户端替换金额。新建expectedVersion=0。 |
| POST `/supplemental-batches/{id}/actions` | Action={commandId,expectedVersion,action,expectedFingerprint,note} → Detail；action=calculate/confirm/publish/void，void仅draft/calculated，confirmed只可publish。 |
| PUT `/supplemental-batches/{id}/payments/{paymentId}` | 既有PayrollDtos.PaymentWrite → Detail；现金登记／reversesId整笔冲正，正额且不超独立应付。 |
| GET `/my-supplemental-payslips` | Payslip[]，本人已发布。 |
| GET `/my-supplemental-payslips/{id}` | 本人Payslip。 |

批次draft→calculated→confirmed→published；批准已完成才可建计算批次。计算不消费事件，确认以source锁内重捕获核对，冻结事件／责任差额。每个批次一员工一个originMonth，使独立失败／重试明确；不影响多批同公司月累计。原普通工资和付款不改。

Snapshot同时返回grossDelta、employeeCashPayable、employeeCpfDeduction、cdacDeduction、employeeCpfLiabilityDelta、employerCpfDelta、sdlDelta、sdlRoundingDelta、cdacDelta、originalCashRemaining、balances、context、assessment、issues。未确定金额null，余额为负显示待退款，不产生负工资付款。

本人 Payslip.items 为专用安全投影 `[{label,amount,businessType,classification,originMonth,settlementMonth}]`，逐项解释新增奖金／AWS／漏计金额和OW/AW分类；不返回完整Proposal、eventEvidence、evidenceReference、classificationReason、审批说明、年度fingerprint或CPF身份。

## 独立法定事实与余额

`GET/PUT /statutory-settlement-contexts/{companyId}/{employeeId}/{month}` → EvidencePage。PUT正文Evidence；仅总部完整租户店权限可维护。Evidence.expectedVersion从0递增，commandId精确幂等；证据reference必填。

Evidence保存：discoveryDate；employeeCpfDeducted/cdacDeducted（原实际已扣）；remitted={employeeCpf,employerCpf,sdl,cdac}（系统外原实缴，有证据为0才录0）；recognisedSdl/recognisedCdac（外部原责任）；cdacProfile/cdacReview/manualCdac（外部原批准口径）；recovery={cause,legalBasis,dueYear,deadline,maximum,evidenceReference,reconciliationId}；interest={state,assessedAmount,officialReference}。

数据缺失可null，不将新账没有payment推成历史未缴。原内部冻结责任优先，Evidence不覆盖旧工资；新remittance另加在原实缴之上。Recovery.cause分ordinary_employer_omission/ordinary_non_negligent_recovery/aw_statutory_reassessment/already_deducted_unremitted/unclassified；无自动追扣，资格证据不足返回null。AW statutory reassessment资格必须引用本公司/员工/原年度真实不可变reconciliationId，受原月正员工差额、实际追加缴纳（净实缴超过原认列责任）、未扣余额、人工证据maximum及原应缴年度后六月末期限共同限制；缺关联返回null，尚无追加实缴或已过期返回0。不以发现年度延长期限。interest.state=unknown/assessed/not_applicable，后两者须官方或适用依据；利息不扣员工net。

MonthBalance按分项返回Balance={target,recognised,deducted,remitted,liabilityDelta,deductionDelta,remittanceBalance}，以及recovery、employeeCpfRecoverable、interest、refundRequired、issues。公司SDL分项不跨员工挪用。

## 年度复核与缴纳

`EvidencePage.fingerprint`供 `POST /statutory-settlement-contexts/{companyId}/{employeeId}/{month}/settlements` 使用：SettleWrite→Settlement，记录原少缴CPF责任差额；已扣未缴原责任差额0，不新增工资／SDL／CDAC。

`GET/PUT /statutory-company-months/{companyId}/{month}` → CompanySdl。PUT CompanyEvidence={commandId,expectedVersion,evidenceReference,employeeIds,recognisedSdl,remittedSdl}，总部维护整公司外部完整性证据；只有正常工资覆盖完整原公司责任时采用其原totalSdl。同月存在普通line以外非零external actual原工资时，employeeIds必须完整包含普通原员工及这些外部原收款人，recognisedSdl必须是整个公司原累计责任（包含内部和外部原责任，不含本系统后续新增责任entries），不能填单个外部份额或把两个已floor的部分相加；缺完整名单或累计金额则recognised/liabilityDelta为null并阻止补发confirm，完整原工资事实仍可独立给出target。目标由各员工完整月工资和唯一SDL内核算个人精度后公司floor；缺资料返回STATUTORY_SDL_COMPANY_INCOMPLETE。返回target/recognised/remitted/liabilityDelta/remittanceBalance、individualTotal、roundingAdjustment及issues。公司SDL不能分配给某员工份额；仅SDL Allocation.employeeId=null，其余component员工必填。

审批 `supplemental:[]` 规范化null，不影响旧JSON/hash；显式[]可移除草稿已有补发，整申请因此没有任何经济内容时拒绝。含补发申请的普通impact返回净额null及SUPPLEMENTAL_CALCULATION_REQUIRED，保留可确定的新gross差。

年度上下文及创建复核仍用阶段5 API，不重复计算器。

| 路由 | 正文／返回 |
| --- | --- |
| GET `/aw-reconciliations?companyId&employeeId&year` | ReconciliationPage[]，含原不可变assessment、balances及settled。 |
| GET `/aw-reconciliations/{id}` | ReconciliationPage。 |
| POST `/aw-reconciliations/{id}/settlements` | SettleWrite={commandId,expectedFingerprint,note} → Settlement；责任修订唯一消费，不计新gross/SDL/CDAC。 |
| GET `/statutory-remittances?companyId` | Remittance[]。 |
| GET `/statutory-remittances/{id}` | Remittance。 |
| PUT `/statutory-remittances/{id}` | RemittanceWrite={commandId,companyId,paymentDate,reference,allocations,reversesId,note} → Remittance。 |

Allocation={employeeId,month,component,amount}；component=cpf_employee/cpf_employer/sdl/cdac/interest。正额登记、部分分配、完整冲正；本金和利息分项，外部回执必填。登记只是记录已发生外部缴纳，不转账／申报，不自动抵消反向员工／雇主差额。

## 权限、重试与错误

所有管理读写使用PayrollAccess完整公司工资权限及所有来源店App Gate；批准与外部事实维护总部，保持普通工资本人确认／付款限制。员工本人API只返回独立Payslip DTO。命令同actor/store/payload重试原响应，变payload409；过期版本/指纹409，缺数据404，权限403，非法结构400。

沿用Stage5错误码；新增 `SUPPLEMENTAL_SOURCE_CHANGED`、`SUPPLEMENTAL_SOURCE_MISSING`、`SUPPLEMENTAL_APPROVAL_REQUIRED`、`SUPPLEMENTAL_ALREADY_RECOGNISED`、`STATUTORY_REMITTANCE_UNKNOWN`、`STATUTORY_DEDUCTION_UNKNOWN`、`STATUTORY_SDL_COMPANY_INCOMPLETE`、`STATUTORY_SETTLEMENT_BLOCKED`。原 `ANNUAL_OPENING_PAYROLL_CONFLICT` 继续限制普通工资，补发用明确来源链保留原opening及事件。受影响金额缺资料为null；500<rawTW<=750且AW截短仍明确unsupported，不扩大至已核实正常档。

原期V1兼容：原snapshot为schema1且无AW能力时，新目标只可选择总部已显式启用、同原贡献年份/币种且完整覆盖原月的schema2政策；不拿当前其他年份政策补历史。原工资/opening政策及原已认列责任仍冻结不变，目标context保存实际选用V2版本。确认同时检查批准effectiveDate与合同应付日已到期。

公司SDL完整名单包括所有已确认/发布补发的收款人，不能按当前在职名册过滤。已有普通公司月批次而离职员工不在其line时，仍可继续核验employment-zero来源；只在原雇佣与批准身份资料齐全、公司完整性明确时计算，不把缺身份当foreign。

旧普通工资 CDAC 兼容：原目标与已认列责任共用冻结金额投影，优先采用明确的 `cdacCalculation.amount`。合法旧 `profile.cdac=null` 或 manual 且缺计算 DTO 时，必须以完整冻结 `components` 与原 rule/input 项逐项核对、验证固定扣款合计后保留 `kind=cdac` 金额；完整明细无 CDAC 项可证明零，缺失/非法金额依据则为 null 并报告 `CDAC_ORIGINAL_LIABILITY_MISSING`。新补发不再重复原固定 CDAC；null/manual 搭配 `input.cdacReview` 仍报告 `CDAC_REVIEW_INVALID`。原已扣和实缴仍独立取事实凭证，不从原责任推断；不改原 profile、calculation 或付款。

年度责任复核的所属 year 与 settlementMonth 分别保留。前端复核结算月上限为新加坡当前月份，可在次年复核原年；外部历史月份仍限所属 year。服务端原年终/离职 due 与当前月校验不变，同一命令重试保留原年度 URL 和选定结算月。
