# 异常考勤与合同计发：本地实施验收

日期：2026-10-31，项目D:/RPB。状态：本地实施；没有提交、推送、生产发布或旧业务工资清理。

## 范围与结果

四种考勤方式独立于定位方式。正常事项形成系统有效来源；异常登记/审批与工资使用同一日来源。管理员按员工×日期查看月表，员工查看本人月历；合同计发及自由免考勤不伪造实打或核准工时。合同加班完全依有效条款，日周加班明细与工资共用DailyWorkAllocation。

新增V114策略、V115每日版本/区间例外、V116个人年度权益、V117系统判定审计。GET不写入；自动判定每60秒覆盖已采用策略员工本地当天至前7天。旧月份可显式刷新。经理专审、自审禁止、租户/门店/AppGate与工资独立权限继续校验。

年度假期通过明确授额、结转及调整形成余额，包含待审占用和批准使用；单位或净时间依据缺失为未知。自动按工龄计提、自动结转及跨店同名假别自动合并未实现。

## 数据与运行边界

验证前读取D:/RPB/target/local-postgres-current.txt：127.0.0.1:61644/postgres，postgres空密码。使用本任务创建的workforce_browser_e01d21e4a61645cea6b614638182c5d5隔离schema；后台8107、代理5207均只绑定loopback。未使用其他项目数据库，未改变真实订阅。

原四名负例员工资料/策略/排班/原始打卡/请假/区间例外在工资正例链路前后哈希一致。另建公司及门店，四名正例完成工资验证；无任何支付或银行动作。私有fixture含合成会话，仅保存在target，不作为文档附件或仓库提交。

## 已运行验证

后端分批验证复用未变更结果，不把重复/继承执行次数相加成唯一用例数：

- DailyWorkAllocation、ContractPayCalculator、PayrollGeneration、PayrollLineage、新版来源、逐日加班投影及工作方式适配相关单测通过。
- WorkforceAttendanceDailyPostgresTest：37项通过；WorkforceLeaveEntitlementPostgresTest：22项通过。精确安全白名单、策略日期/权限/快照/重放/并发及工资来源相关回归亦通过。
- 自动判定实际运行暴露audit_logs.source约束不接受scheduler。新增真实数据库审计写入回归，先复现约束失败，再改source=system、metadata.initiator=scheduler；同命令20项通过，其中PG15项，覆盖幂等、并发、审计/日版本同提交及失败回滚。命令：`mvn -Dtest=WorkforceAttendanceNormalEvaluationPostgresTest,AttendanceNormalEvaluationTest,AttendanceNormalEvaluationServiceTest test`。日志target/normal-evaluation-audit-{red,green}.log。
- 旧工资精确清理工具8项真实PG测试通过，包含未知依赖拒绝与保留对象不变；实际业务schema未清理。
- 前端相关广泛回归274项，272通过、2项失败。失败为未改动的原本人薪单列表断言未计入补发列表请求，以及原Profile JSON导入缺少Node import attribute。拥有文件git diff为空；保留失败证据，不扩大本次修改范围。日志target/attendance-ui-broad-final.log。
- `git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check`通过（保留原CRLF约定）；本次4份规格/计划/验收/发布说明相对链接检查通过。
- 本次新增月表/策略/例外/假期/合同组件测试及原规则模板回归通过。曾发现新策略组件覆盖原规则编辑器，已恢复原文件，新组件改名EmployeeAttendancePolicySettings.vue并通过回归。
- `npm run build`通过，含vue-tsc与Vite；日志target/attendance-frontend-final-build.log。后端`mvn -q -DskipTests package`退出0；日志target/attendance-final-package.log，最终运行证据见下节。

## 实际工资API链路

合成正例月份2026-09，经真实API完成官方法定模板→有效配置→刷新→月表→创建工资批次→测算→全量重算→确认→发布→4名员工独立会话查看本人薪单。全量重算金额/指纹一致，确认前后来源未变；发布后付款记录0。没有真实打款、银行或CPF申报验收。

| 方式 | 合成正例gross（SGD） |
| --- | ---: |
| 打卡核对 | 3580.00 |
| 默认正常、登记例外 | 2640.91 |
| 免打卡按合同计发 | 2780.42 |
| 自由免考勤 | 3000.00 |
| 合计 | 12001.33 |

SDL合计30.00；三个适用方式的日历加班各7200秒，与工资逐日合计一致。自由员工仍有待审信息性请假，但不阻断或减少其固定全额工资；合同正例包含按工作日折算、无薪假扣减和批准加班另计。本人薪单不含内部来源指纹、审查人、GPS或敏感内部快照。

完整链路绑定构建SHA256：1A84B6248B3C276E96A804D5259B8E26A62959B96109EDC899F6F8E51EB4A21B。此后只有自动审计来源修复；该结果不冒充修复后包重新执行的完整链路。可复核证据在target/workforce-exception-20261031-a8fbcfcb/financial-happy-{api-result,manifest}.json。

## 最终包及自动任务运行复核

最终后端包SHA256为`FE5769BD5348102EAFBBB8D43904E3AF908F7CA58CBB70CD36C32E71E3FCBDB0`。打包退出0；核实旧进程命令行、端口及任务归属后，仅重启本任务8107后端，原包/日志/manifest保留。新包启动15.879秒。

真实定时任务首轮已提交61条initiator_kind=system日版本及61条对应系统审计（source=system、actor_id为空、metadata.initiator=scheduler）；未出现审计约束或调度错误。新包只读回归确认：已发布批次仍sourceChanged=false，来源指纹及gross12001.33保持一致，四名员工本人薪单金额不变，付款0。没有对已发布批次重新计算或覆盖。

证据：target/workforce-exception-20261031-a8fbcfcb/final-runtime-readback.json及新backend.log。此前完整工资链路证据与本次定向运行复核分别绑定各自构建。原始负例数据保护哈希再次核验通过。

## 审查与修复

后端审查最初要求修复：实打与额外工作双计、未来加班提前生效、合同/自由遗漏原请假、节假日默认正常候选丢失、跨夜例外归属及原/新请假双向冲突。均增加或更新回归并定向复审。

工资/界面定向审查修复：休息日零来源错误算工作日、跨月周前序档案阈值校验时机、采用默认策略请求字段、历史来源店权限、自由及明确不扣假/包含加班合同不被无关请假阻断、合同说明误写为自由全额。精确清理工具审查未发现必要阻塞。

原工作区钱包、EmployeeStores、AGENTS.md及其他未跟踪资料保留；没有以清理旧工资为由删除整个schema、原始业务事实、共享审计或Flyway历史。

## 仍未验证或未执行

- 浏览器实际桌面DOM检查：管理员原四负例员工可见，同日多异常、缺卡、合同/自由不适用显示及显式刷新可用；导航和面板同x、同宽，桌面clientWidth=scrollWidth。截图多次CDP超时，设置viewport未改变实际innerWidth，IAB恢复同样超时/中断，未绕过工具限制。
- 尚无320/390px、中英文全流程及员工四类页面完整视觉验收；组件测试和构建不替代这些验收。无实体手机、GPS或打印验收。
- 旧线上/本地业务工资清理已获授权，但尚未实际执行。工具默认预览42张工资专用表，执行时锁定并拒绝未知依赖；保留员工、合同、打卡、排班、请假、法定费率及其他产品数据。不得将合成库清理测试表述为业务库已清空。
- 生产新版尚未发布；生产发布不由旧工资删除授权自动涵盖。未提交或推送。


## 后续执行更新

用户随后要求实际清理及修复2项基线。线上旧工资29行、本地9schema旧工资741行已清；保护数据不变，新版验收工资保留。前端相关回归现为275/275、构建通过，未发布代码。此前未执行和2项失败记录保留为历史证据，最新结果以[实际清理及修复记录](2026-10-31-workforce-payroll-actual-reset.md)为准；其中也记录生产迁移编号冲突这一后续发布前必修问题。
