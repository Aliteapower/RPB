# 薪资第四阶段：增量、差异与批量工作台

项目D:\RPB，2026-10-30。仅本地实现/验证，不提交、推送、部署或替换运行服务。基于前三阶段继续，不改月薪、自由员工、CPF/CDAC/SDL公式及历史工资事实。

## 模块职责

| 模块 | 职责 |
|---|---|
| PayrollIncremental | 稳定依赖键、员工结果复用、完整回退；无SQL |
| PayrollCalculationService.aggregate | 当前员工顺序的全量金额/问题汇总和公司SDL舍入 |
| PayrollComputation / Repository | 独立缓存和比较权限证据，不进入原PayrollSnapshot或旧回执DTO |
| PayrollBatchService | 原捕获/计算/重验事务，缓存与工资/审计/回执原子提交，确认全量校验 |
| PayrollChanges / ProgressService | signed BigDecimal差值、双基线及待重算投影，历史范围重新鉴权 |
| PayrollBulkService / ProgressRepository | 每批独立提交、命令绑定、确定性子命令、跨实例互斥及恢复 |
| PayrollChangesPanel / ChangeComparison | 双语变化和差额、缺基线/旧预览提示、成本舍入说明 |
| PayrollBulkOperations / payrollBulkProgress | 1..20批次选择、明确确认、逐项结果及未知操作恢复 |

## 增量边界与结果安全

完整快照取数、500员工/10000排班限额、REPEATABLE_READ捕获、事务外计算、最终批次版本/权限/完整来源指纹核对及来源共享/排他锁保留。没有选择性SQL缓存，防止漏掉新增、删除或跨月工时。

全局键包括引擎版本、月份、公司信息及完整扣缴政策；员工键序列化完整CapturedLine，覆盖个人资料/方案、生成后输入、跨月周加班来源、问题及特殊日状态。版本/政策/公司变化或缓存缺失、不兼容时全量计算；其他只重新执行变化员工公式。当前顺序汇总所有员工，重做公司SDL舍入，不能增量相加旧公司舍入值。

原calculate使用增量；新增calculate_full显式全量；确认总是全量复算，对比完整Snapshot（只忽略calculatedAt），且不覆盖原快照。即使缓存内容被错误改写，错误预览也不能通过确认；全量重算可恢复。缓存优化不是确认依据。将来改公式、汇总或依赖序列化必须更新ENGINE_KEY。

月度输入仍清除对外旧预览，内部保留最后计算基线。冻结/发布/作废状态不可通过增量改工资。确认、发布和付款继续使用原快照。

## 差异和权限

GET changes独立投影：本月上次计算差额、紧邻上月已确认/已发布工资差额、待重算人员、最后复用/重算人数。缺少任何对比方明确unavailable；新增/移除员工单独标注，不能把未计算工资当0。

原始十进制字符串显示，不在JS里计算工资。公司成本差额使用公司SDL舍入；个人成本差额含原始SDL精度，属于个人估算，行差额不保证加总等于公司差额，界面明确提示。

已移除员工的旧差异仍可能包含敏感金额，所以cache额外保存比较涉及的旧、新来源门店；读取时与当前、历史快照一并授权。成功bulk重放也重放原子命令来检查原回执历史来源门店，不只检查当前工资。角色/范围变化后不能借旧结果读取薪资。冻结月份GET不写任何工资或缓存。

sourceChanged与pending人数独立：全局来源指纹变化但计薪依赖未变时，可能pending=0；仍提示旧预览不是实时结果，须增量刷新来源快照。

## 批量与恢复

选1..20个批次，支持增量重算、全量重算和确认；每批独立事务，部分失败明确返回，不拆分确认员工，不批量发布/付款/作废。确认继承月底、无异常、来源一致及本人禁止规则。

父命令绑定tenant/actor/store/完整payload。子命令由tenant+父commandId+batchId确定，未知异常不完成父回执，恢复已完成子动作不重复计算或审计。成功父回执重试重新鉴权，失败行不含越权金额。

同一父命令使用专用非池化PostgreSQL session advisory lock，防止两个实例同时执行造成结果竞态；不持有工资/来源锁或长事务。实例最多2个在途批量命令，锁忙或容量满503。专用连接从标准DataSourceProperties连接同数据库，仅做锁；不长期占用业务Hikari连接，避免小池互等。finally解锁关闭，解锁失败终止连接。

前端未知网络/5xx保留当前挂载内的原UUID、版本及payload，禁用其他操作并提供恢复；4xx确定失败解除等待。结果保留在刷新列表之后。与原单次工资操作一致，未增加跨页面/浏览器重启的客户端恢复持久化；不声称已实现关闭浏览器后自动恢复。

## 数据与实际验证方法

V105新增workforce_pay_computations和workforce_pay_bulk_operations，两表有租户复合FK、JSON类型约束和时间字段；扩充原workforce_pay_commands动作约束允许calculate_full，不改旧行/JSON。将来部署需要迁移，当前仅在自建UUID schema验证；未应用到现有运行schema。

只使用target/local-postgres-current.txt指向的D:\RPB运行时。隔离构建及证据在target/workforce-payroll-incremental-20261030。源码基线备份不作为Git提交，既有无关材料不回滚。

规模夹具100/300/500员工×20个真实核准工作日，含长期field策略、时薪/日薪/自动月薪、CPF及SDL。每档初次、无变化、一人、两人及全局公司变化，逐次与原Legacy计算器、当前全量和显式calculate_full的完整快照严格相等；计数真实公式调用行数，另记录端到端SQL/锁/耗时。固定时钟比较包含计算时间，常规确认只忽略墙钟时间差。

纯计时不含来源读取；端到端仍包含全量取数、键计算、缓存持久化及事务。每场景本机一次观测，顺序运行存在JIT/缓存偏向，不作为稳定加速、生产SLA或持续高峰压测。减少公式调用不等于整个请求必然更快。

## 调试及审查记录

- 初始RED为新增增量/持久化接口缺失，新增测试与实现同时编写；未删除既有工作满足流程。
- 首轮真实PG暴露calculate_full未加入旧命令动作check；在V105明确扩展后精准复测迁移和全量动作，不将错误掩盖为原calculate。
- 批量中断测试的spy(Spring代理)触发Mockito unwrap错误，改普通mock委托真实服务，仅调整夹具。审计测试另明确StoreScope重载类型以消除编译歧义。
- 独立审查核对增量、事务、历史范围、批量恢复及前端，未发现已确认必修项；DDL新动作约束另行复核保留原四种动作。

## 回退

当前未发布，无运行时回退。未来若回退到第三阶段包，保留两张新证据表及扩展动作约束，不删除历史工资/回执；旧计算仍可全量运行。原来的发布、付款、工时事实不依赖增量缓存。不可退回删除既有V104定位事实。

最终测试结果与规模原始证据见后续验证段。

## 最终验证结果

相关后端100项、0失败（final-result.json合并未变化代码状态的首轮通过证据和精准复测，含继承重复，不当作100个独立场景）。回归命令最终退出0：7项新PG核心、15组规模验证、批量中断恢复、V104→V105实际Flyway升级/校验/重入、原薪资API、第三阶段并发4项、长期生成10项及四类考勤/隐私15项通过。首轮其他通过证据包括增量11、并行2、差异3、bulk4、历史权限3、新API4、新本地路径安全1、批量PG其余场景及继承的原工资/付款/确认流程。

前端41项中39通过，2项为原已记录工资单基线失败，introduced=[]；新增工作台15项全部通过。npm run build的vue-tsc及Vite通过；没有真实浏览器视觉验收或真实手机验收。保留既有Mockito/CDS、测试安全上下文等警告，不宣称整个项目全绿。

未应用V105至现有运行schema、未替换后端/前端服务、未提交/推送/部署。未来发布需要独立授权及迁移准备，新增动作约束的DDL需按实际数据量安排执行，不在本次执行生产迁移。

验证命令（D:\RPB）：

```powershell
# 精确方法列表避免无意义地重复继承测试；含规模测试的两个开关。
$tests=[IO.File]::ReadAllText('target/workforce-payroll-incremental-20261030/regression-selectors.txt')
mvn -q -f target/workforce-payroll-incremental-20261030/validation.pom.xml "-Dtest=$tests" '-Drpb.payroll.performance=true' '-Drpb.payroll.incremental.scale=true' test
node --import ./scripts/tests/workforce-register.mjs --test --test-concurrency=4 src/test/frontend/payroll-progress-ui.test.mjs src/test/frontend/payroll-recurring-ui.test.mjs src/test/frontend/payroll-breakdown-ui.test.mjs scripts/tests/workforce-phase4.test.mjs
npm run build
```

## 规模验证结果

`scales.json`状态complete：100/300/500人分别2000/6000/10000条排班，初次、无变化、一人、两人、全局变化，共15组全部通过原Legacy计算器/当前全量/强制calculate_full完整Snapshot相等断言。

| 员工 | 无变化重算/复用 | 单人重算/复用 | 两人重算/复用 | 全局变化重算 |
|---|---:|---:|---:|---:|
| 100 | 0/100 | 1/99 | 2/98 | 100 |
| 300 | 0/300 | 1/299 | 2/298 | 300 |
| 500 | 0/500 | 1/499 | 2/498 | 500 |

端到端本机单次观测（毫秒，四舍五入；每格为增量/全量）：

| 员工 | 无变化 | 单人变化 | 两人变化 | 全局变化 |
|---|---:|---:|---:|---:|
| 100 | 630/770 | 686/685 | 563/574 | 608/543 |
| 300 | 3149/3000 | 3205/3442 | 1028/1194 | 1295/1198 |
| 500 | 1673/1690 | 1468/1645 | 1503/1769 | 1934/2054 |

本阶段两种模式均72次JDBC执行，包含第三阶段完整来源捕获和新增缓存取存；数量不随员工/排班线性增长。部分观测增量更慢，说明取数、依赖哈希、缓存JSON与持久化仍占主要成本；不能宣称重算人数降为1就有数百倍请求加速。500人单人/两人变化该次节约约177/266ms，仍只作观测，不外推稳定收益。全部纯计算/端到端/锁原始样本均在scales.json，未挑选最快样本替代结果。
