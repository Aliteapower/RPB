# 产品线接入修复验证记录

项目 `D:\RPB`；2026-10-22；实现契约见 `docs/api/2026-10-22-product-line-access-contract.md`。本次保留原需求文档及用户已有文件，不提交、不推送、不发布。

# Troubleshooting Report

## Symptom

后台拒绝已实现的产品入口，运行时入口权限分散在产品分支中，聚合授权掩盖单店到期，营业记录仍可能显示与公共资料不一致的旧品牌。

## Evidence

- 修复前 `PlatformProductLineServiceTest` 的独立路由创建和改名保留入口两项触发 `REQUEST_INVALID`。
- 修复前共享 Logo 清除测试读到 `legacy`；原品牌 JSON 中仍保留旧 Logo 和英文名。
- 接线但尚未启用单店判断时，`StoreSubscriptionAppGateTest` 的到期、恰好到期、缺失/停用明细和替换 actor 租户测试共四项失败，证明既有判断不足。

## Root Cause

入口权限分支与前后端路由白名单各自维护；App Gate 只检查租户有效期和门店操作开关；品牌读取把私有 JSON 当作公共资料的备用来源。

## Affected Files

`AppGateService`、受控产品入口注册表、`PlatformProductLineService`、门店授权只读仓储、`LedgerBrandingService`、产品入口选项与中英文错误提示、对应测试。无数据库结构及现有计费写入流程变化。

## Fix Plan

共享受控入口定义；App Gate 中叠加门店商业有效期判断并用于菜单；通过既有公共资料服务读取品牌，不再用旧私有字段恢复当前展示。实现已完成。

## Verification

18 类相关测试共 102 项通过；首轮扩大回归的响应字段断言已修正，复测受影响的 5 项数据库用例通过。前端构建通过。详见下方测试清单和 release note。

## Remaining Risk

未做全仓测试和浏览器验收。未来生效与历史商业模型转换不在本次范围；旧品牌仍须显式接管，详见变更说明。

# TDD Review Report

| Scenario | Test Exists | Result | Notes |
|---|---|---|---|
| 独立路由创建/改名及非法路由 | 是 | 通过 | `PlatformProductLineServiceTest`，先红后绿 |
| 注册表权限、安全默认及前后端路由一致 | 是 | 通过 | `ProductLineEntryRegistryTest`、`StoreSubscriptionAppGateTest` |
| 到期、恰好到期、未到期、缺失/暂停/取消/过期明细 | 是 | 通过 | `StoreSubscriptionAppGateTest`，先红后绿 |
| A 店到期、B 店仍可用，实际单店续费恢复且 B 明细不变 | 是 | 通过 | `StoreProductAccessPostgresTest`，真实数据库及 HTTP 拦截器 |
| 跨租户、非有效门店与新增未购买门店 | 是 | 通过 | PostgreSQL 作用域验证及 actor 租户校验 |
| 提前续费不截断当前可用期、永久授权兼容 | 是 | 通过 | PostgreSQL 与 App Gate 测试 |
| 公共资料修改/清除、旧数据保留与媒体越权 | 是 | 通过 | `LedgerBrandingServiceTest`、PostgreSQL、`LedgerApiTest` |
| 幂等、报价、续费与订阅接口回归 | 是 | 通过 | 复用现有计费测试，未改变计费命令 |

## Missing Tests

未做浏览器交互验收，不将构建或源文件检查描述为浏览器证据。

## Required Next Tests

当前授权范围的必要自动化验证已完成；发布前应在待发布前后端版本上执行浏览器验收及环境验证，本次不发布。

# API Review Report

## Endpoint

现有平台产品目录创建/更新接口、App Gate 保护的门店接口及可见产品列表；无新增外部 endpoint 或 DTO 字段。

## Contract Check

受控入口扩展与新增拒绝码写入专门契约；默认入口值仍为站内路由字符串。

## Permission Check

平台目录权限、产品动作权限及门店授权不变；任意数据库 JSON 不能注册入口权限，actor 租户不能被请求替换。

## Error Mapping

到期使用 403 `STORE_APP_EXPIRED`、`appgate.store_app_expired`；保持 `success/error` 外层结构。缺明细或停用使用已有 `STORE_APP_NOT_ENABLED`。前端有中英文提示。

## Replay / Idempotency

本次新增只读判断，不改变购买、续费等命令的幂等契约；实际续费恢复通过现有事务验证。

## Compatibility

历史纯租户授权兼容；提前续费不因下一周期开始时间误停。未拓展本地安全 allowlist。

## Missing Items

无本次范围内未解决的接口契约项。

# Database Review Report

## Migration Scope

无 migration。只读仓储查询既有订阅、门店明细和门店表。

## Table Check / Constraint Check / Index Check

门店明细关联同时包含订阅 ID、租户 ID、产品及门店；保留 `scope_type='store'` 与有效门店条件。复用订阅租户/产品唯一键、明细订阅/门店唯一索引和租户/门店/产品索引，不新增写入路径。菜单只做一次批量查询。

## Tenant Isolation

PostgreSQL 用例覆盖 foreign tenant/store、缺少明细、无效门店及统一资料作用域。验证 schema 为本次随机新建测试空间，不使用既有业务 schema。

## Risk / Recommendation

保持旧授权兼容，不进行收费状态回填。已有按店计费数据在新版本请求时即时受单店到期约束，符合用户授权。

# Code Review Report

## Scope

对上述改动检查模块依赖、租户/门店范围、权限、历史兼容、事务、接口错误与 Vue 消费。App Gate 只读投影不依赖计费应用模块，相关边界测试通过；控制器无新增 Repository 依赖。产品专有业务、账号授权写入及计费事务保持原逻辑。

## P0 - Must Fix

未发现未解决的 P0。

## P1 - Should Fix

本次三个接入缺口及私有品牌回退已修复并覆盖回归。

## P2 - Improvement

未来生效语义、历史纯租户收费转换及通用平台英文资料能力另行设计，不阻塞本次修复。

## Test Coverage

| 测试类 | 数量 |
|---|---:|
| StoreProductAccessPostgresTest | 5 |
| StoreSubscriptionAppGateTest | 6 |
| ProductLineEntryRegistryTest | 2 |
| PlatformProductLineServiceTest | 9 |
| PlatformProductLineControllerTest | 6 |
| ProductSubscriptionServiceTest | 12 |
| PlatformTenantProductSubscriptionControllerTest | 3 |
| SubscriptionQuoteServiceTest | 4 |
| BillingPeriodCalculatorTest | 3 |
| AppGateServiceTest | 19 |
| AppGateEnabledEntitlementExpiryTest | 1 |
| AppGateCommandServiceTest | 8 |
| AppGateGuardIntegrationTest | 4 |
| PlatformBillingBoundaryValidationTest | 1 |
| PlatformProductLineBillingUiImplementationValidationTest | 1 |
| LedgerBrandingServiceTest | 6 |
| LedgerApiTest | 3 |
| LedgerPostgresTest | 9 |
| 合计 | 102 |

扩大回归命令为 `mvn -q "-Dtest=<上表逗号分隔的测试类名>" test`；测试断言修正后受影响复测命令为 `mvn -q "-Dtest=StoreProductAccessPostgresTest" test`。前端 `npm run build` 退出码 0，最终数据库复测退出码 0。结果保存在 `target/surefire-reports`；其余 97 项复用同一生产代码及环境下的已核实结果。

## Final Decision

APPROVE_WITH_COMMENTS：本地实现完成，保留改动，未提交/推送/发布。浏览器与生产验证未执行，不将其表述为已通过。
