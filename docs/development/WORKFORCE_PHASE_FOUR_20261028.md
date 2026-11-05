# 第四阶段实施记录：公司工资批次与工资单

日期2026-10-28；项目D:\RPB。用户授权继续第四阶段；本轮仅本地实现、隔离验证，未提交、推送或部署。第一至第三阶段及原有钱包/其他工作区内容保留。

## 结果
已实现工资模板、个人生效薪资方案、月/日/时薪、同雇主跨店合同日/周加班、普通工资CPF/SDL、公司自然月批次、确认冻结、发布本人工资单、分次付款登记和一次等额冲销。12个独立紧凑双语页面、15条路由，操作按模板/方案/批次/行/工资单/付款分开。

范围严格为2026年、新加坡时区、SGD、私营部门普通工资OW。年度奖金/AWS等AW、跨期补缴、年终AW重算、复杂轮班平均及外籍劳工税自动计算尚未实现。公众假期/休息日待遇由管理员核对并通过普通工资项目体现，不宣称所有法定场景自动计算。月/日薪计薪日数须明确核对填写；同公司同月多段工资标准及SPR转换月非1号等不支持的情形阻断确认。

## 设计和接口
先落盘：
- docs/superpowers/specs/2026-10-28-workforce-phase-four-design.md
- docs/superpowers/plans/2026-10-28-workforce-phase-four.md
- docs/api/2026-10-28-workforce-phase-four.md

后端模块：
- statutory/StatutoryCalculator、StatutoryDtos：CPF/SDL纯规则。
- payroll/PayCalculator：薪资/合同OT纯计算。
- PayrollAccess：公司工资委派，独立于employeeManage。
- PayrollConfigurationRepository/Service：模板、个人生效方案及复制规则。
- PayrollSources：公司完整名册、当月全部排班、有效核准及跨月周上下文、来源指纹。
- PayrollCalculationService、PayrollValues：金额和行问题，不在前端计算薪资。
- PayrollRepository、PayrollBatchService、PayrollViews：批次、输入、命令重放、冻结和历史状态。
- PayrollPaymentService、PayslipService：付款流水、冲销、本人发布单及隐私显示DTO。
- WorkforcePayrollController：14条明确HTTP接口，继承身份/同源/no-store/错误协议；security只增加精确GET/PUT路径。

V091六张新表：workforce_pay_templates/profiles/batches/inputs/commands/payments。tenant/company/employee复合FK、同公司月非voided唯一、付款正金额/一次冲销约束；没有旧产品数据重写、依赖变化或默认工资授权。

## 计算和权限
金额跨API均为规范十进制字符串，后端BigDecimal；每个薪资项目至分，CPF总额整元HALF_UP、员工份额FLOOR、雇主为差额。SDL逐人保精度，公司合计后FLOOR至元。员工工资不扣雇主CPF/SDL/雇主费用。

CPF实现2026官方五表：完整年龄档、低工资50/500/750分段、OW月上限、SPR GG/FG/FF、生日及SPR周年次月升档。缺资料、未支持年份或PR部分转换月不默认为零。模板修改不改已保存个人方案；已确认批次引用的个人方案不可覆盖，需新建生效方案。

工资管理员必须总部companyAllowed或原workforce_administrators.payroll_company_id明确委派；employeeManage不授薪资。公司所属门店及当期/快照支援来源店逐店校验原账号scope及AppGate；拒绝越公司、越店、跨租户和平台管理员人事访问。员工只见本人published单；显示DTO不含生日/PR/其他员工/来源指纹/管理备注，付款备注与操作者也剥离。

月/日薪使用管理员核对的计薪天数，不能从小时猜月薪；时薪按普通核准工时+带薪请假。日/周OT取并集，同日跨店合并、带薪假不计OT，月初周前月上下文只消耗阈值不再支付。月未结束不能确认；未核准、过期核准、待审、缺方案、缺计薪日、特殊日未核对等均阻断。无排班时薪员工可以显式核对零工时并填写说明。

## 一致性和历史
workforce事实源写命令、薪资方案和公司发薪配置加入tenant级共享transaction advisory guard，工资calculate/confirm取同key排他锁；均先guard再原员工/日历锁，正常打卡之间共享并发。没有给旧产品表加锁；未做大规模负载测试。

计算读取完整名册和来源（最多500员工、10000来源），指纹包括公司配置、资格、方案、来源版本、输入及被消费的来源判定。确认时再读、再验当前问题，不能只检查旧计算结果。统一批次锁保护行输入、状态和付款余额；UUID+expectedVersion命令重放。

确认后金额、工资方案、CPF规则、来源引用冻结。后续来源变化提示核对，但不暗改已确认/已发布金额；差异须后续明示调整。未发布且无付款可作废，旧批次及命令保留；已发布不可覆盖。付款是记录，不调用银行；冲销另追加反向记录，不删除原付款。

## 验证结果
### 后端
最终完整命令：
```text
mvn -q -Dtest=StatutoryCalculatorTest,PayCalculatorTest,ApprovalCalculatorTest,AttendanceCalculatorTest,Workforce*Test,AuthPrincipalStorePermissionsTest,StaffStoreAccessPolicyTest,StaffPermissionsServiceTest,StaffPermissionsPostgresTest,StoreSubscriptionAppGateTest,StoreProductSettingsServiceTest,ProductLineEntryRegistryTest test
```
exit0；248项执行，0失败/0错误/0跳过（包含继承执行的既有场景）。其中纯薪资12、纯CPF/SDL10、配置PG20、工资PG24、新HTTP3；同时覆盖前三阶段、原权限和AppGate回归。日志target/workforce-phase4-final-tests.log，逐类明细target/workforce-phase-four-validation/backend-summary.json。

新增两项真实并发PG验证：
- correction写事务持有shared来源锁时，工资confirm在pg_locks中等待exclusive；源提交后旧指纹被拒，批次仍calculated。
- 两笔不同UUID、合计超过冻结净薪的付款并发，只有一笔成功，另一笔WORKFORCE_PAYROLL_OVERPAYMENT。

前期TDD和修复证据保留：缺计算模块/批次服务先编译RED；区域判定遗漏先真实PG复现可错误确认，再修指纹并重验；测试夹具与foundation同员工编号导致的初次冲突已改独立编号且清空setup审计调用。补卡拒绝后原核准仍因来源历史变化而过期，测试按真实流程先重新核准，没有降低生产校验。

### 前端
- 阶段2/3/4实际组件及纯规则测试48项（18+15+15）。
- 原workforce及staff-permissions/panel/store-assignment15项。
- 合计63项通过，最终npm run build exit0。
- 新增实组件RED→GREEN：时薪零来源核对、中文内置工资项、半日单位、现金可空参考号、扣项负号/雇主承担性质、本人确认、工资权限与网络重放。
- 薪资内容只在当前组件内存恢复同UUID，不写入sessionStorage；离开页面后应核对服务器状态/付款历史。

### 隔离真实接口
读取target/local-postgres-current.txt：port61644、runtimeWorktree D:\RPB。独立schema workforce_browser_b293644c61fc40a5b31e43f6e2c923b3；V001..V091迁移成功，没有向public或原业务schema应用迁移。

后端8094（pid28888）、loopback代理5194（pid15452），独立target/workforce-phase-four-package；原前三阶段后台未停。真实cookie/Auth/scope/AppGate接口验收：
```text
node scripts/validation/workforce-phase-four-api.mjs
```
83组通过，report target/workforce-phase-four-validation/api-result.json。Sep5班×8小时×20：工资800、员工CPF160、雇主CPF136、SDL2、净薪640、雇主成本938。包含普通店长拒薪资、工资委派、模板不漂移、来源失效/重新核准、冻结、员工只本人发布单、私密字段剥离、分次/幂等/超付/冲销一次、Oct日/月薪预计算及本月确认拒绝。

过去排班仅由本fixture SQL seed（产品API禁止倒排班）；后续补卡、核准、薪资、付款均真实API。fixture.json含测试会话，不打印或提交。

### 浏览器
本地UI实际操作已验证：新增时薪备用模板25并列表可见；管理端已发布批次800/640/136/2/938保持冻结；冲销一笔200后，现金空参考号登记200，原付款及反向记录保留；员工工资单仍显示已登记640、余额0，管理备注/生日不显示。

中文/英文390×844 CSS像素检查，document clientWidth=scrollWidth=375，无整页横向溢出，导航/项目表内部可滚动；临时视口已恢复。工资单内置项双语、时薪不堆无意义日数、扣项负号、雇主费用性质，SDL只去显示尾零不损失有效精度。实现打印样式及按钮，但未真实打印或做PDF文件验收。

截图位于target/workforce-phase-four-validation：batch-desktop.png、payslip-desktop.png、payslip-mobile-zh.png、payslip-mobile-en.png。

## 独立审查与闭环
独立只读审查提出两项必要问题，均修复：
1. 来源店币种/时区判定未进指纹：CapturedLine结果纳入hash，confirm重验当前计算问题；changedStoreRegionInvalidatesCalculatedPayroll真实RED→GREEN。
2. 时薪无来源不能勾核对：统一工时/计薪依据核对控件，实组件先RED后GREEN，并核对必填说明。
HTTP补审APPROVE，其余核心未发现明确必要问题；审查建议的两项并发已补真实PG通过。必要UI联调修复也有实组件覆盖，不以审查标签替代真实证据。

## 边界与后续
本阶段普通OW范围完成。年度奖金/AW、复杂政策、PR转换月分段工资、同公司同月多工资标准等必须先扩展规则与对账；不通过随意填OW津贴绕过。集团汇总、导出、试运行对账属于第五阶段，未自动开始。

未接银行、CPF或税务申报；付款记录不证明真实资金转移。未真实手机/微信、真实银行/CPF平台、真实打印或大规模负载验收，也未执行全项目所有测试。尚未生产迁移，未来真实业务数据不能通过删薪资表回退，应保留冻结历史并前向修复。暂不部署、不提交、不推送。

## 中断恢复核对（2026-10-28）

原任务最终以模型容量错误中断，但第四阶段代码、计划勾选、实施记录和发布说明均已落盘。已读取原主任务及计算、UI、配置、独立审查智能体结果，核对两项必要修复及并发回归确实存在；没有重复实现或启动第五阶段，未改业务代码及其他已有改动。

本次新验证：工资/CPF纯计算、工资配置PostgreSQL、工资批次PostgreSQL、工资HTTP合计69项通过，0失败/错误/跳过；阶段2/3/4组件48项及原workforce/员工权限15项共63项通过；`npm run build`、`git diff --check`通过。后端日志为 `target/workforce-phase4-resume-backend.log`，逐类结果为 `target/workforce-phase-four-validation/resume-backend-summary.json`；前端日志为 `target/workforce-phase4-resume-frontend.log`、`target/workforce-phase4-resume-permissions.log`、`target/workforce-phase4-resume-build.log`。

首轮数据库测试因指针端口61644拒绝连接而中止；按本项目重启指南启动指针所指原数据目录后重跑成功，没有切换数据库或重建集群。测试仍只在随机独立schema执行并清理自身schema。原248项综合回归、83组真实API及浏览器截图沿用前述既有证据，本次未重复这些验收；已查看保存的中文窄屏工资单截图。仍未提交、推送或部署。
