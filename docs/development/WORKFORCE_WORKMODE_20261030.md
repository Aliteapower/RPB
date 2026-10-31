# 第二阶段：岗位默认、个人考勤策略、定位打卡及居家地址审批

2026-10-30，项目D:\RPB。用户授权本地实现、验证和更新文档；未提交、推送、部署或替换原运行服务。第一阶段已有工作及其他用户材料保留。

## 使用与规则

- 管理→岗位管理：新增默认考勤方式，旧岗位未设置时保留原行为。岗位仍由原总部权限维护。
- 员工列表→考勤方式与居家地址：管理员追加按生效日期保存的个人例外，或复制“当前岗位默认”。新员工首次分配岗位时复制默认；现有员工调岗默认次日生效，已设置的个人例外不被调岗覆盖。以后岗位改名/改默认不会联动旧员工策略。已保存策略只追加、按日期和版本检查，不覆盖历史。
- 管理→门店打卡定位点：具备本店员工管理权限者配置文字名称、经纬度及10..1000米半径。工资授权不授予该权限。
- 员工打卡页→考勤方式与居家地址：本人提交地点和申请生效日期。管理员在员工页审查批准或拒绝并填写说明，不能自审；需要具备员工全部门店的管理范围。申请不可原地修改坐标，变更需新申请。个人策略/居家申请最早次日生效；过期未批准申请需按新日期重新提交，原批准地址在新批准地址生效前继续有效。
- 现场：每次上下班/休息动作均获取位置，按门店地点由服务器校验。
- 外勤：需要打卡、不要求固定定位点；地点说明可选，允许只提交description，不伪造经纬度。
- 居家：需要已生效的批准地点；所有动作按该定位点校验，待审/拒绝地点不能打卡。
- 自由：免打卡、免补卡、免工时核准，不显示缺排班联系管理员提示、不伪造出勤或核准记录；工时看板单列“免打卡／免工时核准”。基本工资继续遵循第一阶段fixed方案，月中入离职/无薪假不折算。

自由考勤与薪资配置保持独立：未配置fixed薪资的自由员工、配置普通考勤却使用fixed薪资、同月跨自由/非自由方式均阻断工资确认，给出明确错误。旧第一阶段fixed方案在尚无显式考勤策略时保持兼容。检查限定发薪公司的实际雇佣区间，不能让其他公司/入职前的策略影响本公司工资。

## 模块职责

| 模块 | 职责 |
|---|---|
| WorkModeDtos / WorkModeRules | 显式接口、四种方式和legacy校验、坐标形状、距离/精度/时效纯计算 |
| WorkModeRepository | 版本化个人历史、站点、居家申请和事件证据；租户条件、FK、索引 |
| WorkModeResolver | 按日期解析员工策略，不动态追随岗位默认；按日期选批准居家地点 |
| WorkModeService | 岗位默认复制、个人例外/恢复继承、站点写入及原权限/审计 |
| HomeAddressService | 员工申请、管理员审批、禁止自审、版本及重试 |
| WorkModeClock | IN时策略和目标快照、后续动作校验、事件证据；审批来源只引用需要的不可变信息 |
| WorkModePrivacy | 本人/完整管理范围之外的响应脱敏，覆盖考勤、事件、核准/历史/看板；不改存储或指纹 |
| WorkModePayroll | 自由/普通考勤与长期薪资方案的纯一致性检查 |
| WorkModeFields / WorkPointFields | 复用方式及地点表单；WorkModePage负责个人/审批页面，ClockSitePage负责站点 |

原ClockService继续负责出勤状态机，原ApprovalSources/Service负责工时；不把定位或地址审批塞进工资公式。原EmployeeService在已有锁和事务中调用默认复制。Controller只调用应用服务。第三、四阶段的并行/缩锁/增量重算未实施。

## 定位证据、重试及历史

位置来自浏览器，服务器校验有限经纬度、120秒内/未来不超过30秒、精度≤100米且≤半径、Haversine距离≤半径。拒绝定位/超时/精度不足不提交成功动作，不生成假位置。

IN固定当次策略、站点或批准地址的版本；OUT和休息沿用该Session快照，即使管理员以后移动站点或批准新地址也不改变当前班次。旧Session无快照时仍走原方式。成功command重放先于定位过期检查，并核对原action/schedule/breakRule/location；未知结果重试原载荷，不换位置或commandId。

没有Session时，工时核准来源只包含不可变的mode和assignment版本，不把日后可变的站点几何信息作为历史核准依赖。已有Session包含当次不可变目标。原始事实、审批快照及冻结工资不回写。

## 隐私与权限审查

独立审查发现原出勤/核准接口只需当前门店权限，若直接返回新Session快照，会绕过完整管理范围要求读取居家地址。已新增响应投影：只有本人或员工全部门店管理者可见完整地址/定位；其他合法的本店考勤查看者保留工时及指纹，但目标居家地点、地址ID、事件定位及嵌套审批快照均脱敏。数据库证据、源对象和SHA-256指纹保持原值。独立复核确认覆盖列表、详情、历史、看板、审批成功和重放响应。

沿用AppGate、原员工管理范围、私有缓存头及同源写限制。新增本地安全放行仅限精确GET/PUT路径，其他HTTP方法及近似路径仍拒绝；不授予角色、订阅或额外工资权限。

## 迁移与验证环境

V104只给岗位追加nullable attendance_mode、Session追加nullable clock_policy；新增个人策略、站点、居家申请、事件定位四张表，含租户/员工/门店复合FK、状态/版本/日期唯一约束与索引。无自动回填、自动定位、地址种子或角色授予。

只按target/local-postgres-current.txt使用owner=D:\RPB、port=61644的PostgreSQL，测试自行创建隔离schema并清理。未修改原业务/浏览器验收schema。实际Flyway V103→V104升级、validate、重跑0项及旧打卡/冻结工资原字段不变均有测试。

证据目录：target/workforce-workmode-20261030。基线源码和哈希为baseline/、baseline-hashes.json；构建隔离在build/。最终后端命令为verify-backend.ps1，统计final-backend-result.json及regression-backend-result.json；本轮文件及最终SHA-256见changed-files.json。

## 验证过程

先观察缺少新类型/纯生成器的RED，再实现核心。首轮15项核心通过；扩展20项中发现免核准分类仍为exception，补测试修正为exempt。隐私复核问题通过部分门店管理员的真实PG服务测试覆盖，确认专用策略接口403、考勤/事件/核准历史脱敏且DB/指纹不变。

前端第一次全量750项中737通过，13失败：10项为第一阶段已核实基线；新增3项由打卡恢复按钮文案变化导致旧测试恢复分支未执行、遗留状态连锁影响。已恢复原“恢复上次操作”入口文案，并保留新定位原载荷重试。原phase2打卡专项回归恢复到仅3项既有基线失败。新自由页面测试进一步确保不会要求免打卡员工选择排班或联系管理员。

最终后端聚焦28项全部通过：规则3、薪资方式一致性2、无Session核准/旧JSON兼容3、API3、本地安全1、实际Flyway升级1、真实PG业务15（含并发、租户FK、AppGate三类拒绝、审计回滚、默认复制/个人例外/恢复继承、位置重放及隐私脱敏）。见final-backend.log和final-backend-result.json。

此前相关后端广泛回归220项全部通过：工时看板55、核准47、出勤37、工资24、岗位PG17、岗位迁移1、岗位API3、原工时API3、第一阶段生成10及当时新增核心23（包含继承测试重复执行，不能当作独立业务场景数）。结果保存regression-backend-result.json。最后关于显式新策略的站点版本、无Session地点依赖及公司雇佣区间细化后，已重新执行上述聚焦28项；legacy分支及旧JSON结构不变，复用原回归证据。

最终前端全量751项，741通过、10项原基线失败；与第一阶段已核实失败名称集合相同，frontend-baseline-comparison.json的introduced为空。新workmode交互19项，以及关联出勤、核准、导航和第一阶段薪资合计49项聚焦全部通过（frontend-focused-final.log）；随后恢复原打卡恢复入口文案后，原phase2打卡＋新workmode共37项中34通过、仅3项原基线失败（frontend-clock-regression.log）。全量最终结果已覆盖该修复。

vue-tsc及Vite构建通过（frontend-build-verified.log），最后可选siteVersion类型补充后再次vue-tsc --noEmit通过（final-typecheck.log）。未执行整个项目后端全量测试。测试工具保留既有Mockito/CDS与WebSocket端口警告，未将警告隐瞒为无警告运行。

主要验证命令：
```powershell
# 在D:\RPB执行；该脚本含最终15个PG场景的精确方法选择，避免重复运行继承测试。
./target/workforce-workmode-20261030/verify-backend.ps1
node --import ./scripts/tests/workforce-register.mjs --test --test-concurrency=4 src/test/frontend/*.test.mjs scripts/tests/workforce-phase*.test.mjs
node --import ./scripts/tests/workforce-register.mjs --test src/test/frontend/workmode-ui.test.mjs src/test/frontend/attendance-review-ui.test.mjs src/test/frontend/approval-workbench-ui.test.mjs src/test/frontend/management-navigation.test.mjs src/test/frontend/payroll-recurring-ui.test.mjs
npm run build
npx vue-tsc --noEmit
```
未声称全项目后端全绿、真实设备定位、物理防伪、真实手机或浏览器视觉验收；未做多人压测。

## 回退

不删除V104、策略/地点/事件或已确认工资。旧包不理解新定位要求，不能让旧包继续处理已经采用新策略的员工打卡；若需回退，先暂停相关写入并准备兼容策略，保留原始证据和历史。生产发布、迁移或替换服务须另行授权。

## API / Database / TDD / Code Review 结论

- API：显式DTO、稳定错误、原同源/登录及AppGate、精确本地路径；本人和完整员工管理范围分开，无新工资授权。
- Database：V104仅增量；租户/员工/门店FK、状态版本/唯一索引及历史兼容已由PG与实际升级验证。原运行schema未应用迁移。
- TDD：缺类型/方法先RED，免核准分类、自由页面误导提示、恢复文案回归均复现后修复。新增隐私边界由独立审查指出并补真实PG回归，不回滚用户材料满足流程。
- Code review：独立审查发现的P1地址泄露已修复并复核；原始存储/源对象/指纹未脱敏改写。最终未留已确认必要修复项。不是生产发布批准或真实设备验收。


## 2026-10-31 许可多选与外勤名称增量

后续实现以 [多选规格](../superpowers/specs/2026-10-31-workforce-clock-options-design.md) 和 [V113 API](../api/2026-10-31-workforce-clock-options.md) 为准；上文保留首版实施记录。

- 办公室、已批准居家和外勤可同时获准，另设默认方式；free/legacy 继续互斥。既有记录缺许可集合时仅允许原方式，迁移不自动扩大员工权限。
- 每次打卡可选择原班次快照允许的方式。办公室上班、外勤下班使用同一 Session，实际方式只记事件，不重复工时或工资。
- 外勤可填写至多200字符的地点／客户名称，随服务器实际打卡时间展示；名称可单独记录，不新增GPS必填或每次外出审批。
- 个人许可无实质变化不增加策略历史、版本或审计；命令回执保留精确幂等。岗位旧null与相同单项也视为无变化。已有历史和未来记录不删除。
- 居家申请分待办与历史，明确批准待生效、过期、同日冲突。过期请求需本人重新申请，不修改原生效日期或自动批准。
- 隐私投影覆盖许可 options 中的居家地点，即使默认方式为办公室／外勤；法定政策、自由员工全额工资、已确认工资和付款快照规则不变。

验证和发布结果记录于本轮 WORKFORCE_CLOCK_OPTIONS 发布记录；未做真实设备GPS或真实业务申请审批。
