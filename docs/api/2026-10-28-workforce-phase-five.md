# Workforce 第五阶段 API

基路径 /api/v1/stores/{storeId}/workforce，原cookie actor/tenant、PayrollAccess、逐店AppGate；private,no-store。仅2026普通工资既有快照，不新增法定计算或迁移。

- GET /payroll-report?month=2026-10&companyIds=uuid,uuid：1..50唯一明确公司；逐个授权，返回Report{month,currency,companyIds,companies,employees,payments,frozen,forecast,fingerprint,generatedAt}。missing/draft不提供金额；冻结/预估分开，去重员工与工资行数同时返回。
- GET /payroll-report/export?month=...&companyIds=...&kind=companies|employees|payments&fingerprint=...：重新一致读取并校验fingerprint，UTF-8 BOM CSV附件；仅最小显示字段，不返回规则或敏感资料。
- PUT /payroll-reconciliation：{companyId,month,fingerprint,expected:{gross,net,employeeCpf,employerCpf,sdl,employerCost,paid,remaining}}，八项十进制字符串必填（最多12位整数、2位小数），只读计算，无持久化。必须单公司confirmed/published且fingerprint匹配；返回{report,matched,items:[{field,expected,actual,difference}]}，difference=系统-外部。要求X-RPB-Workforce:1且同源，重试重新读取当前快照；金额不写业务表。

Totals含八项金额；Aggregate={companyCount,employeeCount,payrollRows,totals}。公司行={companyId,companyName,batchId,status,sourceChanged,employeeCount,totals|null,issues}。员工行只含公司/批次/状态、员工ID/编号/姓名、工时与工资金额和付款余额。付款行只含公司/批次、员工ID/编号/姓名、付款ID、日期/方式/参考、原额、signedAmount和reversesId。SDL员工原值不等同公司申报整元数。

错误：401 WORKFORCE_UNAUTHENTICATED；403 WORKFORCE_FORBIDDEN或原AppGate拒绝；400 WORKFORCE_INVALID（无公司/重复/格式错误/非法kind/金额）或WORKFORCE_PAYROLL_UNSUPPORTED（非2026）；409 WORKFORCE_REPORT_CHANGED（快照变化）、WORKFORCE_PAYROLL_BLOCKED（非冻结批次不能对账）；413 WORKFORCE_REPORT_LIMIT（超过公司/行/付款界限）。权限先于报表数据返回，任意请求公司无权则整次拒绝。GET无写副作用；PUT对账不写审计/工资/付款表。CSV由服务端决定列与安全转义，前端不重算金额。
