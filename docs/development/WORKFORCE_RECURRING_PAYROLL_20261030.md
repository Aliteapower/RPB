# 长期薪资方案与月度自动生成：第一阶段

项目D:\RPB；2026-10-30；仅本地实现和验证，不提交、推送或部署。

## 使用方式

管理原薪资模板/个人薪资方案中增加“月度生成方式”：人工核定（保留原方式）、按核准出勤自动生成、自由员工全额月薪。

- 个人方案按生效日期跨月有效，无需每月新建；工资月份仍由管理员选择。选用模板保存个人快照，模板修改不自动联动。
- 自动月薪一次设置每周计薪工作日和标准日工时；分母按当月工作日重算，核准工作＋带薪假换算计薪日，每日上限1。缺排班、缺核准、非配置日及特殊待遇保留异常。日薪按标准日工时换算；时薪沿用核准秒数。
- 自由员工必须月薪且关闭自动加班；有效雇佣月份全额基本工资，月中入离职和无薪假不折算，不要求打卡、排班、工时核准或无来源说明。整月无雇佣关系不生成工资。
- 固定收入项目可选全额或随月薪计薪比例折算。旧项目保留原规则；新固定项目默认全额。当月奖金、报销和扣款只在当月保存，不复制至下月。
- 含自动方案的批次在创建时生成calculated预览；异常不自动放行，确认/发布/付款仍为原流程。全旧方案保留draft。调整后重新计算；自动模式无需重填天数或重复说明。
- 月中调薪/多段方案首期明确阻断，不能取月末标准覆盖整月。个人方案已被确认工资使用时，继续要求新增生效版本。

## 模块与接口

完整设计：[模块化设计](../superpowers/specs/2026-10-30-workforce-recurring-payroll-design.md)。执行计划：[第一阶段](../superpowers/plans/2026-10-30-workforce-recurring-payroll.md)。API：[契约](../api/2026-10-30-workforce-recurring-payroll.md)。

PayrollGeneration是纯计薪单位生成器：校验规则、聚合日期、生成单位及异常，不访问数据库。PayrollSources捕获雇佣区间、有效个人方案及核准来源；PayrollCalculationService和PayCalculator分别合成扣缴与金额。PayrollBatchService负责原状态、幂等、审计及冻结。前端PayGenerationFields及payGeneration.ts由原模板和个人方案编辑复用。

没有新增路由、角色、权限或AppGate白名单。新字段存在于既有JSON快照，字段级NON_NULL保留旧JSON结构，无数据库迁移或旧资料回填。未调整租户来源锁架构。

## 验证记录

证据目录：target/payroll-recurring-20261030。使用target/local-postgres-current.txt的owner=D:\RPB、port=61644；后端测试自行创建UUID隔离schema并按现有测试生命周期清理，不操作运行中的业务schema。源码基线在baseline/，原始哈希baseline-hashes.json，本轮文件清单changed-files.json。

前端广泛回归：当前730项，720通过、10失败；改动前源码副本726项，716通过、同样10失败。frontend-baseline-comparison.json的introduced为空。本轮新增4项全部通过；随后新增规则非法输入及类别切换检查，最终聚焦23项全部通过。原失败名称详见该JSON，包括旧排班复制/布局Pinia、核准、工资单、报表、个人方案列表fixture和排班周视图场景，不扩大修复。

首次前端直跑缺少项目Vue loader，已按scripts/tests/workforce-register.mjs重新运行。首次隔离Maven未配置workingDirectory，随后修正为D:\RPB；另一次基线和RED编译共用构建输出导致NoClassDefFoundError，后续Maven顺序执行，不把这次运行作为基线成功证据。首次新增PG测试的入离职fixture遗漏company_effective_date约束，已修正fixture，未改变数据库约束。

最终后端聚焦59项全部通过（final-backend.log、final-backend-result.json）：PayrollGeneration 10、PayCalculator 12、StatutoryCalculator 10、ConfiguredStatutoryCalculator 8、CdacPayroll 4、PayrollBreakdown 3、PayrollCsv 1、PayrollReportValues 1、新增PG业务场景10。最终聚焦修复后代码涵盖连续两月、一次性项目不串月、自由入离职/无薪假、无雇佣月份排除、缺来源阻断、同月方案变化、正常整月22个工作日全额、创建重试、真实AppGate拒绝理由及越权、审计失败回滚、特殊日仍需人工说明。

此前广泛后端回归115项中114通过，剩余1项是新增整月场景的测试时钟仍停留10月28日，违反现有禁止补排过去日期的限制；已在测试逐日排班前设置模拟时钟，最终该场景通过。原有PayrollBreakdownPostgresTest 26、WorkforcePayrollConfigurationPostgresTest 20、WorkforcePayrollPostgresTest 24、WorkforcePayrollApiTest 3共73项通过结果保留（regression-backend.log）。后续改动仅修复自动模式的说明条件，原manual分支未改变，复用这些已核实的原流程/并发/付款/权限回归结果，不无依据重跑全部。

主要命令：
```powershell
mvn -q -f target/payroll-recurring-20261030/validation.pom.xml '-Dtest=PayrollGenerationTest,WorkforceRecurringPayrollPostgresTest,WorkforcePayrollPostgresTest,WorkforcePayrollConfigurationPostgresTest,WorkforcePayrollApiTest,PayrollBreakdownPostgresTest' test
# 最终59项的完整可重放命令保存于target/payroll-recurring-20261030/final-verification-commands.ps1；结果见final-backend-result.json。
node --import ./scripts/tests/workforce-register.mjs --test src/test/frontend/*.test.mjs scripts/tests/workforce-phase*.test.mjs
node --import ./scripts/tests/workforce-register.mjs --test src/test/frontend/payroll-recurring-ui.test.mjs src/test/frontend/payroll-breakdown-ui.test.mjs src/test/frontend/cpf-profile-reference.test.mjs src/test/frontend/cdac-ui.test.mjs
npm run build
```

最终前端聚焦23项通过（final-focused-frontend.log）；vue-tsc及Vite构建通过（final-build-frontend.log）。未跑整个项目后端全量测试。后端保留Mockito动态agent/CDS的既有JDK警告，无测试跳过。

## 审查与边界

独立只读代码审查已执行。额外复核发现自动生成说明不能充当特殊日的人工审批说明；该项由red-note-probe.log实际复现，再由PayrollGeneration和PG特殊日测试验证修复；无来源人工核对仅适用于manual，attendance由AUTO_SCHEDULE_MISSING阻断，fixed由有效雇佣和方案决定。自动规则只生成计薪依据，不代替原出勤审批。

本阶段不实现岗位考勤默认值、定位打卡、居家地址审批、共享模板批量升级、条件项目编排、事务外并行或增量计算。自由属性在本阶段是长期薪资方案的核算策略，不改写原出勤事实。未替换原8096运行服务，未做真实手机定位验收、浏览器人工视觉验收或人数压测；不能据此宣称第三阶段性能收益。

## 回退限制

保留已保存方案及冻结工资，不删除业务JSON。旧版本不理解generation/proration，不能让旧计算包继续计算这些新方案。需要回退时先暂停相关工资写入，并提供识别这些字段的兼容处理；不得以回退名义重新折算自由工资或清除已确认工资。生产发布须单独授权。

## API / Database / TDD / Code Review 结论

- API：复用原路径、权限、CSRF、版本及命令重放。Rule.generation和Item.proration为可选属性，生成标记仅在calculation.lines[].input上表达后端依据；未让请求决定工资来源。
- Database：无迁移，所有新查询带tenant/company/employee条件；雇佣区间与hire/leave/company history交集。没有更改用户运行schema或历史快照。
- TDD：先观察缺少生成类型/实现的编译失败，再实现；额外说明漏洞和类别切换均先复现失败，再由最终测试验证修复。未回滚用户已有文件。
- Code review：独立审查及针对人工说明边界的复核完成。模块职责清晰，保留原锁、权限与冻结；本轮未遗留已确认必要修复项。前端10项基线失败如实保留，不宣称全项目全绿或生产就绪。
