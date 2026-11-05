# Workforce 第二阶段 Implementation Plan

> 本会话逐项直接执行；用户已授权方案落盘后实现，不新增审批节点，不提交/推送/部署。继续D:\RPB已有第一阶段，不清理已有工作。

**Goal:** 交付班次、周排班、本人打卡、休息计算、出勤及可追溯补卡闭环。
**Architecture:** scheduling与attendance独立服务/仓储，AttendanceCalculator纯领域计算；复用现有WorkforceAccess、员工分配、审计、Auth及AppGate。前端独立任务路由，紧凑表单。
**Tech Stack:** Java21/SpringBoot/PostgreSQL/Vue3/TypeScript。
**Spec:** docs/superpowers/specs/2026-10-28-workforce-phase-two-design.md。

## Global Constraints
不部署、不改旧产品默认权限、不回滚已有修改；数据库验证必须读取target/local-postgres-current.txt并使用独立测试schema。无工资/请假/工时核准算法。客户端不决定打卡时间，原事件不覆盖。

## Review Focus
跨店并发及重叠；历史快照/归属不漂移；补卡本人审批和版本冲突；网络重试双写；前端切店旧响应/本店权限隔离。

## Task 1：合同、领域和迁移
- [x] API/数据库合同落盘，明确DTO、错误、幂等及时间边界。
- [x] AttendanceCalculatorTest先失败，覆盖自动/实际/带薪/跨夜/异常。
- [x] 实现AttendanceCalculator；V089约束、复合FK和索引。

## Task 2：排班
- [x] ShiftService/Repository、ScheduleService/Repository、ScheduleAccess独立职责。
- [x] 真实PG测试班次版本与快照、员工级排班互斥、复制原子回滚、取消和权限。
- [x] 与第一阶段员工修改共享锁，防止进行中出勤被更改归属。

## Task 3：考勤与补卡
- [x] ClockService/AttendanceRepository/QueryService和CorrectionService，原始事件与有效projection分开。
- [x] 自己操作、在职分配、状态机、命令重试、原店活动状态、31天查询与最小数据。
- [x] 补卡创建/撤回/审核与版本、跨店重叠/本人审批/审计回滚真实PG测试。
- [x] Controller沿用同源保护与错误映射，本地安全仅新增精确GET/PUT端点。

## Task 4：前端
- [x] 新建attendance/scheduling独立类型/文案/API/页面，管理导航分组不堆表单。
- [x] 班次列表与表单、周排班与独立排班表单、今日打卡、出勤列表/详情、补卡申请/审核。
- [x] 幂等未确定命令恢复测试，关键组件交互、切店隔离与构建。

## Task 5：验证、审查和记录
- [x] 新模块/阶段一/权限/AppGate针对性Java、真实PG、前端测试。
- [x] 实际API与浏览器闭环，中英文390px/桌面检查。
- [x] 独立只读代码审查，必要问题修复并复测；API/数据库/UI评审写入实施记录。
- [x] 更新实施记录和release note，保留本地未提交状态。
