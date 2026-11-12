# 员工考勤与薪资第一阶段 Implementation Plan

> 执行方式：本会话直接逐项实施，遵守用户已有实施授权；文档转写不新增审批。保持 D:\RPB 已有未提交文件，不提交、不推送、不部署。

**Goal:** 交付可本地验证的独立产品基础：员工、发薪公司、门店分配、管理员与仅打卡账号。
**Architecture:** 新增 workforce 模块，复用 Auth、Store、operating_entities、AppGate 和审计。Controller 只调用应用层；公司配置、员工、授权、账号 provisioning 各自职责独立；前端独立任务页面。
**Tech Stack:** Java 21 / Spring Boot / PostgreSQL / Vue 3 / TypeScript。
**Spec:** docs/superpowers/specs/2026-10-28-workforce-design.md

## Global Constraints
不部署；不修改旧产品行为；不回滚用户改动；数据库仅使用当前 pointer 实例。工资与打卡算法不在本阶段。紧凑 UI、中英文、加载/空/错误/拒绝状态齐全。

## Review Focus
店长兼 tenant_admin 不可扩大权限；新账号不获旧业务默认权限；工作店与账号访问分离；员工跨店部分可见不泄露/覆盖他店信息；重试/版本/审计事务不可产生重复账号或覆盖变更。

## Task 1: 合同与迁移
- [x] 写 API、数据库与权限合同，定义 /api/v1/stores/{storeId}/workforce。
- [x] 先写真实 PostgreSQL/应用行为测试，观察未实现时失败。
- [x] V088 注册产品、发薪配置、员工/公司历史/门店分配历史、管理员授权；复合 FK、唯一、版本、状态与索引。
- [x] 在 pointer 数据库独立测试 schema 运行迁移及约束验证。

## Task 2: 后端模块
- [x] WorkforceAccess 复用 CurrentActor 与 AppGate；显式委派门店范围，总部身份严格检查。
- [x] WorkforceCompanyService / WorkforceEmployeeService / WorkforceAdminService，独立 Repository、审计及 Auth provisioning。
- [x] API 显式 DTO、错误映射、写请求保护、no-store、版本冲突与重试。
- [x] 测试公司范围、部分可见、账号权限、历史与审计回滚。

## Task 3: 前端与入口
- [x] 独立 workforce routes/types/api/text/styles、Layout、员工列表/基本表单/门店表单、公司配置、授权、本人资料。
- [x] 接入共享产品清单与中英文产品名称，不改旧产品入口逻辑。
- [x] Node 测试验证路由/状态切换隔离和关键授权交互；npm run build。

## Task 4: 验证与交付
- [x] 跑针对性 Java、真实 PG、既有权限/AppGate 回归及前端构建。
- [x] 代码/API/数据库/UI 检查；本地实际页面验证可行时执行，未验证项如实记录。
- [x] 更新实施记录、验证结果和 release note；保留未提交状态，不部署。
