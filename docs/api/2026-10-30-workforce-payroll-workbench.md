# 月度工资工作台契约（本地实现）

GET `/api/v1/stores/{storeId}/workforce/payroll-workbench?companyId={uuid}&month=YYYY-MM&expectedVersion={optional}&batchId={optional}`。

只读、一致快照；PayrollAccess按租户、公司、当前及历史来源门店再鉴权，沿用AppGate。401/403/404沿用workforce错误；非法月份400；expectedVersion与当前有效批次不符409 WORKFORCE_CONFLICT，要求刷新。本地allowlist只增加该精确GET路径，其余方法及子路径仍拒绝。

返回companyId/month/batchId/batchVersion/status/asOf/calculatedAt/frozen；无批次batch字段null。missing是既有完整capture/计算器的阻断清单，category分profile/statutory/workmode/input/source；人数按employeeId去重。证件只对已引用但失效的证据报错，不要求所有人上传证件；付款偏好不阻断计算。

approvals含员工、班次、门店、工作日、missing/stale状态及context。pendingEmployees去重；pendingShifts包含跨月周上下文，contextShifts单列；context不可再次计薪。自由员工exemptEmployees单列。

changes复用原双基线changes；无批次为null、缺基线available=false且差额null，不能当作0。reasons带employeeId/kind/sourceId/oldVersion/newVersion/scope/action，仅有证据时细分。旧缓存或不能精确确定时用source或baseline_unavailable，不编造具体字段变化。冻结批次仅提示当前来源变化，不能重算历史。

payments须有可用的已计算快照才available=true，按后端BigDecimal及原付款减冲正汇总due/paid/remaining；逐员工state=unpaid/partial/settled/zero。zero为净额0无需付款，非银行完成。批次status与付款状态独立。只复用原付款命令的防超付、幂等及原paymentId恢复，不调用银行。

工作台不是确认授权：月底、全量重算一致、来源有效、版本、非本人及权限守卫保持。已确认未发布可依原规则作废重建；已发布金额更正尚不支持，付款冲正不能替代工资更正。跨公司归属保持现状。

batchId可选指定详情页批次，必须同时匹配租户、公司及月份；不传时只选该公司/月非voided有效批次。避免作废后重建时错误拼接两个批次。

既有BatchSummary在无calculation时totalGross/totalNet/totalEmployerCpf/totalSdl/totalEmployerCost返回null（旧值为0.00）；前端显示不可得。实际零净额快照仍返回0.00。新UI不得将未计算工资冒充零。missing.profileId用于准确跳转已有个人方案，来源原因可带storeId/workDate定位来源。

付款available=false也包括计算快照含阻断issues的预览及其voided记录，其0占位不代表合法零工资；due/paid/remaining=null，zero=0、lines=[]。frozen包含confirmed/published/voided，仅表示不可修改；付款可信金额绕过issues只限confirmed/published，保留其历史金额与真实登记。合法无阻断voided金额仍可读；作废流程及付款命令不变。engine原因与capture指纹是否变化独立；旧cache引擎不同但指纹相同也返回engine，冻结只提示。核准动作入口使用既有按storeId/scheduleId精确详情，不依赖列表未实现的employeeId过滤。

双基线比较原因沿用同一可信证据准则，详细字段见 `2026-10-30-workforce-payroll-incremental.md`：新reasonBasis=snapshot_evidence_v1；旧已存lastCalculation缺少完整before及证据准则标记时，只读降级legacy_unverified/legacy_comparison，显示“历史比较结果（缺少完整前态，原因未验证）”。差额、类型、基线版本保留，旧工资和比较JSON不回写。不能把结果差异维度标成真实政策变更、人工输入或出勤事件。
