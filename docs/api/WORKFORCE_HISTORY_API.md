# 员工产品历史只读 API

所有路径以 `/api/v1/stores/{storeId}/workforce/history` 开头，仅 GET，返回 `Cache-Control: private, no-store`。入口 `/stores/:storeId/workforce-history?history=1` 独立于当前业务布局。

- `/context`：`{employeeManage:boolean,self:boolean,companies:Option[]}`。只提供本人身份、现有管理门店能力和显式工资公司范围。
- `/attendance?from=YYYY-MM-DD&to=YYYY-MM-DD&employeeId=&limit=50&offset=0`：既有 AttendancePage。
- `/attendance/{id}`：既有脱敏 Detail，普通员工仅本人，管理者仅 managedStores。
- `/attendance-month?month=YYYY-MM&limit=30&offset=0`：既有脱敏 MonthPage，查询不写入判定。
- `/payslips?year=YYYY`：本人已发布月工资 PayslipSummary 数组。
- `/batches?year=YYYY&companyId=`：`{id,companyName,month,status,employees:Option[]}` 数组；必须指定有工资权限的公司，只展示已冻结 confirmed/published 月工资。
- `/payslips/{id}?scope=mine|managed&employeeId=`：既有脱敏 Payslip；mine 仅本人已发布；managed 要求 employeeId，使用冻结快照，不重新捕获来源。
- `/segments?year=YYYY&scope=mine|managed&companyId=`：脱敏分段工资 Slip 数组；默认 mine，仅本人 published；managed 必须有公司工资授权，仅 confirmed/published。
- `/segments/{id}?scope=mine|managed`：同一脱敏 Slip 和权限边界。
- `/supplements?year=YYYY&scope=mine|managed&companyId=`：脱敏补发工资 Payslip 数组，按结算年份；默认 mine，仅本人 published；managed 必须有公司工资授权，仅 confirmed/published。
- `/supplements/{id}?scope=mine|managed`：同一脱敏 Payslip 和权限边界。

管理分段工资校验冻结累计快照和用于差额展示的上一单来源门店。管理补发工资按 proposal.source 读取原月工资或分段快照及其来源门店；原单缺失、非正式状态、公司/员工不符均404，不退回当前来源捕获。opening 和 employment-zero 原始来源没有批次门店快照，沿用原工资公司全店范围。补发 Batch 模型未保存 companyName，因此名称沿用现有工资单映射从公司目录读取；金额、项目、结算月份均使用冻结快照，付款仅查询既有登记，不重算工资。

历史授权使用现有 evaluateHistory：workforce.view、租户、门店归属和曾开通/曾启用证据仍必需；从未开通、无门店权限、平台禁用拒绝。管理工资使用原工资公司授权、公司全部门店和快照 sourceStores 检查，仅商业状态改用历史 gate；employeeManage 不授予工资权限。工资列表年份2000–2100，考勤区间沿用现有范围限制和分页规则。

401 WORKFORCE_UNAUTHENTICATED；403 原商业/历史授权拒绝码或 WORKFORCE_FORBIDDEN；404 WORKFORCE_NOT_FOUND；400 WORKFORCE_INVALID；500 WORKFORCE_PERSISTENCE_ERROR。不存在写API、重试命令或幂等记录。原业务接口与写方法保持原语义。

验证清单：从未开通拒绝；到期/停用历史可读；原写API仍拒绝；本人/他人、跨租户/门店、普通经理/工资公司授权隔离；快照来源门店权限撤销拒绝；纯快照读取不执行 sources.capture；列表年份、分页、详情、返回保持只读。
