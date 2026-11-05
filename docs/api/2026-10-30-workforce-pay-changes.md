# 工资变更申请与审批（阶段2）

> 阶段演进说明：本文保留阶段2契约；正式有效版本和旧入口后端守卫现以[阶段3](2026-10-30-workforce-effective-pay-governance.md)为准，批量/升级见[阶段4](2026-10-30-workforce-bulk-upgrade.md)。本文“阶段2尚未闭合旧入口”是当时阶段边界，不代表阶段3未实现。奖金/年度事实的阶段5仍在实施，不能由本文推断已完成。

基础路径 `/api/v1/stores/{storeId}/workforce`，写操作 PUT、`X-RPB-Workforce: 1` 与同源保护。全部响应 private,no-store。

| 路径 | 请求/响应 |
|---|---|
| GET /pay-change-requests?companyId=UUID | 本店申请 Request[]；个人/月度要求 PayrollAccess，门店派生要求门店管理 |
| GET /pay-change-requests/{id} | Request，含完整 before/after、patch、来源、revision/hash、事件 |
| PUT /pay-change-requests/{id} | Write → Request；草稿保存/撤回后修订 |
| PUT /pay-change-requests/{id}/actions | Action(commandId,expectedRevision,expectedHash,action,note) → Request |
| GET /pay-change-requests/{id}/impact?month=YYYY-MM | Impact；只读捕获与计算，未知金额 null |
| GET /pay-change-monthly-baseline?companyId=UUID&employeeId=UUID&month=YYYY-MM | LineInput，真实expectedVersion来源；阶段3起批准月度优先，之后安装登记的精确legacy稳定月份，最后无手工金额默认输入；不把任意缺proof raw batch行当旧资料 |

Write 包含 expectedRevision、companyId、employeeId、effectiveDate、adoptionId、profile（原 ProfileWrite）、months[{month,input（原 InputWrite）}]、derivedRule、reason。个人主体 company/employee/effectiveDate；纯月度由服务器依据当月有效方案定位日期，月度身份永远为 company/employee/month。派生主体 adoptionId，与个人互斥。同主体草稿聚合，已有修改须携带当前 revision；同日期可定位原聚合但不能用expectedRevision=0覆盖已有内容。保存相同内容与前一revision可精确重放。months按月份合并完整子项，profile省略时保留该草稿已含方案；没有profile结果的纯monthly不回写profile。提交内容不可编辑，撤回后保存新revision，原提交快照保留。

Action: submit/withdraw/approve/reject。批准/驳回要求真实租户总部并重检原店及工资来源权限；不限制同人显式批准。commandId绑定tenant/actor/store/request/action及精确原payload，精确重放前重检权限。revision/hash不匹配409。基线变化返回持久化 stale 申请，200响应中的status/staleReason是业务结果，不能显示批准成功。

草稿修订省略profile意味着保留旧验证结果：若原baselineHash与当前来源不同，返回409 WORKFORCE_CONFLICT并保留原revision，不会静默吸收变化。客户端须重新读取当前方案/模板/CPF与PR证据，显式传完整ProfileWrite重建；仍执行全部版本和规则校验。未显式重写的月份必须保持完整before输入与当前monthlyBaseline一致，否则同样409；重建时使用新LineInput.version作为expectedVersion。months仍按月份合并，不以省略月份表达删除。

批准事务同时保存决定、有效profile/不可变门店采用/月度版本、精确 proof及审计；不写batch/cache。月度版本独立于batch，并提供 approvedMonthly 查询供阶段3有效解析优先消费；阶段2不声称旧计算/所有旧写入口已闭合。门店派生只产生新候选，不修改员工。未来个人方案沿用 effectiveFrom 解析，待审/驳回不写有效表。

来源基线包含员工当前事实、个人方案序列、固定模板/采用及月度输入版本。提交时固定 before/after 与规范化patch；批准前重新校验来源、公司日期、冻结引用。审批证明只由服务端生成，旧API不接受绕过布尔值。历史个人维护可以引用本店历史固定模板；新选择须当前可用。

Content.sourceTemplate固定个人提案的完整SalaryTemplate（包含governanceSource），beforeProfile保存原有效方案，profile保存精确拟生效结果。Proof.hash是整份Content哈希，proof表的result才是对应单件完整profile/template/input JSON；守卫须校验精确结果与归属，不能将两者哈希混用。月度ApprovedMonth.version为独立有效版本号，input.version为输入基线递增版本，API取expectedVersion须使用LineInput.version。

个人/月度接口只要求原PayrollAccess工资公司完整来源权限与有效入口门店，不额外要求employeeManage。门店派生才要求manage及当前店公司一致。列表逐申请过滤，普通employeeManage不会因此获得个人工资；批准/驳回额外要求headOffice。月度基线同个人工资权限，不以模板库授权代替。

未来门店派生：V109增加adoption.effective_from，旧采用默认最早日；新普通enable使用Clock今天。latest按storeVersion取最大预留版本，仅用于并发；currentAt(asOf)按effective_from DESC、store_version DESC取当日候选，不能按最大version替代日期解析。申请adoption必须是effectiveDate当日的基底；批准可提前落库而不提前切换候选。LibraryEntry/Preview新增scheduledAdoptions[{adoption,effectiveDate}]，当前adoption字段不变。旧无日期enable在本公司存在scheduled采用时始终409，即便客户端知道latest；门店转公司不露出旧公司的scheduled。

effectivity与审批状态分开：future/effective/history/pending。被后来精确版本替代的批准显示history；纯月度按所属月展示，不因长期方案日期较早而把未来月份显示已生效。history是时间/替代展示，不删除其原批准证明或冻结工资。

错误沿用401 WORKFORCE_UNAUTHENTICATED、403 WORKFORCE_FORBIDDEN/AppGate、404 WORKFORCE_NOT_FOUND、400 WORKFORCE_INVALID、409 WORKFORCE_CONFLICT/WORKFORCE_PAYROLL_PROFILE_LOCKED。影响用原 PayrollSources 在捕获前注入假设后调用原计算器；阻断 issues → 净额及差额 null，标准固定差额单独显示，不自定阈值。

未来月份小时制/变动工时资料不足时返回FUTURE_ATTENDANCE_SOURCES_INCOMPLETE，净额保持null，不能用已勾选人工核准制造已知零。固定月薪的未来估计保留FIXED_FUTURE_CONTRACT_ASSUMPTION，明确按当前在职事实与拟批准方案估计；预览不创建未来批次。

V109只新增申请、不可变revision/事件/命令、批准proof和稳定月度版本。既有工资/付款JSON保持原状。奖金/AWS/AW/跨期补缴的财务内核留阶段5/6。
