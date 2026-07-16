# Troubleshooting Report

## Symptom

- 本地指针 PostgreSQL 与隔离前后端均正常监听，但创建验证预约返回 403。
- 首次只读查询使用了不存在的 `reservations.customer_name` 字段。

## Evidence

- `target/local-postgres-current.txt` 指向 PostgreSQL 端口 61644，该端口处于监听状态。
- `information_schema.columns` 显示顾客姓名存于 `customers.display_name`，需通过 `customer_id` 关联。
- `tenants` 与 `stores` 查询结果均为 0 行。
- 后端日志显示 App Gate 拒绝审计写入违反 `app_gate_audit_logs_tenant_id_fkey`，目标本地验证租户不存在。

## Root Cause

- 本地运行指针仍有效，但其数据库缺少本地验证租户、门店和 App Gate 基础数据，属于空数据运行时。
- 查询错误来自把 API 投影字段误当作 `reservations` 表字段，与产品代码无关。

## Affected Files

- 无业务文件受影响。
- 仅使用被 Git 忽略的隔离运行指针和临时本地验证数据。

## Fix Plan

- 从仓库现有集成测试夹具复用最小租户、门店、App Gate、员工账号、顾客和预约数据结构。
- 在指针数据库加入公网有备注、员工有备注、员工空备注三条验证预约。
- 使用真实预约卡片组件完成交互与响应式验证，验证后删除临时可视化夹具。

## Verification

- 本地数据库确认三条预约分别为 `public_booking` 有备注、`staff` 有备注、`staff` 空备注。
- 浏览器确认两条非空备注有入口且能展开/收起，空备注没有入口。
- 手机与平板三个视口均无水平溢出。

## Remaining Risk

- 当前本地数据库原始状态为空，不能代表完整业务演示环境；本次验证只覆盖预约备注披露组件及其响应式布局。
- 线上验证仍需在前端部署后使用真实租户员工会话执行。
