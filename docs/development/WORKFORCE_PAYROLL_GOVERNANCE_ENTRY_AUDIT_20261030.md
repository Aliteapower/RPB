# 计薪治理写入口现状与闭合清单

D:/RPB，初始审计2026-10-30，最终更新2026-10-31。原表保留实施前状态；阶段3及末节记录最终写入口分类、验证及边界。依据完整流程规格第13节及用户确认13.9。

阶段1–6实现及最终代码复审已通过；下面原表保留阶段1前审计历史，最新入口分类见阶段3及本文末节。本轮浏览器/窄屏仍未验收，不把原表“待实现”当最新源码结论。

| 入口/实现 | 当前作用域与影响 | 本轮所需处理 / 当前状态 |
| --- | --- | --- |
| WorkforcePayrollController PUT salary-templates → PayrollConfigurationService.saveTemplate | tenant/company共享可变；个人快照不自动变，但未来预览会变 | 阶段1受管物化模板禁止直接改，其他店不可见/不可用；阶段3旧共享写入口纳入发布/草稿兼容策略。待验证。 |
| 同Controller PUT pay-profiles → saveProfile → configs.saveProfile | 公司工资权限，标准/项目/CPF/CDAC/付款资料，可能新增或覆盖 | 阶段2统一申请；阶段3完整标准化经济匹配。首次适用发布模板原样采用免审，已有员工经济变化即使换已发布模板也须审。待实现。 |
| 同Controller PUT batch inputs → PayrollBatchService.input → PayrollRepository.input | 月度核定单位、项目、CDAC覆盖及说明；未确认批次 | 稳定company/employee/month批准版本，防批次作废重建绕过；纯备注/事实确认与经济字段按实际作用分类。待实现。 |
| CdacBulkService.apply → configs.saveProfile | 当前直接复制方案并改CDAC，绕过saveProfile应用服务 | 必要入口，必须批准证明/统一守卫或转为逐员工申请；不能仅藏UI。待实现。 |
| PositionSalaryDefaultsService.save/preview | company/position/date共享候选映射；不会主动改员工 | 治理模板按当前店可用性重检；保持只改候选，不暗改已有方案；应用到员工仍受统一守卫。阶段1/3。 |
| 治理发布/启用/派生及升级（新增） | 发布/采用/有效个人配置分别有状态 | 阶段1不授工资权；阶段2/4派生与升级不会原地改母版或自动员工方案；必要证明在阶段3核验。 |
| PayrollBulkService / calculate / confirm / publish | 读取配置后计算/冻结，不单独录入工资标准 | 沿用原版本/来源/月底/非本人守卫；捕获仅用有效版本证明。阶段3回归。 |
| PayrollPaymentService.register/reversal | 仅已发布台账登记/冲销，不改工资金额 | 原付款/权限/精度/幂等保留；历史指纹回归，不新增工资更正规则。 |
| PayrollReconciliationService.compare及报表导入/对比 | REPEATABLE_READ只读expected与已冻结报表比差额，无工资写入 | 已核实不是工资金额导入旁路；不新增不必要审批。新增写式导入必须走相同守卫。 |
| RuleTemplateService.save | 原已headOffice限定，tenant共享考勤规则母版；schedule保存shift快照 | 保留中央管理与来源版本，阶段3核对其规则是否存在绕开个人经济Rule的覆盖，不把工时事实审批混成工资审批。 |
| ShiftService.save | 当前store manage，可定breakPaid/休息/时段/异常；改变未来排班事实净工时 | 工资率/day/week/倍数仍来自个人Rule；排班复制规则形成固定shift snapshot，不能回写已打卡schedule。阶段3须逐字段分类与旁路测试；未覆盖必要合同经济覆盖不得宣布完成。 |
| ScheduleService、BulkSchedule/WeekCopy/BulkAdjustment/RestSwap | sourceWrite、门店管理；打卡后原schedule不可改，保存班次规则快照 | 原事实/排班生命周期继续，影响核准则按来源过期；不能作为手工工资率/项目写旁路。阶段3审计/回归。 |
| Clock/Correction/Leave/Approval及批量核准 | 原记录事实与工时核准，有版本、权限、不能本人审批等约束 | 工时核准独立，不新增第二次金额审批；新金额不得通过工时判定说明录入。阶段3回归。 |
| PositionService.save attendanceMode | 原headOffice，岗位规则复制到新分配个人，不实时联动已个人覆盖 | 阶段3核对WorkModePayroll匹配/整月模式守卫，改变方式不能得到未批准经济Rule。 |
| WorkModeService.save / adoptPosition | 员工管理权；save从明日起、adoptPosition隐式复制岗位方式 | 两个入口都需核对，free固定方案不匹配原已阻断，不为新审批改跨公司或自由全额规则。阶段3专项。 |
| WorkforceEmployeeService生日/雇佣事实、证件PR资料 | 员工事实，可经capture刷新CPF适用/比例依据；不等于自定义工资标准 | 事实合法性、范围及source过期保留；CPF/CDAC自主经济设置仍在个人申请守卫，不借纯资料变更伪装调薪。阶段3核对。 |
| StatutoryPolicyService | headOffice且当前所有tenant门店权限/合法年份；集中政策版本及快照 | 保留中央集中管理，不改为门店审批，不新增法定公式。 |
| Repository写方法 | 当前apps可直接saveProfile/saveTemplate/input | 最终守卫须覆盖实际应用调用图及精确服务器证明；SQL超级用户/测试fixture写不等于产品授权入口，不声称防御数据库管理员任意SQL。阶段3未闭合。 |

未发现独立“工资金额写式导入”产品服务；当前reconciliation只读。此结论依据当前src/main/java调用图，不代表未来API可绕过。最终需附每个必要入口的拒绝/批准/历史兼容测试，明确允许的事实更新与尚未治理的配置范围。

相关设计：[完整流程第13节](../superpowers/specs/2026-10-30-workforce-end-to-end-payroll-design.md)；[分阶段计划](../superpowers/plans/2026-10-30-workforce-template-governance.md)。

## 新增财务范围（阶段5/6待实现）

用户已授权奖金/AWS/AW及跨期补缴。事项申请、年度事实/预测写入、补发/更正事项、AW年度复核、法定责任与实缴登记均须加入最终入口清单，不以旧OW-only限制排除。工资经济事项复用统一审批，年度事实须有证据与revision；外部已缴录入不等于批准新工资，也不自动向员工追扣。年度/同月累计fingerprint与并发锁必须在确认时校验。当前仅设计核对完成，未宣称新计算或登记入口存在。

## 阶段3实施状态与验证证据

| 入口 | 当前处理与必要验证 |
| --- | --- |
| Config仓储 saveTemplate/saveProfile | 默认启用精确证明守卫；无合法发布/批准或经济不变证明即409。三处仓储绕过已真实RED→GREEN；旧批准proof回滚及单件result伪配已RED→GREEN。 |
| 旧 template/profile API | 复用原权限/版本/冻结校验后登记精确非审批证据或拒绝转申请；首次适用发布模板原样采用与经济不变免审，换模板经济变化不能免审。首次标准身份及明确CDAC适用性可声明；FG/FF、exempt及个人manual覆盖须审批。guard/API及17项配置回归完成；重复创建冲突断言已修正并定点GREEN，原方案保持不变。 |
| 原 batch input API/仓储 | 月份稳定有效内容为基线；自动生成单位原样回传只存原始有效单位及核准说明，不保存隐藏未审数值。auto/fixed、不同合法数值拒绝及冻结批次备注专项已GREEN。批准稳定输入尚无raw行的精确重放真实缺陷已修复，CdacPostgres原失败重验通过。 |
| CdacBulk | 每员工独立申请提交/回执事务；已有同日开放提案返回conflict；旧API不直接写有效profile。4项治理专项、11项原批量回归GREEN；确定性交错验证同ID新版本也返回stale，旧已完成command缺reason精确重放不新增写入。 |
| PositionSalaryDefaults save/preview | 只改候选，采用时仍过个人守卫；按目标日期及本店可用性重检，不暗改现有个人方案。 |
| 发布 enable / 批准派生 | 普通采用先登记精确不可变release/adoption来源证据→仓储模板守卫→adopt同事务；派生先真实proof→仓储守卫。显式未来派生合同日期生效，普通启用日不作为个人回溯下限。 |
| PayrollSources / Incremental / Workbench | 正式profile与月份先有效解析，稳定approved月优先legacy再default；假设预估单独typed上下文；治理证据入行/FP/cache v3，沿用证据固定sourceVersion。177项中的173项通过，发现的4项失败已在59项组合中全部修复重验。Q2缺声明假设预估实际RED→GREEN；未知after保留可信before，完整FG/FF/exempt仍可预估，最终42项受影响组合全部通过。 |
| RuleTemplate / Shift | 考勤时段、休息paid/窗口/配额、宽限与异常形成已保存排班快照及待核准工时，不写profile.rate/dayHours/weekHours/OT倍率。affectsBonus目前仅资格事实，无自动奖金金额。保留原事实核准；不新增第二次金额审批。实际专项验证改Shift不变旧Schedule/FP，新排班核准后gross1500且无薪资申请，GREEN。 |
| Schedule / copy / bulk / correction / leave / approval | 仅事实/工时，used schedule不可改；工资金额仍来自已证明个人Rule/月度输入。工资原核准→计算→确认→发布→付款29项已GREEN，其他事实接口沿用原来源审查。 |
| Position.attendanceMode / WorkMode.save/adoptPosition | 不更改金额Rule；岗位仅新采用时复制，个人例外保留；free/fixed不匹配及整月切换阻断。两入口实际回归在177项内，未新增员工管理→工资权限。 |
| 生日 / 雇佣 / PR证件事实 | 原事实权限与校验，capture验证存储方案proof后叠加生日；PR证据不一致继续阻断，事实及revision入FP。个人CPF选择/PR模式/CDAC自定设置仍由个人经济守卫覆盖。 |
| StatutoryPolicy | 继续总部及完整门店权限集中维护/版本冻结，不转成门店审批、不新增公式。生日及法定回归在177项内。 |
| reconciliation/report import / payment | 当前报表对比只读，不是金额导入；付款只作用已发布冻结台账。阶段3不改已发布更正/跨公司规则，不回写旧工资或付款。 |

V110已用独立自建随机schema执行安装前有数据→实际迁移专项：未来profile、active优先、单个及多个相同void输入保留valid、不同void输入标ambiguous、缺表fail-closed、原JSON不改且无伪审批，GREEN。完整XML、精确方法和失败修复分类见阶段3报告；父级负责最终Spec/Quality判断。阶段5/6新增wage事件、年度事实、补缴及缴纳入口仍待其实现与验收，不能宣称整轮业务完成。

## 阶段3只读预检补充（保留原审计要求，实际证据见上表）

- Shift时段、breakPaid、休息规则及考勤grace/anomalies改变待核准事实。Schedule只接收shiftId并固定完整快照，已使用排班不能修改；工资rate/dayHours/weekHours/OT倍率来自个人Rule。后续应验证新旧班次快照、核准过期与工资指纹，不给事实流程增加重复金额审批。
- Position.attendanceMode经新分配/adoptPosition复制到日期化WorkMode；现有WorkModePayroll对free/fixed不符和月中free切换有阻断。须同时回归WorkMode.save和员工岗位变更触发adoptPosition，不能只测一个入口。affectsBonus当前只是资格事实输出，没有自动写奖金金额；新奖金功能若消费它，须重新列明来源和批准关系。
- 员工birthDate会在capture中覆写CPF投影。批准proof应先校验数据库存储的原批准profile，再叠加有版本的合法事实；否则合法生日纠正会错误触发经济快照失配。事实变化仍须让未确认工资过期，冻结记录不改。
- request Content hash不等于各profile/input结果hash；仓储守卫需核对不可变submitted快照的完整具体结果及归属。批准proof和有效结果同事务建立，不能靠ThreadLocal/boolean或缺依赖时关闭guard。精确proof检查不可忽略ItemSource等防重复证据。
- 现有测试fixture有直接构造repository和无WorkModeResolver的路径。阶段3应显式登记测试legacy基线并注入真实必要解析器；不得为了旧测试绿而让产品缺proof自动视为legacy。

本节保留源码预检的分类依据。生产没有缺proof默认legacy、空依赖放行或布尔许可；测试历史资料显式登记legacy，经济变动经真实save→submit→approve。WorkMode两入口、生日及法定回归在177组合通过范围内；Shift新增专项另有GREEN证据。

## 2026-10-31 新财务入口补充审计

下表描述最终实现边界。阶段5/6及整轮独立代码复审通过，PG/组件/本地实际HTTP验收通过；浏览器及窄屏未验收。上文“阶段5/6待实现”保留为审计历史，不代表最终源码状态。

| 新入口 | 治理与实际作用 |
| --- | --- |
| PayChange 月份 wage_event | 沿用同员工/生效日申请、提交固定快照与批准证明；普通批次只接收同月事项。奖金/AWS录入合同确定金额，不能通过经常性模板、旧 input 或年度事实新增未审工资。 |
| AnnualWageService 外部月份修订 | 总部及全租户门店 App Gate；有凭证的历史收入/原责任事实，不是内部工资批准入口。旧月份与事件不得删除，旧事件内容不可改；只允许规定的事实修订及新增遗漏事实。历史内部冻结与批准月份不能被外部 opening 遮蔽。 |
| AnnualWageService 年度复核 | 复用年度与月度唯一法定内核，保存不可变 assessment；创建复核不产生新 gross、付款或实缴，也不自动追扣员工。 |
| PayChange supplemental | 新增独立补发提案仍走原统一申请与精确 proof；一个提案绑定同员工原贡献月、服务器来源 hash 和完整新事项。普通月份跨期限制不放宽；批准日期与合同应付日均在确认时核对。 |
| SupplementalService 建立/计算/确认/发布 | 建立只接受 request/revision/hash/proposal 引用，不接收替换金额；source 锁内重捕获有效批准、原单/外部事实、年度、已扣/实缴与公司 SDL，确认才消费事件及认列差额。原已算未付继续原单支付，不能复制为新收入。 |
| 原 PayrollPaymentService 与独立补发付款 | 两类现金台账独立；原付款与冲正均参加 batch→source 锁序，防补发确认捕获旧现金余额。补发仅支付本单已发布正额余额，不回写原工资快照。 |
| SettlementService 法定事实 | 总部与全租户门店 App Gate；证据修订分别记录原实际已扣、系统外实缴及官方利息，不代表新增工资、已批准追回或实际转账。未知仍为 null。 |
| SettlementService 公司月份 SDL | 独立完整性证据及公司目标/原认列/实缴，汇总已确认补发收款人，不能只按当前在职名册；公司 floor 差额不分配成某员工的扣款。 |
| SettlementService 月份/年度责任结算 | 持久化原期资料与年度 context，固定指纹并唯一消费；只认列 CPF 责任差额，不产生新工资、SDL 或 CDAC。新旧员工/雇主正负责任分别保留，不能相互净抵后当成全部结清。 |
| SettlementService 实缴登记/冲正 | 有外部凭证、精确命令幂等、正额分配及整笔冲正；本金与官方利息独立。员工追回资格必须满足实际引用与法定条件，后台不因填写 cause 自动认定，也不自动扣未来工资。 |
| 员工本人普通/补发工资单 | 专用安全 DTO；可查看本人金额、OW/AW、原期/结算期及付款余额，不返回管理端完整 proposal、审批理由、分类凭证、年度身份或内部 fingerprint。 |

阶段6 API：[独立补发及法定缴纳](../api/2026-10-31-workforce-supplemental-settlement.md)。阶段5 API：[奖金与年度上下文](../api/2026-10-30-workforce-bonus-aw.md)。最终测试、复审、实际HTTP及剩余边界见[本地实施记录](../release-notes/2026-10-31-workforce-template-governance-financial.md)。未发现未受控的现有工资金额写入口；真实浏览器、外部平台与明确unsupported组合不在通过声明内。

仍保留的能力边界：考勤规则/班次是有快照的工时事实，工资率及个人金额仍由批准方案控制；没有新增自动奖金公式。未发现工资金额写式导入产品入口，未来新增写式导入必须走同一守卫。本轮不防御数据库超级用户任意 SQL。未核实的低工资 AW 截短交叉档明确阻断；真实银行、CPF 平台申报、跨公司工资重新归属及历史已发布工资金额改写均不在新增规则范围内。

终审补充：合法旧CDAC空设置从完整冻结项目/组件保留旧手工责任，target与original recognised共用来源，缺明细保持未知；非法review不放行，actual deducted/remitted仍需独立证据。实际原两店旧工资读取及OW25补发通过，未补造免税资格或修改历史profile。跨年复核结算输入与后台当前月限制对齐，原历史事实月份仍锁所属year。
