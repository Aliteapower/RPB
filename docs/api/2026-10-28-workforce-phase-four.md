# 第四阶段 API 合约

基址/api/v1/stores/{storeId}/workforce。读写身份/AppGate/tenant/store，工资接口另校验PayrollAccess；private,no-store，同源PUT X-RPB-Workforce:1。金额/小时/日数字段均规范十进制字符串，秒数long。

|方法|路径|请求→返回|
|---|---|---|
|GET|/payroll-home|PayrollHome {companies:Option[],employeeId:uuid|null,policy:Policy}|
|GET|/salary-templates?companyId|SalaryTemplate[]|
|PUT|/salary-templates/{id}|TemplateWrite→SalaryTemplate|
|GET|/pay-profiles?companyId|ProfilePage {items:PayProfile[],employees:Option[]}|
|PUT|/pay-profiles/{id}|ProfileWrite→PayProfile|
|GET|/payroll-batches?companyId|BatchSummary[]，最近24个月最多100条|
|PUT|/payroll-batches/{id}|{companyId,month}→BatchDetail|
|GET|/payroll-batches/{id}|BatchDetail|
|PUT|/payroll-batches/{id}/inputs/{employeeId}|InputWrite→BatchDetail|
|PUT|/payroll-batches/{id}/actions/{commandId}|{expectedVersion,action:calculate/confirm/publish/void,note}→BatchDetail|
|PUT|/payroll-batches/{id}/payments/{paymentId}|PaymentWrite→BatchDetail|
|GET|/my-payslips?year=2026|PayslipSummary[]|
|GET|/my-payslips/{batchId}|Payslip（仅本人发布记录）|
|GET|/payroll-batches/{id}/payslips/{employeeId}|Payslip（管理预览，冻结计算结果）|

Rule {basis,rate,dayHours,weekHours,overtimeEnabled,overtimeMultiplier,items:Item[]}；Item {label,kind:ow_allowance/reimbursement/deduction/employer_charge,amount}。
CpfProfile {citizenship:citizen/spr/foreign,birthDate:date|null,prDate:date|null,prMode:GG/FG/FF,electionReference:string}；foreign允许DOB空，SPR必填prDate，FG/FF必填依据。
Policy {version,year,owCeiling,cpfSource,sdlSource,notes:string[]}。
SalaryTemplate {id,companyId,name,rule,enabled,version}；TemplateWrite {expectedVersion,companyId,name,rule,enabled}。
PayProfile {id,companyId,employeeId,employeeName,effectiveFrom,templateId,templateVersion,rule,cpf,version}；ProfileWrite {expectedVersion,companyId,employeeId,effectiveFrom,templateId,rule,cpf}。
Profile保存时templateVersion由服务读取，rule是引用后独立编辑的复制值。已确认引用不可改同条，另建生效日期；不自动变更历史方案。
InputWrite {expectedVersion,workingDays:string|null,payableDays:string|null,unitsReviewed:boolean,specialDaysReviewed:boolean,items:Item[],note:string}；LineInput含employeeId/version及同字段。
BatchSummary {id,companyId,companyName,month,status:draft/calculated/confirmed/published/voided,version,employeeCount,totalGross,totalNet,totalEmployerCpf,totalSdl,totalEmployerCost,createdAt}。
BatchDetail {batch:BatchSummary,employees:Option[],inputs:LineInput[],calculation:PayrollSnapshot|null,sourceChanged:boolean,payments:Payment[]}。
PayrollSnapshot {sourceFingerprint,companyName,month,policyVersion,lines:PayrollLine[],issues:string[],totalGross,totalNet,totalEmployeeCpf,totalEmployerCpf,totalSdl,totalEmployerCost,calculatedAt}。
PayrollLine {employeeId,employeeName,employeeNo,profile:PayProfile|null,input:LineInput,workSeconds,normalSeconds,overtimeSeconds,paidLeaveSeconds,unpaidLeaveSeconds,basicPay,overtimePay,allowances,reimbursements,deductions,grossPay,employeeCpf,employerCpf,sdl,employerCharges,netPay,cpfRateCode,components:Component[],sources:SourceRef[],issues:string[]}。
Component {label,kind,amount}。SourceRef {scheduleId,storeId,workDate,approvalVersion,sourceFingerprint,workSeconds,paidLeaveSeconds,unpaidLeaveSeconds,payable:boolean}；payable=false前月周上下文，仅用于OT阈值。
PaymentWrite {employeeId,amount,paymentDate,method:cash/bank/other,reference,note,reversesId:uuid|null}；Payment含id及以上字段、actorId、createdAt；反向金额正数、method与原一致、一次全额冲销，无delete。
PayslipSummary {batchId,companyName,month,netPay,paidAmount,status}；Payslip {batchId,companyName,month,status,line:工资显示DTO,payments:Payment[],paidAmount,remainingAmount}；line只暴露工资项/规则/units/小时/CPF/SDL，不返回CPF出生/PR资料、其他员工、source指纹和管理备注。

错误：400 WORKFORCE_INVALID、WORKFORCE_PAYROLL_UNSUPPORTED（年份/地区/复杂费率）、WORKFORCE_CPF_PARTIAL_MONTH；401/403/404原错误；409 WORKFORCE_CONFLICT、WORKFORCE_PAYROLL_BLOCKED、WORKFORCE_PAYROLL_SOURCE_CHANGED、WORKFORCE_PAYROLL_OVERPAYMENT、WORKFORCE_PAYROLL_PROFILE_LOCKED；500原持久错误。
calculate保留issues返回200，issues非空不能confirm。状态命令expectedVersion+UUID和原payload/actor校验，重复返回原命令响应；相同UUID变参数409。inputs写入后batch回draft、清除旧计算，expectedVersion针对当前行。payment UUID同参数重放，超净应付409；仅published登记，员工自己不得登记。支付不是银行操作。
