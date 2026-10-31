# 第四阶段设计：工资方案、公司批次、CPF/SDL、工资单与付款登记

日期2026-10-28；D:\RPB。用户已授权继续第四阶段。复用前三阶段与原组织/Auth/AppGate；先落盘再实现；不提交、推送或部署。现有钱包及其他未提交内容保留。

## 范围和简化选择
按公司、自然月、SGD建立唯一有效工资批次；工资读取核准来源，不修改打卡或核准记录。工资模板复制到个人生效方案，之后互不联动；批次计算→确认冻结→发布给员工→登记付款。公司独立，集团报表/导出/试运行对账留第五阶段。
本轮实现普通工资OW的CPF（2026私营部门五张表、低工资分段/年龄/SPR年资及GG/FG/FF）、SDL。年度奖金/AWS等AW、跨期补缴、年终AW重算和外籍劳工税自动计算暂不支持，不能把AW伪装成普通津贴；额外雇主费用可人工登记。未配置或超出法定版本年份须阻断，不套旧费率。
月薪按“月基本工资÷当月工作日×应计薪日”计算；固定普通津贴同样按月比例。日薪由管理员核对可计薪日（含获批带薪假）；时薪按核准普通工时+带薪请假。月/日薪的计薪日数由管理员明确填写，不能从打卡小时简单猜测月薪或无薪扣款。
自动加班为明确启用的合同日/周规则，合并同雇主跨店事实工时；排除带薪请假，日/周重叠不重复计费，跨月周读取月初所属周的前序核准工时。月薪加班小时基本率12*月基本工资/(52*44)，日薪=日基本工资/标准日小时，时薪=小时基本工资。倍率启用时>=1.5、周阈值<=44。是否适用Part IV、轮班平均、休息日/公众假期待遇不能由系统猜；标记日有工作时必须人工核对并填写说明/普通工资补项。未启用自动加班明确显示“按所选合同方案计算”，不宣称覆盖所有法定场景。

## 官方依据（核对2026-10-28）
- CPF rates: https://www.cpf.gov.sg/content/dam/web/employer/employer-obligations/documents/CPFcontributionratesfrom1Jan2026.pdf
- CPF年龄/SPR/取整: https://www.cpf.gov.sg/employer/employer-obligations/how-much-cpf-contributions-to-pay
- SDL: https://www.cpf.gov.sg/employer/employer-obligations/skills-development-levy
- 月薪/不完整月: https://www.mom.gov.sg/employment-practices/salary/monthly-and-daily-salary
- OT: https://www.mom.gov.sg/employment-practices/hours-of-work-overtime-and-rest-days
- 工资单: https://www.mom.gov.sg/employment-practices/salary/itemised-payslips
2026 OW月上限8000；总CPF四舍五入到元、员工份额舍分到元、雇主=总-员工。生日档位次月生效；SPR周年次月升档；SPR转换月非1日因缺分段工资资料阻断，不能对整月乱算。外国员工CPF=0；SDL按每人月工资0.25%，最低2最高11.25（零工资0），先保留精度汇总再公司向下取整到元。非默认SPR费率须填写获批依据。仅支持SGD及新加坡时区排班。

## 数据和模块
新增V091：workforce_pay_templates、workforce_pay_profiles、workforce_pay_batches、workforce_pay_inputs、workforce_pay_commands、workforce_pay_payments。全部tenant/company复合FK；profiles关联employee；inputs/payments关联batch及employee；batch同公司同月非voided唯一。JSON快照不跨产品复用。金额持久NUMERIC或规范十进制字符串，前端不用浮点计算薪资。
后端：statutory纯CPF/SDL；payroll/PayCalculator纯薪资/加班；payroll/PayrollAccess工资权限；PayrollConfigurationService/Repository模板和个人方案；PayrollSources核准及资格读取；PayrollBatchService/Repository状态/快照；PayrollPaymentService付款；PayslipService本人已发布单；controller只编排应用服务。
个人方案属于employee+company+effectiveFrom，含templateId/version、复制的工资规则、CPF资料。可新增生效方案；已被确认批次引用的同一方案不可覆盖，使用新生效日期。单公司同月有多段工资费率需要另行拆分核算，本轮阻断；公司调动分公司批次并由人工按各公司计薪日确认。

## 权限
总部需原companyAllowed。门店工资管理员必须已有workforce_administrators.payroll_company_id显式委派；employee_manage不等于工资权限。公司全部所属门店与所有实际支援来源店必须在账号现有范围且AppGate允许，否则拒绝整公司工资访问。普通门店管理员不得见薪资。平台管理员仍不得读人事薪资。
工资入口另有/payroll-home返回可管理公司，不能从普通home泄露金额。普通员工仅/my-payslips读取本人published记录；未发布草稿不可见，不返回生日、其他员工或管理快照。本人不得确认包含本人薪资的批次；付款也不得登记自己的记录。

## 来源一致性与锁
对workforce事实来源写命令（employee/schedule/clock/correction/leave/calendar/approval）增加tenant级共享transaction advisory lock，通常多店打卡仍可并发；工资capture/calculate/confirm取同key排他锁，防止查询后插入新排班/员工或补卡竞争。只锁新workforce产品，不锁旧产品表。锁必须在原employee/calendar锁之前取得，顺序一致；读取本人资料不加锁。模板/个人配置写入及公司发薪设置也使用共享锁。
批次同UUID命令重试和expectedVersion；批次锁→来源排他锁→读取→保存审计。来源指纹包含员工资格/生效薪资方案/全部核准版本与指纹/行输入和company配置；calculate保存快照。confirm必须重算指纹一致、所有当前月排班已核准且有效、无错误和待审、月已结束，防止漏计月底出勤。月初周上下文要求核准有效，且不重复支付前月。
确认冻结明细/费率/员工和公司名称/来源历史；之后来源变化只提示待调整，不暗改已确认金额。发布/付款基于已冻结记录；已发单差异在后续月份有说明的工资项目调整，不覆写旧单。确认后未发布且无付款可作废再新建；published不可作废。

## 工资项目与金额
规则字段basis monthly/daily/hourly、rate、dayHours、weekHours、overtimeEnabled、overtimeMultiplier、items。Item={label,kind,amount}，kind ow_allowance/reimbursement/deduction/employer_charge；普通津贴/加班为OW和SDL工资，费用报销不算工资；扣款不降低CPF基数，雇主费用不扣员工净薪。模板项目为每月固定项，行输入项目为本月一次项，月薪固定OW津贴按计薪日比例，其他按明确录入额。
每项金额HALF_UP至分；CPF按官方整元；SDL保留每人原精度后公司整元。净薪=基本+OT+津贴+报销-人工扣款-员工CPF，不能为负；雇主成本另列。任何扣款由管理员核对合法依据并在note说明，不自动推定合法。
月/日薪须workingDays(1..31，月薪用)/payableDays(0..31，最多半日单位)及unitsReviewed；应计薪日不能超过工作日，日薪不能超过月天数。特殊日出勤须specialDaysReviewed+说明。默认不捏造计薪日；预计算将问题列在对应员工行。

## 页面
独立PayrollHome导航、SalaryTemplateList/Form、PayProfileList/Form、PayrollBatchList/New、PayrollBatchDetail、PayrollLineForm、PayslipList/Detail、PaymentForm；中英文、紧凑专业、局部CSS。详情展示工资构成、普通/加班小时、CPF双方、SDL、状态/来源变化；工资单独立可打印视图，不接银行或CPF申报。
付款登记支持分次、现金/银行/其他、实际日期/参考号/说明；不可超净应付。反向登记必须引用原付款且等额、只能一次，保留原记录；不连接银行，不声称发生转账。已发布工资单显示实际付款记录，未付款不写成已付款。

## 验收
纯计算：低工资50/500/750边界、五个年龄档、生日月/次月、SPR1/2/3年及FG/FF、OW封顶/取整、未来年份/转换月阻断、SDL公司整元；薪资三种基数、月/日计薪日、加班日周不双算/跨月/跨店/公司隔离、负净薪。
真实PG：新迁移、工资显式委派/越租户越公司拒绝、普通店长无工资、草稿不泄露、过期核准/新增排班使确认失败、确认后快照不漂移、本人不能确认、版本/重放/并发/audit回滚、重复公司月、分次付款/超付/反向、员工只见发布本人。沿用pointer PG新schema。HTTP同源/no-store、前端实组件/双语/窄屏及实际API/浏览器链路。未生产迁移、不含真实银行/CPF申报或真实设备验收。
