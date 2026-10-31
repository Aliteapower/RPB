# 合同、假期与折现 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan inline, task by task. No per-task agent dispatch.

**Goal:** 实现可维护官方政策引用、合同工时、额度账本和年假折现，复用现有审批/核工时/计薪并保持历史冻结。
**Architecture:** workforce/compliance owns policy versions/contracts; leave/account owns append-only balances and reservations; leave/encashment owns approved pay sources. Existing leave and payroll services call small transaction-safe adapters. Vue consumes typed APIs and shared layout.
**Tech Stack:** Java21/Spring/JDBC/PostgreSQL/Flyway; Vue3/TypeScript.
**Spec:** docs/development/WORKFORCE_LEAVE_CONTRACT_COMPLIANCE_SPEC_20261031.md

## Global Constraints

- 不改旧迁移，不回填旧工资，现有工作树与上一批发布制品保留。
- 数据按tenant/company/employee隔离，沿用workforce门店App Gate和写防护。
- 官方规则存服务端版本及来源，不在页面硬编码JSON；未知资料阻断，不默认归零。
- 公共布局/普通保存区；每步清楚下一操作，审批金额权限独立。
- 本地数据库只用target/local-postgres-current.txt，合成数据验收，不生产发布本批。

## Review Focus

- 混合月/日/时薪、兼职比较工时不全与政策生效变更。
- 自然年切换服务年晋档、年结重试及占用/过期并存。
- 分组/单条审批、取消/重试/并发不重复扣减。
- 折现source跨普通/分段/补发唯一消费、失败回滚与旧快照不变。
- 无薪假/带薪假/休息/加班口径不重复计付，工资权限不得被员工管理替代。

## Tasks / 完成契约

- [x] Task 1 政策模型、纯计算器、版本化来源及合同：先写policy tests并看失败，再实现compliance/{ComplianceDtos,ComplianceRules,LeaveEntitlement,WorkHoursPolicy}及V116新增表、Repository/Service/API，Postgres验证版本/权限/幂等。
- [x] Task 2 假期账户：leave/account/{LeaveAccountDtos,LeaveAccountRepository,LeaveAccountService,LeaveAccountBridge}；扩展现有LeaveService及批量/分组审批事务调用。先测试预占、批准、取消、并发、年结，再实现。旧类型无绑定兼容、不改历史。
- [x] Task 3 折现及计薪：leave/encashment单据、金额核准、唯一source消费；现有payroll wage events/年度政策及普通/分段/补发适配器。先测试合法资格/基数/CPF/重复消费/确认回滚/冻结再实现。
- [x] Task 4 超时待核实及合同加班：核工时来源/fingerprint/决定小模块；扩展现有ApprovalSources/Service及PayCalculator只走新政策已采用员工。测试跨店/跨月/兼职双阈值/旧算法兼容/事实保留。
- [x] Task 5 引导式页面与设置路由：政策来源版本、合同/额度、年结、折现任务组件；复用现有LeaveForm、EmployeeForm、ApprovalDetail、工资总表。真实挂载测试、vue-tsc与Vite通过。
- [ ] Task 6 合成验收与交付：独立算术基线、真实接口完整链路、历史快照哈希不变、桌面/手机检查、code-review/API/database/TDD复核、更新规格和release note。证据不足不声称通过，不部署新批次。

每任务完成后记录代码/命令/结果到target/leave-contract-compliance-20261031/progress.md，任务未完成不得标完成。用户未要求当前新功能提前提交/生产发布，不以提交作为测试门槛。

## 2026-10-31 最终状态

Task 1–5 已实现并有纯计算、PostgreSQL 集成、Vue 挂载与类型构建证据；最终精确方法重跑记录见 review／release note。Task 6 的业务计算、真实 HTTP 接口与历史快照检查已执行，但桌面／手机视觉验收因 Computer Use 无法可靠识别 Windows 网址被平台停止，故整项仍不勾选，不声称所有验收完成。审查发现的失效预占、跨月周工时、同年换雇主建账已修复。用户允许开发旧雇主测试账清理，本批未进行批量删除，也不扩展其兼容入口。未提交或生产发布本批。
