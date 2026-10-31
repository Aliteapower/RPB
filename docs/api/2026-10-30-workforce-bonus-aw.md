# 奖金／AWS／AW 与年度上下文 API（阶段 5）

工作区 D:/RPB。本契约对应既有统一经济申请和普通月度计薪；跨期现金／缴纳登记由阶段 6 扩展。所有路径前缀 `/api/v1/stores/{storeId}/workforce`。金额为十进制字符串，未知为 null。旧可空字段 JSON 不改变，新增 `eventEvidence`/`aw` 仅非 null 时序列化。

## 工资事件

原月度工资申请 `months[].input.items[]` 增加 `kind=wage_event`，沿用金额、label、itemId，新增 `eventEvidence`：

`eventId` UUID；`businessType` bonus/aws/retroactive_increment；`classification` OW/AW；`serviceFrom/serviceTo` ISO日期；`decisionDate` 可空（retroactive_increment 必填）；`contractualPayableDate`；`contributionMonth/settlementMonth` YYYY-MM；`evidenceReference/classificationReason` 必填、各最多1000字符。

新工资事项必须走既有 pay-changes 草稿→提交→批准，与该月其他经济内容共同冻结；不新增审批。阶段 5 普通批次只接收贡献月=结算月=申请月；跨期由后续独立差额路径消费。`wage_event` 不允许写入经常性模板。旧 ow_additional 不变。AWS输入合同已经核定的金额，不自动计算一个月工资。事件编号在确认消费时唯一；作废保留原记录、明确释放消费，不改旧快照。

## 法定政策 V2

GET `/statutory-policy-templates/{year}/with-aw` 返回新模板。旧模板接口不变。保存仍 PUT `/statutory-policies/{id}`；schemaVersion=2 增加 `aw={annualWageCeiling:"102000",calculationVersion:"SG-OW-AW-MONTH-v2",source:"https://..."}`。旧 V1 不能用于本地公民／SPR AW，外籍 CPF 不适用且 SDL 继续计算。

## 年度事实

GET `/statutory-year-contexts/{companyId}/{employeeId}/{year}` → `AnnualWageDtos.Page`，包含 context 与 assessment。读取使用完整公司工资权限、员工归属和全部源门店 App Gate；`context.writable` 只对总部法定事实管理员为 true。

PUT 同路径，正文 `AnnualWageDtos.Write={commandId,expectedVersion,months,reason}`。仅总部且具备全部租户门店访问/App Gate，可维护具体外部历史事实；普通门店工资权限不能写。完整替换外部历史 revision，不修改内部实际／批准规则。`months[]` 为 `OpeningMonth`：month、state(actual/known-zero)、rawOW、cpf、policyId/policyVersion、recognisedEmployeeCpf/recognisedEmployerCpf（缺原责任可 null）、evidenceReference、events。known-zero 必须 rawOW=0 且无事件及有具体凭证；实际收入必须提供当期 CPF 身份、政策版本和明细。只接受当前月以前且不存在内部冻结或有效批准月输入的外部月份；不得录入自定义未来 forecast。重复 commandId 同 actor/store/payload 返回原响应，冲突409。

context 返回固定12个月 state=actual/forecast/known-zero/unknown。内部冻结工资为只读实际，明确雇佣期间外为有来源零，当前有效批准固定月薪与合法雇佣期间为预测；工时未知、缺政策或旧事实保持 unknown。所有预测依赖、批准、政策、年度revision及事件纳入 fingerprint。

`context.openingMonths` 对有写权限的调用者返回已保存的完整外部月份，供编辑回传；其他人为空数组。完整替换表示提交一份新的不可变 revision，不表示可以撤除旧事实覆盖期：既有 month 与 eventId 必须保留，否则返回 `ANNUAL_EVENT_HISTORY_IMMUTABLE`。可以凭证据修订该月 rawOW、历史CPF身份／政策、原已入账员工／雇主责任及月份凭证，或新增遗漏月份／事件；既有事件的金额、分类、服务／决定／应付／贡献／结算日期及事件凭证不可改写。新页面尚未保存的行可删除；已登记事件的撤销、负更正和消费释放必须走明确更正链，不通过删年度资料实现。内部已冻结或已批准月份始终不能由外部事实覆盖。

当前可自动推导的预测是已证明的 fixed 月薪；工时依赖工资没有未来已确认工时就保持 unknown。联合申请的拟工资方案及月事项通过持久化 request/revision/hash 绑定的只读假设覆盖未来预测；不写正式年度事实。确认普通 AW 工资时，`workforce_aw_assessments` 保存当次完整12月上下文与额度／月责任计算，以便以后重估，历史 forecast 证据不会仅剩一个指纹。

若同员工、公司、月份已有外部 opening（actual 或 known-zero），普通工资所有行均报告 `ANNUAL_OPENING_PAYROLL_CONFLICT`，包含纯 OW、不同 AW 及拟批准预估；该行 CPF 与净额为 null。中文提示：“该月份已有外部工资事实，不能通过普通工资重复计薪；补发或更正请走独立来源处理。”opening 覆盖期和 revision 纳入普通来源指纹，计算后新增或修订会使未确认批次过期；确认在 source 锁内再次捕获并以此错误码（HTTP 409）拒绝。不得自动合并／替换外部历史或丢弃旧事件释放额度。补发／更正由阶段 6 的独立来源链处理，不存在隐式导入转换；原先禁止外部事实覆盖内部冻结／批准月份的方向也保持。

## 年度复核

POST `/statutory-year-contexts/{companyId}/{employeeId}/{year}/reconciliations`，正文 `{commandId,expectedFingerprint,settlementMonth}`，总部权限同年度写入；在年终／离职较早月、实际12月事实完整时记录不可变 assessment。返回 `Reconciliation`。它仅记录责任复核／退款待办，不付工资、不推断实际CPF已缴、不自动抵扣未来工资。实际结算由阶段6消费。

GET 的 Page 同时返回 `reconciliations` 历史列表，便于刷新后继续显示已登记复核与退款待办。`PayrollLine.aw` 为内部工资管理trace；员工 `PayslipLine.publicAw` 只含 rawOW/rawAW/cpfOW/cpfAW，eventEvidence、年度证据和内部审批内容不进入本人工资单。

assessment 字段及 MonthFact/MonthLiability 以 Java `AnnualWageDtos` 为精确类型契约。status=ready/incomplete/not_applicable；delta按逐原月目标整数责任减原责任。总差额0而员工／雇主分项非0仍 reviewRequired。未知总额null，不展示为零。

## 明确问题

`WAGE_CLASSIFICATION_EVIDENCE_MISSING` 400；`WAGE_EVENT_PERIOD_UNSUPPORTED`、`STATUTORY_CAPABILITY_UNSUPPORTED`、`ANNUAL_WAGES_INCOMPLETE`、`AW_RAMP_CAP_UNSUPPORTED`、`ORIGINAL_LIABILITY_AMBIGUOUS`、`AW_CONTEXT_STALE`、`WAGE_EVENT_ALREADY_CONSUMED` 409。500<rawTW<=750 且 AW 被年度截短明确不支持；其他已验证低档／正常档不受此限制。未批准金额不能进入计薪，年度变化令未确认计算过期，历史冻结与付款JSON不回写。

财务公式与证据：[设计及附录A/B/C](../superpowers/specs/2026-10-30-workforce-bonus-aw-arrears-design.md)、[五组官方合成响应](../development/evidence/2026-10-30-cpf-aw-calculator-oracles.json)。生产计算没有实时官方计算器依赖。
