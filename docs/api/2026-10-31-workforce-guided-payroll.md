# 导向式工资接口契约

## 岗位方式默认（增量，兼容旧端）
现有 /api/v1/stores/{storeId}/workforce 下：
- GET position-salary-defaults 增加可选 basis=monthly/hourly/daily；省略仍只返回旧V107记录。
- PUT position-salary-defaults/{id} 的 DefaultWrite 增加可选 basis；省略写旧表，明确方式写新表。方式必须与模板一致，身份/公司/岗位/日期/方式不可变，expectedVersion控制冲突和原载荷重试。
- GET pay-profile-preview 增加可选 basis；明确方式按最新已生效新映射优先，显式禁用不回退；没有新映射才按模板方式匹配旧默认。未来映射不提前生效；显式templateId仍需原授权及basis一致。省略保留旧解析。
- ProfilePreview.defaultMapping沿用原字段，增量basis仅新映射出现。ProfileWrite提交时验证映射版本、最新适用性、方式与模板。不改旧快照。

权限：原PayrollAccess、sourceWrite及公司/来源门店AppGate。员工管理权不获得工资权。原401/403/404、WORKFORCE_INVALID 400、WORKFORCE_CONFLICT 409不变。读取无副作用、private/no-store；不扩大local runtime白名单。

## 数据库评审
V114新增 workforce_position_basis_salary_defaults，不编辑V107。tenant_id/company_id/position_id/basis/effective_from唯一；模板FK含tenant/company，岗位FK含tenant。basis约束monthly/hourly/daily、正版本、审计时间及snapshot；旧记录不迁移，不删除。new/legacy id联合查找发现冲突拒绝；写入幂等与scope检查由服务层/乐观条件共同实现。

## 测试要求
月薪/时薪同日并存、未来生效、禁用不回退、旧无方式路径不变；非法方式/模板、租户/公司越界、审计失败回滚、版本冲突、重复命令及原历史快照保持。

## 员工月度总表
GET /api/v1/stores/{storeId}/workforce/employee-payroll-board?companyId&month。
PayrollAccess校验公司及全量来源门店；REPEATABLE_READ只读，无查询副作用。所有合法员工（最多原500人边界）一次返回，前端筛选不改变公司确认范围。不额外分页取工资/N+1。
Board包含companyId/month/batchId/batchVersion/status/frozen/sourceChanged/asOf/issues/rows/totals。
Row包含employeeId/name/no、当前岗位名称（冻结且无快照岗位则未记录）、basis/profileId、gross/deductions/net、issues、原PayrollLine和PayBreakdown。无已保存计算、来源过期、员工有阻断时金额未知null，不能回退零占位；全量阻断时totals为null。冻结只读原snapshot，不以当前姓名、岗位或方案覆盖。
金额decimal strings；员工扣款=employeeCpf+CDAC+otherDeductions，由后端精确相加；公司totals使用PayBreakdown.of(snapshot)，保留company SDL舍入。错误/权限/匿名和原workbench一致；新增GET仅加入精确本地白名单。

## 核工时汇总
原approval-board新增employees只读字段，按日期/岗位/姓名范围、在状态筛选和分页之前归集。包含排班跨度（含休息）、实际净工时及有效核准工时；任一证据缺失则对应合计null，不能将未核准当零。员工详情仍复用原日记录/单条核准；批量继续原preview/commit逐项事务、幂等和本人/来源限制，无新批量命令。

## 原期补充汇总
GET supplemental-month-summary?companyId&month，工资权限与来源门店沿用SupplementalService。
只归集originMonth相符且confirmed/published的补发（含未付款）；草稿/测算/作废不入正式总额。返回每单来源ID、员工姓名、所属/结算月、收入差额、员工扣款、本次应实发、雇主CPF/SDL法定调整；法定调整可能影响其他原期，不冒称当月公司成本。
formalGross/formalNet为原期正式普通工资加已确认补发；原普通批次不存在或尚未正式确认则formal总额为null，外部期初亦不能当作公司普通工资为0，不能当成完整应付。补发合计与记录仍可查看。人员按员工去重。asOf标注补充汇总时点，不修改原冻结报表。
付款跨月沿用付款账，不作为第二份收入；提前结薪接入后须归集其正式金额再完成月度全量统计。

ProfileWrite新增可选defaultBasis，用于新UI从旧默认回退预览时仍执行按方式解析的提交校验，防止预览后新增/停用新映射被旧解析绕过。旧客户端省略仍保留旧语义；字段不进入原工资快照。

GET my-latest-payslip 返回 {slip: PayslipSummary|null}，只查询当前员工本人已发布最新普通工资单，跨年度定位，不返回管理端字段；权限和AppGate与my-payslips一致。无工资单显式null，非本人或未发布不可见。只添加精确GET本地入口。

## 岗位薪资业务入口
GET payroll-positions?companyId 返回有效岗位Option(id,name)，先校验PayrollAccess公司/全量门店，不依赖employeeManage，也不授予岗位增删权限。不暴露岗位考勤规则或其他员工数据。岗位薪资页按公司、岗位、计薪方式办理已有模板的默认采用；模板发布和个人工资变更仍走各自原有审批。

my-latest-payslip同时返回supplementalMonth（本人最近已发布补发的结算月或null）；员工默认查看年度取普通所属月和补发结算月较新者，避免普通工资在旧年度而新补发被默认隐藏。

## 审查修复契约
BoardRow新增approvals(scheduleId,storeId,workDate,state)待核准来源元数据，包含上月周上下文，不返回无权来源；先校验公司全量来源门店。UI按原来源单定位，核准权限仍由原服务决定。
approval-board读取与preview.selection.filter新增可选employeeId精确筛选，原五字段Filter保留；必须在原actor/store/self范围内筛选，eligible/preview使用同一过滤。search仍是额外模糊条件，不能替代按人作用域。
无批次且需月度单位时，用户显式建立原公司月草稿后进入原inputs；不猜单位、不绕过审批或确认。办理返回使用结构化fromPayroll/returnStoreId/companyId/month及查看筛选，只接受当前或已授权门店内的固定工资总表路径，不接受任意returnTo URL。

BoardRow.suggestedEffectiveFrom依据目标公司/月唯一有效雇佣期间起日批量读取，供缺方案办理预填；无明确或多期间时null，不能猜。冻结行不拿当前雇佣事实补历史。预填不修改计薪来源哈希，也不绕过原方案生效和审批规则。

## 在职分段结薪增量（用户2026-10-31明确包含）

独立模块payroll.segment；普通月度月底守卫不变。覆盖同一员工/公司/月的连续前缀：首次从月初（任职前日期无收入），下一段从上一正式截止次日起；截止日必须在本工资月且已结束，月底段须月底后确认。草稿不占用范围，正式确认以来源锁和员工公司月锁校验前驱，禁止跳段、覆盖或重复。正式段不可作废释放范围，后续更正走补发/纠错；只有草稿可作废。

累计计算复用PayrollSources/PayrollCalculationService；查询截至营业日期（含上月周阈值上下文），不纳入截止后的未核准记录。月薪自动单位仍以完整月工作日为分母；固定/人工月薪分段须显式核对累计工作日/应付日，不按日历比例猜算。收入和扣款保留原规则，累计金额与已结金额都显示；负差额、AW/事件等尚不能准确归属的类型明确阻断，不能预估正式入账。后续普通整月计算排除已有正式段的员工并纳入段版本指纹。

全部路径前缀/api/v1/stores/{storeId}/workforce：
- GET /payroll-segments?companyId&month：公司月份独立结算清单及普通正式/分段/补发统一汇总（金额十进制字符串，未知保持null）；附员工可选项。
- GET /payroll-segments/{id}：详情及冻结累计/本次三组金额、来源变化、付款。
- PUT /payroll-segments/{id}：expectedVersion(0新建)、companyId、employeeId、month、cutoff、workingDays/payableDays（仅人工累计单位）、unitsReviewed、specialDaysReviewed、note；不接受调用者任意收入/身份/比例；新建ID重试同载荷返回同草稿，冲突409。
- POST /payroll-segments/{id}/commands/{commandId}：expectedVersion/action(calculate/confirm/publish/void)/note。命令按租户、员工、办理人、载荷绑定回放；首次正式确认后覆盖范围冻结。发布独立动作，不能直接转账。
- PUT /payroll-segments/{id}/payments/{paymentId}：沿用PaymentWrite，独立记录实际付款及原付款全额冲正；本人禁止、超付禁止、幂等ID。
- GET /my-payroll-segments?year；GET /my-payroll-segments/{id}：本人已发布工资单脱敏投影（原期/覆盖日期/收入/员工扣款/公司承担/实发/已付），不得暴露内部治理指纹或其他员工。

新表V115：独立单（tenant/company/employee/month/cutoff、状态版本、原请求、冻结snapshot及审计），命令和付款；正式截止唯一，业务锁保证连续。无历史工资回填。新查询继续PayrollAccess所有来源门店授权；写入sourceWrite，正式确认/付款禁止本人；员工查询使用原本人身份解析。

核查清单：截止后异常不阻断已完成段；连续两段之和等于同月完整累计（CPF/CDAC/SDL舍入）；跨周加班上下文不重复；同月普通与分段并发互斥且普通已确认拒绝分段；相同/重叠截止并发只成功一次；补发后续不再重复结收入；已结前缀来源更正提示独立纠错且原快照不变；发布/付款分离，权限/租户/本人/重试全覆盖。

### 分段累计与兼容约束

- `Calculation.cumulative` 是不含独立补发的原薪累计快照，供年度基础归集；`targetGross/targetNet` 是含本月已正式OW补发的本次累计目标。页面展示后者，满足目标累计−此前已正式结算＝本次差额；不把补发再计入年度基础。
- 保留已结前缀的原来源引用和金额。后续发现已结前缀补卡/撤销/工时变化，显示独立纠错待办；后续段仍从冻结前缀加新期间测算，不将更正自动再付。当前工资总表也显示这种来源变化。
- 同月使用同一实际CPF档案、方案、政策及月薪分母；生日更正、身份/PR/政策或待遇变化需独立复核，不能只凭方案ID未变就在后续段重新计旧CPF。
- 公司SDL先合计所有当前正式员工累计个人精度，再按政策公司舍入，减已正式责任；个人SDL精度不提前舍成两位。普通批次与分段同一口径，正式确认互斥。存在无法关联的外部原期工资/公司SDL期初时阻断分段确认，不把期初当零。
- OW补发后续段扣除所有已正式补发收入及扣款；补发“法定责任”与“可扣员工金额”不同的情形不自动从后续工资追扣。AW、负差额等超出本增量准确计算范围时明确阻断并保留原规则和独立复核。
- 写锁顺序统一先取得租户工资来源独占锁，再取得来源写入共享锁和员工月锁，避免两确认同时升级锁造成死锁；日期覆盖表唯一键是最终防重约束。
- 员工工资单只公开本人已发布记录及核准工时、计薪标准、本单应付日、三组金额与公开付款字段；不公开CPF身份档案、治理请求、来源指纹、办理人ID或内部说明。
- 月度统一正式汇总的雇主CPF只归入对应原月份的责任，跨月AW重估另按原法定记录追踪，不把全年责任差额混进本月公司成本。旧冻结工资与旧报表不回写。

### 已结范围与补发守卫

- 人工累计应付日不得超过截至本次实际任职日数；截止前无实际任职不能创建正式结薪，预付工资不是本模块的已赚取工资。
- 独立原单的OW补发服务日期须在原缴费月、最新正式截止日以内，且全段有该公司任职覆盖。未结期间走后续结薪，不得先补发再进入后续工资。违反返回 `SEGMENT_SUPPLEMENT_RANGE_UNSETTLED`。
- 部分月份仍在职时，AW补发须等整月覆盖或实际离职最终截止后再办理，违反返回 `SEGMENT_AW_REQUIRES_FINAL_RANGE`。此约束避免AW先行导致剩余期间无法准确结薪。
- 上述边界在原变更基线、审批以及补发建立/测算/确认共同执行，不仅靠页面；原年度完整性与员工扣款责任守卫继续保留。

## 满勤待遇估算（2026-10-31）

POST `/api/v1/stores/{storeId}/workforce/pay-plan-estimate` 为只读敏感查询，无持久化副作用或幂等命令记录。请求 `{companyId,employeeId,month,rule,cpf,cdac,days,dayHours}`；只接受明确1..当月天数的整数满勤日、0..24标准日小时（>0），禁止额外AW事件。复用工资权限、员工公司/门店范围；员工资料生日覆盖客户端生日。缺必要身份/政策返回原业务错误，不将扣款未知当零。草稿规则可估算但不代表审批通过。

响应 `{month,days,dayHours,workSeconds,breakdown,policy,assumptions}`，金额字符串；policy为可读规则名称及版本，不是内部UUID。假设完整月满勤、所有全勤条件满足、标准普通工时无加班、无请假/临时调整；固定津贴及现金住宿为OW。使用已启用整月法定政策；个人SDL保留精度，公司成本为个人估算，不是公司汇总应缴。正式计薪仍使用核准实际来源。所有请求/响应private,no-store；无新工资写入权限。

`ow_attendance` 仅经常性月全勤奖类别；实际月底所有本月相关排班必须有current核准及明确全勤资格，任一已核准不符合则0，资格未知阻断`ATTENDANCE_BONUS_REVIEW_REQUIRED`。月底前独立段为deferred不付，最终月底再判定。旧无此类别不受影响；工资月度人工补项拒绝此类别。新SourceEvidence可选fullAttendanceStatus空字段省略，保留旧构造/快照读取。
