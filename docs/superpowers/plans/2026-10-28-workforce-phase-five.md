# Workforce 第五阶段实施计划

> Use superpowers:executing-plans to implement task-by-task; user continuation authorizes the existing phase scope without another approval gate.

**Goal:** 公司/集团汇总、最小字段CSV导出、人工公司汇总试运行对账。
**Architecture:** 独立reporting读模块，复用PayrollAccess/批次快照；无新迁移，不改工资/付款命令。
**Tech Stack:** Java/Spring/JDBC/PostgreSQL、Vue3/TS、BigDecimal、现有Node组件测试。
**Spec:** docs/superpowers/specs/2026-10-28-workforce-phase-five-design.md

## Global Constraints
原D:\RPB工作区保留未提交前三/四阶段；不提交/推送/部署、不新增依赖。只用pointer PG新schema，不重写旧fixture。报表明确所选公司，不隐去无权公司，不混合冻结/预估。

## Review Focus
完整所选公司权限和快照支援店；SDL公司取整；跨公司员工去重；付款冲销/状态变化令导出失效；CSV公式及私人字段泄漏。测试按以下任务覆盖。

### Task 1: 后端报告与安全导出
- [x] PayrollReportPostgresTest、PayrollCsvTest先RED（缺实现），WorkforceReportApiTest补HTTP安全合约覆盖。
- [x] 新reporting DTO/Repository/Service/Values/Csv，独立controller和精确local security GET路径。
- [x] 验证冻结/预估分离、缺批次、作废、scope/tenant/AppGate、去重、冲销/失效/隐私。
### Task 2: 试运行对账
- [x] PG差额、零值、非法精度、过期和非冻结测试RED。
- [x] 独立PayrollReconciliationService、PUT接口精确allowlist，逐项差额、不写业务数据。
- [x] 验证重复只读计算、来源变化、工资/付款记录不变。
### Task 3: UI
- [x] phase5实组件测试先RED；管理入口/选择变更/失效请求/本地化/对账结果。
- [x] 独立报告与对账页面、types/text/UI辅助、下载fetch及blob释放、工资首页入口和路由。
- [x] 前端测试、build、回归。
### Task 4: 验收与交付
- [x] 独立本地fixture、API/浏览器链路与窄屏检查。
- [x] 只读独立审查、必要问题复现修复与定向回归。
- [x] 实施记录、release note和阶段状态；明确仅本地试运行及未覆盖外部申报。

## Ledger
设计沿用用户已批准的第五阶段；架构review技能文件在docs/skills及技能目录不存在，按现有模块/项目code-review规则核查职责；无迁移故不触发数据库迁移流程。当前分支codex/paynow-payment-product-line-staging，必须复用未提交前三/四阶段，不复制到缺失依赖的干净worktree。

Task 1/2/3/4: complete。综合后端286项执行、前端71项、真实API111组、构建和浏览器验收通过；无提交/推送/部署。独立审查连续来源指纹问题已PG RED→GREEN，草稿输入失效也纳入回归；界面语言切换完成提示问题已实组件RED→GREEN。详细日志和范围见实施记录。
