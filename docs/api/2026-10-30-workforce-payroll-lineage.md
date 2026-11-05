# 工资工时与项目来源契约（Task2，2026-10-30）

沿用既有 payroll 配置、批次、工资单路由和权限；无新增路由/迁移。所有新增字段 NON_NULL，旧构造/旧 JSON 保留，冻结快照不回写。金额十进制字符串，工时整数秒。

- Item 可选 itemId（UUID）、source：origin/sourceId/version/eventId/independentReason。新编辑赋稳定编号，旧项目不按名称合并。可写source.origin仅支持approved_work/paid_leave；profile/monthly_input由系统根据容器推导到lineage，不能客户端自报（避免容器新版本与所见旧版本混淆），eventId 为 scheduleId:异常代码；sourceId 为实际来源身份，version 为所见版本。独立扣款解释必须独立于已折减工时，员工投影移除内部解释原文。
- SourceRef 可选 evidence：actualSeconds（当前事实）、approvalState（current/missing/stale）、approvedSeconds（仅 current 非空）、startAt/endAt、eventIds（不含备注）。旧 workSeconds 仍为计算输入；新捕获仅有效核准能提供计薪秒数。
- PayrollLine/PayslipLine 可选 hours：actualSeconds/approvedSeconds/approvalState/basis/normalSeconds/paidLeaveSeconds/overtimeSeconds/workingDays/payableDays。fixed 显示 not_applicable，月薪/日薪只显示计薪日，不伪造计薪小时。
- Component 可选 lineage：origin/sourceId/version/itemId/month/formula/quantity/unit/rate/multiplier/proration/unroundedAmount/rounding/roundedAmount/sourceKeys。所有来源确定性生成，不含生成时间；旧项目显示来源证据不足。项目来源键细到 itemId 或旧项目序号，不按 profileId/label 去重。
- 来源重复/重叠、已折减事件的来源扣款无独立解释等加入工资 issues，沿用确认阻断；不静默裁剪、删除旧人工项目或改变跨公司规则。
- 缓存 ENGINE_KEY 升级至 v2-lineage；旧缓存全算。确认继续全量比较。员工工资单仅本人必要工时/项目计算说明，无内部备注/地址/CPF资料/他人来源。

测试清单：真实纯计算工时及跨月阈值；重复来源/重叠/项目事件防重；旧JSON；PG补卡过期/确认阻断/本人隐私/冻结；增量等价/旧缓存；Vue真实渲染及类型检查。

## 实施补充

实际区间 evidence.startAt/endAt 来自当前 Session.inAt/outAt，明确是打卡区间而非计划区间；不保存GPS、地址、备注或核准人。actualSeconds 来自当前实际净工时投影。新捕获对 missing/stale 核准不提供有效核准秒数，SourceRef.work/paidLeave/unpaidLeave 是阻断计算输入的零占位；这会改变未核准/过期草稿的金额预览，不代表合法应付为零。hours.approvedSeconds 缺省、状态与 APPROVAL_MISSING/STALE 阻断同时存在，UI明确“待核准或证据不足；当前金额仅为阻断预览，不可确认为应付工资”。重新核准后才恢复计薪来源；已确认/发布快照不变。

固定全额基本工资 lineage 为 profile / fixed_full_v1 / fixed_salary / full，不暴露内部生成器1/1为出勤日。月/日薪 hours 只展示计薪单位；时薪分别展示正常、带薪假和加班秒数。没有来源证据时不反推有效核准。

新组件保存适用的 Employee CPF / Employer CPF / SDL 政策ID/版本、政策公式/费率、中间金额与原舍入规则。StatutoryCalculator 的可选 Trace 仅捕获既有局部BigDecimal变量，不重新计算金额；员工投影不包含CPF原始个人资料。雇主CPF明确是已舍入总CPF减已舍入员工CPF，SDL保留个人精度并注明公司汇总舍入。精确未舍入值无法有限十进制表达时以分子/分母字符串保留（实际金额及roundedAmount仍为十进制字符串）。旧组件不填造公式或来源。

草稿旧项目进入编辑器后补itemId，不改历史快照；同名两项保留独立编号。个人覆盖比较仅比较项目label/kind/amount/proration，新增跟踪ID/source不误报金额覆盖。

测试旧读取oracle继续独立维护；新捕获增加evidence并排除无效核准的计薪输入，因此不再声称与旧Capture指纹/完整JSON相等。旧已核准事实和金额应严格保持，新增证据、阻断预览变化及新全量/增量等价分别验证。原opt-in规模性能基准旧/新完整快照严格相等断言不再兼容新证据，本轮未运行该基准、没有性能提升/旧快照全等声明。

可写source版本仅指所见有效核准版本，保存input/profile不会修改它。approved_work来源扣款必须包含scheduleId:异常代码的eventId及独立原因；paid_leave来源引用有效核准scheduleId/version且该核准确有带薪假，eventId缺省。系统推导profile/monthly_input采用保存后的配置/输入版本，不让客户端自报它们。

独立审查边界：显式项目只允许消费本月payable核准引用；上月context不得形成本月项目。人工月/日薪basic来源为monthly_input，另保存profile费率版本键；工时证据unavailable不等于工资阻断，合法人工核定单位可确认发布。UI“阻断预览”提示依据实际核准/来源issues，不能仅凭hours.approvalState推断不可支付。

## 聚合来源引用解释

Lineage与可写Item.source含义不同：聚合基本/加班项目的origin表示工时计量来源类型，顶层sourceId/version记录费率所属的个人方案及其版本；实际核准身份、版本及payable/context角色在sourceKeys中，不得把顶层profile ID当作approval ID解析。固定全额基本来源为profile；人工月/日薪基本来源为monthly_input，其顶层引用员工当月输入版本，sourceKeys另列个人方案费率版本。显式事件项目的sourceId/version则是所引用班次及核准版本。
