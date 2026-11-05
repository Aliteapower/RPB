# 休息规则与考勤核准本地验收（2026-10-29）

范围D:\RPB；只本地实现与验证，无提交、推送或部署。沿用用户确认的“休息规则→打卡事实→考勤判定→工时核准”，不自动扣工资或发放/扣减全勤奖金额。

## 使用入口

- 管理→考勤设置→共享规则模板：总部管理员新增、修改、停用；门店经理可查看及选用。模板保存相对上班时间的分钟数，参考时间仅用于编辑显示；选用到不同开始时间的班次会平移，需核对实际时间。
- 管理→班次模板→编辑：显式启用多段休息和考勤规则，可复制模板快照或直接设置；关闭则沿用原规则。11–23点、午餐13–14、中场15–17、晚餐20–21均不计薪时，计划汇总显示12小时跨度、4小时固定非计薪休息、8小时净工时。
- 原打卡页选择休息段并打进出卡；出勤/打卡/核准详情共用一个休息摘要组件，显示计划、已休、剩余、超额和不足。固定不要求打卡时不凭空显示少休；actual允许窗口不当作quota或整段计划休息。
- 原工时核准页显示疑似迟到/早退、缺卡、休息偏差及已批请假；人工确认/豁免均需说明。缺卡只待核准，明确确认为旷工后才核准0工时，不生成虚假出勤。缺休息记录、开放休息、未结束排班等独立阻断不能绕过。
- 管理子页面共用“← 返回管理”及面包屑，保留当前门店及companyId。原保存、取消和权限路径保留。

## 模块边界

没有新增业务页面。`scheduling.rules`负责规则值对象校验及共享模板应用/仓储，原Shift保存规则快照；`attendance.BreakCalculator`负责区间并集和休息摘要；`approval.AttendanceJudgement`负责应工作时间与异常，原ApprovalService保存人工判定及审计。事实时间、异常秒数、全勤资格和工资金额独立。

前端复用AttendanceRulesEditor和BreakSummaryTable，管理导航集中在layout及managementNavigation，规则摘要集中在restPolicy，核准判定和双语摘要集中在attendanceReview。保留现有员工、门店、公司、AppGate、补卡和请假流程，不增角色或绕过门店权限。

## 验证证据

目录：`D:\RPB\target\workforce-attendance-rules-20261029`。

- 前端135项通过：原106项、共享导航5项、规则编辑10项、打卡/核准14项。涵盖跨日offset、窗口/额度/打卡约束、模板复制不联动、关闭新规则回旧模式、管理权限、默认空态、未知结果同请求重试、已核准判定刷新/再次核准、停用旷工判定、共享休息摘要。
- 后端相关138项通过：BreakRulesCalculator5项、AttendanceJudgement5项、规则PostgreSQL18项、模板API/本地安全2项、原出勤37项、核准47项、工资24项。
- `npm run build`（vue-tsc及Vite）和隔离后端打包通过。未跑整个项目全量测试。
- 本地API准备16项、结果6项检查通过：同租户跨店模板、经理不可改共享模板、员工不能读管理模板、非授权店403、迟到6分钟/早退10分钟按5分钟excess宽限分别记录1/5分钟、quota120实际100分钟显示不足20分钟、无卡先MISSING后人工absence。
- 浏览器实际验证模板修改至v2、选用/保存班次快照、返回管理、计划12−4=8、缺卡说明后确认旷工、quota摘要。截图 `shift-rules.png`、`missing-before-review.png`、`absence-reviewed.png`、`quota-summary.png`。
- 独立员工在临时loopback5197真实完成IN/BREAK_START/BREAK_END/OUT四个事件，ruleId正确、原始区间保留，quota未补成标准时长；原5196测试身份未替换。仅此新账号临时获得本店workforce.view，跨店403，结束后禁用账号、撤销会话并停止自建代理。
- 独立审查修复：actual/quota可选窗口不自动排除迟到；新规则不被隐藏的旧duration/window参数阻挡；Interval/Session新属性仅属性级NON_NULL保留旧字段；当前有效核准保留完整decisions以支持再次核准；新规则摘要不误显示旧“无休息”。
- 原22类表135条基线记录原字段哈希保持不变。哈希排除V098追加的nullable列，仅比较迁移前已有字段；详见data-before/data-after。4条原核准当前来源指纹、工时及requiresReview在升级前后相同。
- 本轮独立验收员工 `RULES-LOCAL-1029`、验收班次及共享模板已停用。测试事实、核准和审计保留；没有删除原数据或更改现有员工规则。

## API Review Report

新增GET/PUT attendance-rule-templates，使用原/v1/store路径、同源头和private/no-store；Controller调用应用服务，显式记录契约见docs/api/2026-10-29-workforce-attendance-rules.md。管理读需原门店管理权限，写需总部权限；跨租户仓储条件、模板来源tenant/version检查、乐观版本和同内容重试均保留。原clock重放现在同时校验breakRuleId，核准绑定fingerprint/version及完整decisions。

## Database Review Report

V098只添加租户规则模板表以及session活动规则ID、clock事件规则ID、核准decisions nullable列；无自动种子、历史规则回填或角色授予。表含tenant FK、主键、查询索引、时间戳、version和状态/JSON形状约束。应用校验规则ID、时段、不重叠和固定代码。原快照规则null保持旧JSON形状，新规则复制后不联动历史排班。

按target/local-postgres-current.txt核对owner=D:\RPB、PostgreSQL61644，仅事务应用至原专用验收schema `workforce_browser_6b3d0ca974744b0c9b681036f8462dd6`。8096/5196运行新构建；原8095实例及其他schema未改。

## TDD / Code Review Report

测试在实现前或同时定义，首轮缺规则类时失败，随后覆盖正常/失败、权限、跨租户、AppGate三类拒绝、同源与窄路径、重试、审计回滚、来源冲突、快照兼容、真实打卡与核准。代码职责在规则、事实、判定及核准之间保持清晰；没有新增工资金额公式。独立静态审查问题已修复并由回归验证，未留已确认阻塞问题。

## 风险与回退

固定时段模式依计划与实际出勤交集计算；需要精确按实际休息计算的班次应选择actual/quota并配置打卡要求。模板跨班次按相对时间平移，使用者应核对实际时间；“计划8小时”不代表系统已判断劳动合规。

只有显式启用的新规则生效。旧班次、旧核准继续旧逻辑；此轮不自动迁移历史或全店套用示例。回退时保留V098表/列与审计，不能用不认识新rules的旧计算包直接处理新规则排班；应先暂停新规则写入并确认所有新规则数据的处理方案。生产发布须另行授权。
