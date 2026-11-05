# 薪资第三阶段：批量采集、事务外计算与受控并行

项目 D:\RPB，日期 2026-10-30。授权仅本地实现和验证；不提交、推送、部署，不替换运行服务。承接第一、二阶段，第四阶段增量重算未实现。

## 实现

- `PayrollSources` 批量读取同公司月份范围内的员工、工作门店、雇佣区间、方案、PR凭证、排班、最新核准、考勤、补卡、请假及考勤方式。原员工和排班遍历顺序、证据JSON及SHA-256组成不变。日历及无Session策略按门店批量装载；不按员工/排班逐次查库。保留500员工/10000排班限额和跨月周加班上下文。
- 仓储新增批量投影接口，保留原single接口。`ApprovalSources.readAll` 的无Session策略只取生效assignment；不引入地点或住址变化对旧核准的依赖，session继续引用原定位策略快照。
- `PayrollTransactions` 管理独立只读REPEATABLE_READ捕获和独立READ_COMMITTED写事务。`PayrollBatchService` 的create、calculate、confirm外层挂起调用方事务；纯计算结束后，提交事务获取批次锁、核验版本和完整工资权限、获取原租户来源排他锁，重新批量采集并核验完整指纹后保存。原来源写入共享锁保留。
- 输入编辑复用已捕获的员工投影，避免无计算结果时再次读取全公司来源。detail/list保留REQUIRED，付款事务内的新登记可见。
- `PayrollComputeExecutor` 最多4个工作线程、2个在途计算批次、有限队列。有序chunk结果由调用线程按原员工顺序汇总，继续BigDecimal及原公司SDL舍入。worker不访问JDBC/权限上下文。失败/中断取消并排空任务再释放容量。
- 容量满使用`WORKFORCE_PAYROLL_BUSY`/503，中英文提示可重试或恢复本次操作；无成功回执或工资写入。既有命令重放、状态版本、异常阻断、本人审批及历史冻结规则不变。确认不覆盖原calculatedAt和工资快照。

## 并发安全

计算期间补卡、输入编辑可提交；恢复计算后，来源变化409或批次版本冲突409拒绝旧结果。提交再次鉴权，计算中失去权限不写入。创建计算失败不遗留草稿；审计失败与工资写入同事务回滚。来源锁仍覆盖最终权威读取至审计及命令提交，不能把它描述为无锁写入。

## 性能验证方法

测试为`WorkforcePayrollPerformancePostgresTest`，显式属性`rpb.payroll.performance=true`启用。同一独立UUID PostgreSQL schema、固定2026-03-02时钟、2026-02工资月；100/300/500员工各20个工作日，共2000/6000/10000排班。混合时薪、日薪、自动月薪，含固定收入、CPF和SDL，真实考勤及审批来源。最终夹具启用生产使用的WorkModeResolver/Clock构造路径，每员工有长期field/personal策略；session保留合法的legacy null历史policy。打卡探针也使用实际七参ClockService。种数据不计时。

测试专用Legacy来源、计算器和批次编排保留本次修改前代码，用完整快照`equals`比较（包括来源指纹、明细、扣缴、汇总、问题顺序、计算时间）。新旧均使用同一计数DataSource，旧服务通过事务代理运行，不以每SQL新连接夸大基线。

每规模每实现1次预热、3次自然计时交替执行，报告原始样本及中位数，不把3个样本称为P95。SQL计数为JDBC execute类调用；来源排他锁持有时间从获取锁SQL返回至事务afterCompletion，锁获取耗时包含数据库往返。总耗时包含JSON序列化、持久化、审计及回执。

另设不属于二月来源的三月员工，暂停计算并执行真实ClockService打卡，通过pg_locks验证旧实现等待来源锁，新实现在释放计算前完成打卡。人为暂停不纳入自然性能数字。

原始证据目录：`target/workforce-payroll-perf-20261030/`，包含基线源码哈希、独立validation.pom.xml、测试日志、surefire报告及metrics.json。只使用target/local-postgres-current.txt的端口，自建并清理测试schema，未应用迁移到现有业务schema。

## 自然性能结果

最终`metrics.json`状态complete，三个规模均通过完整工资快照相等及旧阻塞/新释放打卡的断言。以下为每实现3次计时的中位数，单位毫秒（四舍五入）：

| 员工 / 排班 | 总耗时 原→新 | SQL执行次数 原→新 | 来源排他锁持有 原→新 | 纯计算 原→新 |
|---|---:|---:|---:|---:|
| 100 / 2000 | 1723→646 | 20629→69 | 1571→233 | 4.79→12.01 |
| 300 / 6000 | 7552→3212 | 61529→69 | 7239→1542 | 11.33→21.74 |
| 500 / 10000 | 5867→1462 | 102529→69 | 5662→758 | 19.85→10.05 |

自然总耗时减少约63%/57%/75%，来源排他锁持有减少约85%/79%/87%。查询数不再随员工/排班数线性增长；此数据固定门店数，更多门店会增加按门店批量读取。首档公司两个门店，后两档公司各一个门店，旧查询数包含该差异。300人采样比500人更慢，说明本机计时波动明显，不能按规模外推生产速度；全部原始样本保留，不挑选最快样本替代中位数。

主要收益来自批量取数；100/300人纯计算由于线程调度开销和本机噪声未更快，不能把整体收益归因于并行算术。受控并行保证资源上限及确定性，500人该次采样纯计算较快。最终重验仍需来源锁，500人本地中位数约758ms，不宣称零等待。

第一轮性能夹具使用超过40字符的探针员工编号，被现有数据库约束拒绝；仅缩短测试编号后完整重跑，未放宽业务约束。首次不完整结果另存metrics-first-partial.json；兼容构造路径的中间完整结果另存metrics-legacy-mode-path.json。以上只引用启用实际策略模块的最终metrics.json complete结果。

## 自然并发打卡观测

最终`natural-concurrency.json`启用实际策略模块及七参ClockService，三个规模各一次。计算器入口仅通知主线程，随后立即正常计算；没有人为暂停或等待打卡完成。完整工资快照均相等，真实打卡均持久化成功。

| 员工数 | 打卡总耗时 原→新（ms） | 共享来源锁获取 原→新（ms） |
|---|---:|---:|
| 100 | 166.80→94.58 | 80.81→0.54 |
| 300 | 283.67→77.14 | 203.41→0.73 |
| 500 | 367.00→70.43 | 289.48→0.36 |

总耗时包含ClockService事务提交、不含线程池排队；锁获取指标含JDBC往返，不能当作纯数据库server wait。新实现300人观测中，writer实际启动时纯计算已结束，处于工资操作后续提交窗口；100/500人与纯计算有实际重叠。每档仅一次调度观测，不据此承诺稳定延迟或任何请求都不等待。另三档人工暂停探针提供确定性断言：旧打卡在pg_locks等待来源锁，新打卡在计算释放前完成；这些人工暂停耗时不混入自然性能表。

## 审查及验证边界

独立静态审查覆盖事务、来源顺序、权限、回执和执行器，未发现已确认新增缺陷。新增并发、计算器、batch策略及API/前端busy回归与实现同时编写；后续统一运行结果附下。

相关后端广泛回归共326项，首次325通过，唯一失败为权限撤销测试在`when(mock(any()))`重设时触发旧Answer的null参数；改用`doThrow(...).when(...)`避免执行旧回调，仅修正测试夹具。其余工资、付款、工时核准、考勤、方式/隐私、长期生成和扣缴回归均通过。该权限方法最终单独重跑，不无依据重复整套回归。测试数含父类继承重复，不等于独立业务场景数。

最终verify-final.ps1退出0：修正权限方法、正式自然并发、正式性能三项全部通过；合并未变化代码状态下的既有回归证据，final-result.json共327项、0失败。新旧完整快照、来源变化拒绝、月度输入冲突拒绝、提交权限重验、创建失败回滚、批次容量/任务取消及顺序均有测试证据。没有未解决的本阶段行为失败。

复杂来源等价PG测试3项通过，每次对完整Capture record断言相等，覆盖多门店、取消排班、补卡pending/rejected/approved、带薪/无薪假、calendar、PR凭证变动/删除、非SGD门店、跨月周加班、manual/attendance/fixed及有resolver的历史policy和无Session住址变动。

前端相关26项中24通过、2项为此前记录的工资单基线失败（introduced=[]）；新busy提示及长期方案/工资分项聚焦11项全部通过。`npm run build`（vue-tsc及Vite）通过。未重跑全项目后端或全前端751项；本阶段前端变动仅busy错误映射/文案。

本地模块测试的AppGate为既有允许fixture，权限拒绝另有测试；不是生产容量、真实设备定位或高峰持续压测验收。保留原租户来源锁，最终重验阶段仍会短暂阻塞该租户来源写入；不宣称完全消除等待。无数据库迁移或外部API路径变化。

主要验证命令（D:\RPB）：

```powershell
$tests=[IO.File]::ReadAllText('target/workforce-payroll-perf-20261030/regression-selectors.txt')+',WorkforcePayrollNaturalConcurrencyPostgresTest#naturalClockConcurrencyOldAndNew'
mvn -q -f target/workforce-payroll-perf-20261030/validation.pom.xml "-Dtest=$tests" '-Drpb.payroll.performance=true' test
# 上一轮唯一Mock夹具失败在修正后，通过以下精准复测完成闭环。
./target/workforce-payroll-perf-20261030/verify-final.ps1
node --import ./scripts/tests/workforce-register.mjs --test --test-concurrency=4 src/test/frontend/payroll-recurring-ui.test.mjs src/test/frontend/payroll-breakdown-ui.test.mjs scripts/tests/workforce-phase4.test.mjs
npm run build
```

## 回退与范围

本阶段未发布，无运行时回退操作。若未来需发布后回退，可恢复本阶段源码基线并构建兼容第二阶段的包，无本阶段数据迁移需要回滚；不得删除既有V104、来源证据或历史工资。无需改变预约、排队、现场接待、座位或清洁流程。
