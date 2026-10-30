# 有效计薪版本与旧写入口（阶段3）

D:/RPB，2026-10-30。本地实现；本文件不代表生产发布或最终验收。阶段3后端验证、失败修复与独立审查证据见阶段报告。

## 旧写接口

现有路由、App Gate、公司完整工资来源权限及同源写保护不变。旧 `PUT salary-templates/{id}`、`pay-profiles/{id}`、`payroll-batches/{id}/inputs/{employeeId}` 不接受客户端 approvalId、许可布尔值或缺依赖放行。经济内容不合法直接写入时返回 **409 WORKFORCE_PAYROLL_APPROVAL_REQUIRED**，前端应保留输入并引导统一申请，不能显示已生效。

首次个人方案仅允许适用、本店已启用的不可变发布模板原样采用；已有任何合法方案的员工换模板或新生效日期仍比较经济内容。经济不变可以直接保存，服务器登记完整结果及当时 sourceId/sourceVersion/source_snapshot；纯身份 itemId 不造成重复金额审批，实际 source/event 证据不忽略。个人 CPF/CDAC、规则及月度金额覆盖需要精确批准结果。付款偏好、说明及核准标记仅在经济内容不变时可直接维护。

首次标准采用不推断种族或姓名：合法CPF身份/出生/PR资料属于事实，SPR以GG为标准；FG/FF特殊选择须批准。CDAC explicit applicable（自动）/not_applicable 是适用性声明；manual/exempt个人选择须批准，但发布模板已明确同额CDAC项目且个人规则无经济覆盖的manual可按原样采用。foreign的法定n/a不强迫新增审批。新citizen/SPR无CDAC声明且规则无CDAC项目时返回409 `WORKFORCE_PAYROLL_CDAC_DECLARATION_REQUIRED`，要求补标准声明，不能当成0；真实批准写入也不能绕过资料缺失。安装登记的legacy-null保留原行为，不把所有缺声明数据当legacy。

新建旧共享工资模板及其经济改写被拒绝；发布/派生使用治理流程。既有合法模板非经济资料可维护。原样采用、经济不变记录属于非审批来源，不伪造 approvedBy 或历史批准。

显式未来批准的派生模板须遵守其合同生效日。普通模板启用时间属于采用审计，不新增“个人生效日必须晚于启用日”的回溯限制。合法未来个人方案按目标日期选候选；原方案自己历史模板来源独立可维护。

## 正式捕获与预估

正式 capture 在 fixed 筛选、加班周上下文及自动单位生成之前验证已存个人方案证明。有效月份按以下顺序解析：精确 approvedMonthly → 安装登记的唯一 legacy 月份基线 → 无手工金额 default。旧 batch 输入不能覆盖新批准金额；未来批准月份和作废重建保留相同稳定版本。

旧 batch 的纯说明/核准资料仅可在经济内容相同时叠加。客户端原样回传自动生成的单位时，服务器只保存原始有效输入及其说明/核准资料；任意不同数字不能被自动模式掩藏，须拒绝或通过统一申请。工时核准仍独立于金额审批。

影响预估使用 `captureAssuming(..., Assumptions(requestId,revision,hash,profiles,monthlyOverrides))`；服务器核对持久化申请的完整内容。先解析正式有效版本，再叠加该申请的精确只读假设。正式 BatchService 只调用普通 capture；预估不写 batch/cache，指纹含假设身份。

假设中的新citizen/SPR若缺CDAC适用性声明，返回问题码 `WORKFORCE_PAYROLL_CDAC_DECLARATION_REQUIRED`，`afterNet/netDifference`为null；仍可信的`beforeNet`保留。完整且明确的FG/FF或exempt提案虽尚待批准，仍可作为只读假设预估，不因其待审批而当成资料未知。原foreign的法定n/a或legacy-null不能作为后来新居民未声明CDAC的兼容来源；既有居民安装时登记的legacy-null继续兼容。

`PayrollLine.governance` 为可空 `EffectiveSources`，其 `profile/input` 亦可空。Evidence 包含 kind、subjectId、version、requestId/revision/hash、evidenceId/sourceId/sourceVersion；kind 为 approved、legacy-valid、direct-adoption、economic-unchanged、default。只有真实 approved 使用 requestId；default 表示无手工金额，不能展示为批准。月份 Evidence.version 是稳定批准月版本，LineInput.version 是输入版本，两者不可互换。个人工资单不返回 governance 或内部申请理由。

缺证明的存量以外新增数据返回行 issue `WORKFORCE_PAYROLL_GOVERNANCE_PROOF_REQUIRED`；无唯一旧月份证据返回 `WORKFORCE_PAYROLL_INPUT_AMBIGUOUS`。显式未来派生日期违反返回409 `WORKFORCE_PAYROLL_TEMPLATE_NOT_EFFECTIVE` 或当前候选作用域拒绝。没有有效方案保留 PROFILE_MISSING 阻断。待审/驳回使用旧有效方案；批准与未来生效分别展示。

完整结果验证按当前 DTO 全类型读取后比较，包括所有新扩展字段；旧 JSON 缺可空字段与 null 等价，不回写旧 JSON。来源证明进入捕获指纹、增量行键及工资行；引擎键为 `workforce-payroll-incremental-v3-effective-proofs`，旧未确认缓存必须过期。冻结工资、发布/付款快照不回写。

## CDAC批量

原批量路由与 selection/preview 保留。Write 为 `{selection,fingerprint,reason}`，reason 为必填独立申请理由，不替代 CDAC reference 法律凭证。apply 现在表示显式提交统一申请，**不直接生效，也不自动批准**。

Result 保留 `profiles: []`，新增 `requests: [{employeeId,requestId|null,status,error|null}]`；status 为 submitted/failed/stale。每员工独立事务，申请提交与单件回执同事务；同员工同日期已有其他开放申请时返回 failed/WORKFORCE_CONFLICT，不静默合并或提交预览未展示的提案。受控事务失败为 `WORKFORCE_PAYROLL_CHANGE_FAILED`，来源变化为 stale/WORKFORCE_CDAC_BULK_CHANGED。

命令 claim 固定 tenant/company/store/actor 和完整 payload；相同命令精确重放不重复提交。失联时保留原命令及 payload 重试；部分失败后仅选择未提交员工，重新预览并使用新 command。已 submitted 员工不可被重试批次自动批准。

每项持锁后重新比较完整预览基线，包括员工、个人方案全历史/版本、冻结引用、模板及PR证据；同ID版本变化也返回stale，不复制未预览的新工资。迁移前已有成功命令的精确旧payload重放不要求新增reason、无任何新业务写入，原历史profiles响应保留；旧JSON缺requests读取为[]。所有新命令仍要求独立reason，新操作profiles恒空。

## 迁移与锁

V110 仅新增不可变有效证据、legacy月份及CDAC命令/单件回执表。迁移登记安装时全部模板、个人方案（含未来）及旧批次输入，保留原JSON。唯一未作废批次优先形成稳定 legacy 月基线；仅作废来源时，单个或多个完整内容一致的输入保留valid，来源按版本/时间/id固定；真正不同输入登记歧义，不能任意择一或静默default。旧可空JSON字段与版本元数据不造成虚假冲突。缺少迁移表会失败关闭。

个人写/批准使用 source共享 → subject → defaults → employee → profile；确认维持 batch → source排他。批准只写稳定配置/月记录，不在 source共享下改 batch。仓储验证精确批准或非审批证据，并拒绝旧批准版本回滚现行个人方案。CDAC父命令不持员工锁跨子事务。

奖金/AWS/AW/WageEventEvidence和补缴后续扩展沿用完整 result/proof；阶段3不新增财务公式。租户集中法定政策与原工时事实审批保留，具体入口分类见入口审计。
