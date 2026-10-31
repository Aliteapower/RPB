# Code Review Report

## Scope
门店常用存放位置、可选仓位、历史名称冻结。API 合同见 docs/api/2026-10-26-storage-locations.md。

## P0 - Must Fix
无。

## P1 - Should Fix
无。

## P2 - Improvement
无必要的额外改动。

## Test Coverage
Controller 只调用身份与应用服务；所有位置查询、更新及审计带 tenant/store 范围。维护使用既有权限及 App Gate，并额外限制租户管理员；收存复用已有权限与事务。位置变更、收存通过门店锁串行化；命令沿用 StorageCommands 幂等。数据库唯一约束、范围复合外键与版本检查完整。客户端名称不可信，服务端重新解析并冻结。旧 JSON 可读且新增 null 字段不改变旧命令摘要。

Vue 组件分为选择和管理职责，复用收存表单；明确区分未初始化、默认选择及主动清空，卸载/切店晚响应不写入新界面。已有记录按原 location 展示。

## Final Decision
APPROVE。后续用户明确授权“提交部署”，已按生产检查发布 `5b8f7884`，记录见 STORAGE_LOCATIONS_PRODUCTION_RELEASE_20261026.md。

# TDD Review Report

| Scenario | Test Exists | Result | Notes |
|---|---|---|---|
| 默认读取无写入、新增与改名 | 是 | 通过 | PostgreSQL 和 Vue |
| 幂等重放、重复名称、旧版本 | 是 | 通过 | 真实 PostgreSQL |
| 管理员/员工、跨租户/店、Gate 拒绝 | 是 | 通过 | 新服务用例及既有 StorageAccessTest |
| API 身份、范围头、HTTP 错误 | 是 | 通过 | MVC/安全过滤器 |
| 选填、伪造名称、冻结历史、直接登记 | 是 | 通过 | 收存事务与快照 |
| 旧 JSON、旧幂等摘要形状 | 是 | 通过 | NON_NULL 与旧构造兼容 |
| 前端默认/清空/管理权限 | 是 | 通过 | 前端回归共 67 项 |

## Missing Tests
无阻断项；手机实机未覆盖。

## Required Next Tests
用户后续明确授权发布，V081、前后端版本、认证只读接口及公网资源均已验证；手机实机仍未覆盖。
