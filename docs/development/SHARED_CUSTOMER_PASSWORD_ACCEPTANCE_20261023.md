# 租户共享顾客与手机号密码验收

日期：2026-10-23。项目：`D:\RPB`。范围：本地实现；未提交、推送或生产发布。

## 实现

1. `customer.application.CustomerDirectoryQuery` 提供租户统一有效顾客目录。钱包通过集成适配器使用该目录，移除认证账号筛选；后台、钱包和后续产品线复用 `customer_id`。
2. 正式顾客管理要求 E.164 手机号，数据库原有租户手机号唯一索引继续生效。临时到店／历史无号档案不重写。新开钱包需补号，历史账户继续可用。
3. `customerauth` 独立负责手机号密码登录、BCrypt 凭据、会话、登录限流和改密审计。初始密码为手机号后六位；成功初始化后不再动态回退，改号保留原密码。登录只查已有顾客，不自动注册。
4. 改密要求有效顾客会话和旧密码，新密码至少八字符、UTF-8 不超过 72 字节；撤销该顾客全部旧会话并签发当前新会话。审计不含密码／哈希。
5. 钱包与公开预约共用登录和改密组件。预约预填登录手机号，顾客改号由统一管理入口维护。me 可按门店租户过滤。旧邮箱／OAuth API 保留兼容。

## API Review Report

- Endpoint：新增 public customer-auth 的 password-login、password-change；me 增加可选 storeId。见 [API 契约](../api/2026-10-23-shared-customer-phone-auth.md)。
- Contract Check：请求不接受 tenantId、customerId；门店决定租户，改密身份来自 cookie；响应不返回密码或哈希。
- Permission Check：共享原顾客会话；钱包员工权限、门店访问与 App Gate 未改变；改密显式认证。
- Error Mapping：未知顾客／错误密码同为 401 invalid_credentials；限流 429；缺号新开户 409 CUSTOMER_PHONE_REQUIRED。
- Replay / Idempotency：登录每次签发新会话；改密成功撤销旧会话，客户端不自动重试。钱包原资金幂等不变。
- Compatibility：后台正式建档手机号由可选变必填，历史列表保留。principal.email 可空，增加 phoneE164；旧调用 me 不传门店仍兼容。
- Missing Items：本轮范围无。

## Database Review Report

- Migration Scope：V058，认证邮箱 nullable、密码凭据、持久化限流。无顾客／资金重写。
- Constraint Check：凭据以 tenant/customer 为主键，具顾客复合外键与 BCrypt 格式检查；限流以 tenant/bucket 为主键。
- Index Check：复用原租户手机号唯一索引；凭据／限流主键覆盖读写，限流更新时间索引支持维护。
- Tenant Isolation：按租户查顾客和凭据，登录串行锁定同租户原顾客；不按邮箱／姓名跨档案归并。
- Risk：默认密码可推知；按用户明确要求保留，界面提示改密。历史无号档案补号遇冲突需人工核实。
- Recommendation：通过；发布需应用 V058，回退保留凭据和数据。

## TDD Review Report

新增密码服务测试先运行红灯（缺少实现），再实现、修复和验证。真实行为验证包含：

| 场景 | 证据 | 结果 |
|---|---|---|
| 初始密码、改密、禁止回退／空密码／BCrypt 截断 | CustomerPasswordAuthServiceTest | 6 通过 |
| 手机号必填、重复冲突、显式 ID 不得抢占手机号 | CustomerManagementApplicationServiceTest | 8 通过 |
| PostgreSQL 并发初始化、同号异租户、限流、会话撤销、原钱包、me 作用域、审计 | SharedCustomerPasswordPostgresTest | 10 通过 |
| 原邮箱认证及有效档案检查 | CustomerAuthApplicationServiceTest | 6 通过 |
| 预约创建与公开预约业务 | ReservationCreateApplicationServiceTest / PublicBookingApplicationServiceTest | 34 通过 |
| 钱包资金、历史无号账户、新开户校验 | WalletPostgresTest | 47 通过 |
| 钱包身份、请求、商业权限、本地安全入口 | WalletApiIdentityTest / WalletRequestSecurityTest / WalletCommercialGateTest / WalletLocalRuntimeSecurityTest | 11 通过 |
| 顾客与公开预约 UI 契约 | PublicBookingUiValidationTest / TenantAdminCustomerManagementUiValidationTest | 11 通过 |
| 真实 Vue 登录／改密交互、员工共享顾客选择、状态隔离 | customer-password.test.mjs / wallet-workspace.test.mjs | 11 通过 |
| 钱包导航、扫码扣款、丢响应重试 | wallet-navigation.test.mjs / wallet-scan-charge.test.mjs / wallet-retry.test.mjs | 11 通过 |

后端合计 133 项，0 失败、0 错误、0 跳过；前端合计 22 项通过。`npm run build` 的类型检查和 Vite 构建通过；`git diff --check` 无内容格式错误（仓库已有 CRLF 转换提示）。

本地数据库使用本轮读取的 `target/local-postgres-current.txt`，端口 61644、数据库 postgres、用户名 postgres、空密码。真实 PostgreSQL 测试仅创建各自随机 schema，结束删除自己创建的 schema；不更改 public 或既有验证 schema。

证据：`target/shared-customer-validation/results.json`、`target/shared-customer-verified.log`、`target/shared-customer-password-audit-verified.log`、`target/shared-customer-regression-final.log`、`target/shared-customer-ui-verified.log`、`target/shared-customer-wallet-front.log`、`target/shared-customer-build-verified.log`。

首次集成启动曾遇 Windows JDK HTTP 客户端回环连接失败。仅在新增集成测试中选择 simple HTTP factory，密码认证仍通过真实 Spring 事务、会话和 PostgreSQL 验证。前端初次回归遗漏 Node 22 的 TypeScript transform 参数，修正命令后全部通过；不修改运行依赖。

## Code Review Report

- Scope：本轮顾客、认证、钱包适配和共用 UI；不扩展资金规则。
- P0 / P1：未发现待修复问题。
- Module Boundaries：顾客目录／档案归 customer，凭据／登录归 customerauth，wallet 仅消费应用接口；新产品线不得再建顾客表、复制档案或用是否登录筛选目录。
- Transaction / Security：凭据和会话原子更新；失败计数持久化；登录／改密按顾客串行；改密审计不含敏感值。
- Test Coverage：如上。复用未变资金与权限代码已经核实的结果，不重复全量测试。
- Final Decision：APPROVE。

## 剩余范围

未执行全仓所有测试或生产浏览器验收；新增前端行为通过真实 Vue 组件交互测试及构建验证。未添加短信，未自动合并历史顾客；未提交或发布。原有未跟踪资料及验收文档保留。
