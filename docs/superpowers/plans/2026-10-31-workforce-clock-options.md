# 打卡多选与外勤记录实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement task-by-task.

**Goal:** 管理员授权可并存方式，员工按实际场景打卡，外勤名称与实际时间可追溯，完成验证及提交部署。

**Architecture:** 复用workmode/positions/attendance及版本、快照、审计；保持单Session和工资内核。后端先固定契约，UI消费同一契约；父级负责跨模块验收与发布。

**Tech Stack:** Java21/Spring Boot、PostgreSQL、Vue3/TypeScript及现有Node组件测试。

**Spec:** docs/superpowers/specs/2026-10-31-workforce-clock-options-design.md

## Global Constraints

- D:/RPB当前树增量，保留钱包8文件及所有已有材料；不删除/回滚。代码及迁移完成后用户已授权提交、推送、部署。
- 本地DB严格读target/local-postgres-current.txt，当前61644/postgres，postgres空密码；随机测试schema，不能操作daily_ledger或其他fixture。
- V001–V112不改，新增V113；不回写旧策略/班次/工资/付款，不自动给员工开权限或处理地址申请。
- 旧nullable JSON兼容、真实隐私隔离、free全额工资、日期约束及跨公司工资归属保留。
- 使用现有外勤说明字段存地点/客户名称；不新增GPS必填或外勤逐次审批，不把地点名当定位证据。

## Review Focus

- 未授权方式、缺定位、free/legacy混用、伪造继承许可必须后端拒绝。
- 开始班次后的模式切换只改变事件，原IN许可快照保留；不能双算或绕过休息/跨日及核准源约束。
- options含居家坐标时，即使主mode非home也须隐私投影；不能泄露给仅考勤/工资权限。
- no-op需在版本校验后判断，回执精确幂等且不增长历史；旧客户端不能隐式扩大/缩小多选。
- 过期申请不能靠改日期绕过，提示原因；未发生的审批不能显示批准，无真实业务数据自动处理。

### Task 1：后端许可、事件快照与兼容

**Files:** workforce/workmode、positions、attendance相关DTO/服务/仓储、必要approval隐私投影；V113；对应Java tests；docs/api/2026-10-31-workforce-clock-options.md。

**Interfaces:** 先写精确新增字段/错误码/回执/旧客户端语义并通知父级和UI；所有旧构造器及旧JSON重放保留。Controller权限沿用，不新增宽泛匿名路径。

- [ ] RED：单选不能office IN/field OUT，未授权拒绝，实际名称/时间、休息/跨日、原IN快照不改；旧命令重放。
- [ ] RED：无变化保存/继承不增长，版本过期/竞争仍409；兼容旧客户；缺地址/过期/重复批准原因明确。
- [ ] 最小实现迁移与模块化许可解析、命令回执、事件投影和隐私保护，不重复新公式。
- [ ] PG/接口/原核准及工资free回归，保存真实红绿、退出/XML/hash；冻结供独立任务审查。

### Task 2：管理员与员工UI

**Files:** workforce/workMode/types/positions、WorkModeFields、WorkModePage、PositionList、ClockPage、AttendanceDetail及相关组件测试。

**Interfaces:** Task1精确API为准。Task2不得改Java/SQL/API；契约稳定后实施，可先读相关现状。Task1不改Vue/TS。

- [ ] RED：允许方式多选、原设置兼容、无变化提示、待办/历史及过期原因；不假装新批准已生效。
- [ ] RED：员工选择实际方式、名称输入、办公室IN外勤OUT、切换班次清理状态及不确定请求锁定/原重试。
- [ ] RED：事件时间/实际方式/名称展示、无权限/旧事件字段缺失、空/加载/失败恢复；窄屏不溢出。
- [ ] 最小UI实现并相关Node/类型/Vite验证；冻结报告及hash，独立任务审查。

### Task 3：集成与本地验收

- [ ] 两任务独立审查后全范围集成审查，必要缺陷集中修复并定点复查。
- [ ] 使用拥有明确归属的本地合成副本实际API验证多选/同班次模式切换/名称时间/no-op/重试/隐私，保留旧工资付款指纹；尝试真实浏览器/窄屏，如连接受阻明确缺口。
- [ ] 更新MD/API/入口与兼容说明及release note，源码/制品冻结。

### Task 4：提交、推送、部署

- [ ] 精确白名单提交及干净归档构建，验证原钱包/私有材料hash；复用已有不变验证，必要受影响组合重验。
- [ ] 新鲜生产版本/磁盘/数据基线；V112→V113独立真实备份恢复演练，旧列指纹/旧快照/许可不自动扩张。
- [ ] 新停服备份后切换，核对前后端版本/资源/接口保护/服务日志、数据及相邻服务；发布记录提交推送。
