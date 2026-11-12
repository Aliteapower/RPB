# 每日营业记录 Implementation Plan

**Goal:** 将单店全部记账能力接入 RPB 多租户产品线，并迁入蜀香测试账本。

**Architecture:** dailyledger domain/application/persistence/api；Vue 独立 daily-ledger 模块。复用账号、集团门店、AppGate 与 platformbilling。

**Tech Stack:** Java 21, Spring Boot 3, PostgreSQL, Flyway, Vue 3, TypeScript。

**Spec:** ../specs/2026-10-22-daily-ledger-design.md

依用户已有实现授权，当前会话直接执行；不新增审批节点。

## 执行任务

- [x] 1. 只读导出原站点配置、日期、工作/提交记录与审计；检查无截断，保存来源证据。
- [x] 2. 先写金额、记录校验与快照测试，运行失败；实现 domain 规则并验证。
- [x] 3. 建表、产品价格与权限注册；实现 scoped JDBC、CAS、幂等与审计服务；补 PostgreSQL/服务/API 授权测试。
- [x] 4. 接入 AppGate、账号权限、员工首页及 Vue 路由；实现表单、自动保存、设置、历史报表/CSV/图表。
- [x] 5. 实现显式本地导入工具，复用 RPB 租户账号结构；拒绝覆盖，建立 sx 测试门店订阅并核对源/目标记录。
- [x] 6a. 41项Java回归、9项前端行为、构建、实际接口/完整数据核对、独立审查、发布说明。
- [x] 6b. 用户登录后完成桌面/手机浏览器验收、清空恢复、错误重试、跨餐段提交、提交快照、CSV和审计；修复语言浮层遮挡。

## 执行记录

实现与验证见 `docs/development/DAILY_LEDGER_LOCAL_VALIDATION.md`。

Ruling: 用指针数据库的独立schema验证并迁入sx，保留原public schema和旧工作区数据；原因是原指针归属旧public-booking工作区且没有当前账本结构；代价是本地重启必须指定schema，已写入指针和启动工具。

Ruling: 共用AppGate拒绝审计作最小修复；真实API发现错误门店请求在拒绝审计FK处返回500，影响新产品越权契约；保留请求门店ID，但仅给已授权门店使用store FK。

## 重点回归

最终班次不能增加抽款；前班修改必须让旧提交来源冲突。历史配置变化不能重算旧底钱。重试不能重复审计。切换门店或日期不能将未保存表单写到新范围。自动保存不能覆盖正在编辑的字段。金额不得信任客户端 totals。
