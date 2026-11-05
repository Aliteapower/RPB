# 第三阶段实施计划

**Goal:** 请假与日历形成可追溯核准工时，按公司跨店汇总。
**Architecture:** Spring/JDBC独立leave/calendar/approval，Vue独立路由和局部样式，复用既有权限/AppGate。
**Spec:** docs/superpowers/specs/2026-10-28-workforce-phase-three-design.md
**API:** docs/api/2026-10-28-workforce-phase-three.md

## 全局约束
D:\RPB现有未提交阶段1/2为基线；不提交/推送/部署，不改钱包；不另建登录。测试严格使用本workspace pointer PG新schema。授权方案转写不再请求审批。
## Review Focus
- 新建重试不能被后续类型变更破坏；审批并发不可丢版本。
- 已批准请假撤销必须双人处理；本人审批绝不放行。
- 补卡/排班/日历变化后旧核准不可继续计入。
- 跨店支援归属发薪公司snapshot，受限门店不能被公司查询绕过。
- 整班请假无打卡可核准，部分无打卡不可，休息不得重复抵扣。

### Task 1: 合约和数据库
- [x] 核对阶段1/2源码，落盘设计/API/计划。
- [x] V090四表与约束，读取pointer在隔离schema跑migration。
### Task 2: 请假与日历
- [x] 先补WorkforceLeavePostgresTest场景，观察缺实现失败。
- [x] 实现leave/calendar DTO repository service；排班used检查包括有效请假。
- [x] 真实PG验证自审/范围/版本/重叠/销假/快照/审计。
### Task 3: 核准与汇总
- [x] 先写ApprovalCalculatorTest，验证整班/部分/窗口/比例/重叠/缺卡。
- [x] approval DTO/calculator/source reader/repository/service/summary；同锁防并发、fingerprint与历史。
- [x] WorkforceApprovalPostgresTest验证过期/重放/审计/跨店公司/拒绝泄露。
### Task 4: HTTP和UI
- [x] 独立WorkforceTimekeepingController，精确security路径；HTTP错误及同源测试。
- [x] 独立pages和phaseThreeText/types，阶段3导航；双语紧凑表单。
- [x] 前端真实组件测试、构建；已有workforce/权限回归。
### Task 5: 集成验收及记录
- [x] 本地真实API/浏览器验证；独立只读代码审查并修复必要问题。
- [x] 更新实施记录、release note、主设计状态，只报告真实通过和剩余边界。

## 执行记录
- 2026-10-28：用户已直接授权继续第三阶段；主代理负责数据/核准/API，独立任务按明确合约分配，避免共享源码写冲突。所有产物留本地。

- 验证闭环：相关后端179项覆盖全部通过（177项综合回归通过，2项测试夹具修正后定向重跑通过；未改生产源码）；前端48项、39组真实API、构建、浏览器和独立审查问题修复完成。证据见阶段三实施记录。
