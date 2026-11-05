# 第一阶段长期薪资与自动生成执行计划

目标：按已批准设计在D:\RPB本地实现并验证，不提交、推送、部署。
设计依据：[完整设计](../specs/2026-10-30-workforce-recurring-payroll-design.md)。

## 约束与执行决定

- 在当前codex/paynow-payment-product-line-staging分支原地增量修改：本功能依赖大量现有未跟踪workforce源码，空worktree不包含这些资料；不搬移/提交/回滚用户材料。
- 基线源码与哈希备份：target/payroll-recurring-20261030/baseline及baseline-hashes.json。
- 每步先测试再实现；仅运行自建PG测试schema；测试构建隔离到target/payroll-recurring-20261030/build。
- API沿用现有路径、授权、CSRF和幂等；通过可选JSON字段扩展，旧字段不变，不新增数据库迁移。
- 第二至第四阶段不实施；不把计薪规则转换为考勤端定位功能。

## Task 1：长期规则与纯计算

- [x] 编写并运行失败测试：生成策略校验、每周工作日、自由月薪限制、自动单位及固定项目全额/折算。
- [x] PayrollDtos.Rule新增可选generation；Item新增可选proration。构造器保留旧签名，字段级NON_NULL。
- [x] 新建PayrollGeneration，负责mode/工作日校验及generate(rule,input,sources,month,employmentFrom,employmentTo)，返回生成输入和异常；不访问仓储。
- [x] PayCalculator保留旧公式，明确处理固定项目策略；自动生成的单位允许精确小数日。
- [x] PayrollConfigurationService保存规则时调用策略校验。

## Task 2：来源捕获和月度生成

- [x] 编写PG集成失败测试：连续两月沿用、自由入离职/无薪假全额、旧人工模式不变、缺来源阻断、月中变更阻断、确认冻结与权限。
- [x] PayrollSources选择长期有效方案；自由模式不依赖考勤，生成输入时保留当月调整/版本/CDAC，不复制上月。
- [x] PayrollRepository提供公司月雇佣有效区间，保证自由员工整月无雇佣不产生工资。
- [x] PayrollCalculationService使用生成输入；PayrollBatchService在新批次含自动方案时生成calculated预览，重试不重复计算/写入。
- [x] 普通自动模式保留出勤异常/特殊日及审批阻断，人工勾选不能绕过。

## Task 3：前端易用性

- [x] 编写前端失败测试：策略选择及保存、固定项目策略、自动月度输入只编辑变动、自由说明、前端校验。
- [x] 新建PayGenerationFields.vue及双语文案/辅助函数，供原模板与个人方案编辑复用；不堆入页面。
- [x] PayrollLineForm显示自动生成依据及只读日数，隐藏重复核定输入；保留调整、特殊日及CDAC复核。
- [x] 新建/列表提示长期有效与自动生成，旧方案正常编辑。

## Task 4：验证与审查

- [x] 后端相关PG/API/纯计算回归；前端workforce测试及npm run build。
- [x] 自审权限/幂等/快照兼容/源变更/冻结及同月调薪；修复受影响范围。
- [x] 保存API契约、开发验证报告、release note，逐项填写命令和结果，区分已验证与未验证。

## 审查重点

旧JSON及来源指纹兼容；自由雇佣边界/公司调动；月中方案变化；新规则不能悄悄覆盖旧手工数据；自动计算不能绕过原始缺卡；一次性调整不串月；生成结果与保存版本区分；历史工资不回写。

## 完成记录

最终后端聚焦59项、前端聚焦23项通过；原相关PG/API回归73项通过结果复用。广泛前端回归10项原基线失败已在源码副本复现，无新增失败。完整结果见docs/development/WORKFORCE_RECURRING_PAYROLL_20261030.md。未提交、推送、部署或替换运行中服务。
