# RPB 产品线落地方法手册

本文复盘 PayNow `payment` 产品线在 2026-08-05 至 2026-08-06 的设计、实现、部署和故障修复过程，沉淀为下一条产品线可复用的方法。

PayNow 是一个完整样例：它经历了正式设计、产品线 seed、App Gate 权限、原生后端模块、租户后台设置页、员工收款工作台、顾客展示页、Flyway 迁移、生产部署，以及部署后的既有账号权限补齐。

## 核心原则

新增产品线优先做成 RPB 原生能力。除非有明确、可控、短期不可替代的集成理由，不要把外部项目作为 sidecar 运行后再由 RPB 调用。

PayNow 中，`D:\payment_runtime` 的价值是业务流程证据：快速收款、金额键盘、展示编号、收款码展示、120 秒自动清除、顾客屏展示数量等。它不应该成为运行时依赖。这样可以避免两个用户体系、两个权限体系、两个数据库、两个租户边界。

可以从旧项目或外部项目提取：

- 业务流程和用户操作习惯。
- 领域词汇和状态流转。
- 输入校验规则。
- 高频页面交互。
- 已经被真实使用验证过的边界场景。

不要直接复制：

- 登录和用户体系。
- 权限模型。
- 数据库和租户边界。
- 部署形态。
- 与 RPB 产品线边界冲突的模块结构。

## 标准落地流程

1. 先写正式设计文档。
2. 定义产品线边界和 App Gate contract。
3. 设计数据模型和 Flyway 迁移。
4. 按 OOD 边界实现后端模块。
5. 定义可复用 API contract 和稳定错误码。
6. 实现租户后台、员工工作台、必要的顾客展示页。
7. 同时处理既有账号权限 backfill 和未来账号默认权限。
8. 补齐 migration、service、controller、UI contract、权限拒绝场景测试。
9. 按产品线重构租户后台菜单、员工入口和国际化字典入口。
10. 从干净 worktree 的精确 commit 构建和部署。
11. 写发布说明，记录提交、备份、Flyway、烟测、风险和回滚方式。

## 正式设计文档清单

每条新产品线开工前，先完成这些内容：

- 产品线边界：`app_key`、显示名、入口路由、目标用户、租户/门店范围、计费关系。
- 模块 OOD：`api`、`application`、`domain`、`persistence`、`provider`、前端路由。
- API contract：接口、DTO、幂等、`sourceType`、`sourceId`、状态流转、错误码。
- 数据模型：表、`tenant_id`、`store_id`、唯一约束、索引、审计字段、过期字段、迁移顺序。
- 权限模型：平台、租户管理员、门店经理、员工、只读、审核、支持角色。
- Phase 计划：先最小可用，再做跨产品线复用、报表、对账、自动化。
- 风险矩阵：sidecar、权限遗漏、迁移回滚、生产写入烟测、跨设备状态、外部服务假设。
- 测试矩阵：migration、service、controller、UI source、权限拒绝、幂等、部署烟测。

## 产品线边界

产品线既是商业单元，也是运行时单元。至少明确：

- `platform_apps.app_key`：稳定产品 key，例如 PayNow 使用 `payment`。
- `platform_apps.entry_route`：产品入口，例如 `/stores/:storeId/payments`。
- `entry_permissions`：菜单和入口可见所需权限。
- 产品线价格 seed：订阅或附加产品计费元数据。
- 门店 app 激活：门店必须启用该产品线，员工才能执行运行时动作。
- 被其他产品线调用时，通过稳定 adapter/API 复用，不在 POS、预约、账单里复制一套逻辑。

PayNow 踩坑：菜单能看到，不代表 API 权限完整。员工进入了收款页，但 `POST /payments/intents` 仍可因为缺 `payment.intent.create` 返回 `403`。

## 后端 OOD 模式

建议按原生模块组织：

- `api`：Controller、request/response DTO、HTTP 错误映射。
- `application`：用例服务、事务边界、幂等处理。
- `domain`：状态、命令、结果、领域校验、值对象。
- `persistence`：Repository、SQL/JDBC/JPA 映射。
- `provider`：外部支付、码生成、渠道适配等 provider 边界。

依赖方向保持清晰：

- Controller 调 application service。
- Application service 调 repository port 和 provider interface。
- Provider 不知道 HTTP、App Gate、前端页面。
- POS、预约、账单等调用方只传 `sourceType` 和 `sourceId`，不直接生成 provider payload。

PayNow 参考对象：

- `PaymentMethodProfile`：租户/门店支付方式配置。
- `PaymentIntent`：一次收款请求和生命周期。
- `PaymentSession`：收款码/展示会话。
- `PaymentQrPayload`：provider-neutral QR envelope。
- `PayNowQrPayloadBuilder`：PayNow SGQR payload 生成边界。

## API Contract 模式

API 要为不同产品线复用预留边界：

- 优先门店 scoped：`/api/v1/stores/{storeId}/...`。
- 使用显式 DTO，不暴露数据库行。
- `sourceType` 表示调用来源，例如 `quick_pay`、`pos_order`、`reservation_deposit`。
- `sourceId` 保存调用方业务实体 ID。
- create 接口使用幂等 key。
- 状态生命周期稳定，不随页面实现变化。
- 错误码稳定，前端可以明确映射。

建议错误码：

- `PAYMENT_PROFILE_NOT_FOUND`：未配置可用 profile。
- `PAYMENT_PROFILE_DISABLED`：profile 已停用。
- `PAYMENT_INVALID_REQUEST`：金额、来源、字段不合法。
- `PAYMENT_IDEMPOTENCY_CONFLICT`：幂等 key 冲突。
- `PERMISSION_DENIED`：保留 App Gate 权限拒绝语义，并在 UI 显示清楚。

PayNow 踩坑：前端必须同时理解产品线错误和 App Gate envelope。否则生产上只显示“创建失败”，排查会慢很多。

## 数据模型模式

SaaS 产品线的运营数据必须明确归属：

- 所有产品线数据有 `tenant_id`。
- 门店业务有 `store_id`。
- 高频写入表要有合理索引。
- 关键行有状态字段和审计字段。
- 短生命周期页面有 `expires_at`。
- create/replay 行为有幂等约束。
- provider 特有字段可以放 JSON extension，但核心查询字段不要只放 JSON。

迁移规则：

- 产品线 seed、价格 seed、权限 seed、入口 seed 可以同迁移提交，但要确保部署目标需要它们一起出现。
- 既有数据 backfill 必须幂等。
- migration test 要覆盖新库和既有数据。
- 部署前检查 JAR 内确实包含新 migration。

PayNow 踩坑：V047 建好了产品线和 API，但既有账号没有自动拥有新权限，后续才通过 V048/V049 补齐。下一条线要一开始就把“既有账号”和“未来账号”作为同一个权限需求处理。

## 权限清单

每条产品线都要检查六个权限面：

- 平台角色：是否能管理产品线和计费。
- 租户管理员：是否能配置产品线。
- 门店经理：是否能管理门店运营。
- 员工：是否能执行日常操作。
- 既有账号：是否需要 Flyway backfill。
- 未来账号：创建账号/员工时默认权限是否同步更新。

PayNow 最终权限拆分：

- 租户管理员：`payment.settings.manage`、`payment.intent.view`、`payment.intent.create`、`payment.verification.review`。
- 门店员工/门店经理：`payment.intent.view`、`payment.intent.create`。
- 普通员工不授予设置和审核权限。

PayNow 事故复盘：

- V048 只补了既有租户管理员。
- 既有员工能进入收款页，但创建收款码时缺 `payment.intent.create`。
- V049 补了既有 active `store_staff` 和 `store_manager`。
- 同时更新未来员工默认权限，避免新建员工再次踩坑。

## 前端模式

一条产品线通常至少有三个页面面：

- 租户/管理员设置页：基础配置、启停、默认值。
- 员工工作台：高频业务操作。
- 顾客/展示页：当业务有面向顾客的输出时提供独立显示页。

PayNow 路由参考：

- 租户后台设置：`/stores/:storeId/admin/payment/settings`。
- 员工快速收款：`/stores/:storeId/payments`。
- 顾客展示页：`/stores/:storeId/payments/present/:terminalCode`。

前端检查点：

- 路由在正确 app shell 内。
- 导航入口受 App Gate 控制。
- API client 映射产品线错误和 App Gate 权限错误。
- 覆盖空状态、未配置、已停用、加载中、成功、失败。
- 高频页面要按员工操作效率设计。
- UI/source 测试确认使用 RPB 原生 API 和共享组件。

PayNow 可复用经验：`payment_runtime` 最值得借鉴的是员工交互模型，包括计算器输入、预设金额、可编辑展示数量、顾客屏、120 秒清除。

后台配置和员工运行时配置要拆开 API。PayNow 后续优化里，员工端需要读取 quick-pay prefix、daily start number、preset amounts，但不能直接复用租户后台 profile API，因为后台 API 带有手机号/UEN、商户名称和 `payment.settings.manage` 权限。正确做法是提供窄接口，例如 `/payments/intents/terminal-config`，只暴露终端运行所需字段，并用 `payment.intent.create` 保护。

## 租户后台菜单产品线化

租户后台菜单不要按历史功能平铺，也不要把所有基础设置混在同一组里。新增产品线后，后台菜单应先按产品线分大类，再在产品线内分子类。

推荐结构：

- 租户资料、员工管理、顾客管理等租户级基础能力可以保留为通用菜单。
- 预约排队叫号作为一个产品线大类，内部放桌号管理、基础设置、订位分享、公网预约、叫号屏配置、预约排队国际化字典等子类。
- PayNow 作为一个产品线大类，内部放基础设置、Quick Payment Records、PayNow 国际化字典等子类。
- 以后新增 POS、会员、库存、账单、预约增强等产品线，也按同样的大类和子类方式挂载。

显示规则：

- 菜单可见性以租户订阅、门店 app 激活、App Gate 权限共同决定。
- 一个租户只开通 PayNow 时，租户后台只显示 PayNow 产品线相关菜单，不显示预约排队叫号菜单。
- 一个租户只开通预约排队叫号时，不显示 PayNow 菜单。
- 同一租户的不同门店可以因为门店 app 激活状态不同而看到不同产品线入口。
- 菜单显隐必须由持久化产品线/门店 app 状态驱动，不能写死某个租户或门店。

子类设计：

- 产品线设置页只放该产品线自己的基础配置。
- 产品线报表页只查该产品线自己的运营数据。
- 产品线国际化入口只管理该产品线自己的业务文案。
- 产品线运行时入口，例如员工收款、POS 下单、排队叫号，应从员工工作台或底部导航进入，不和后台设置入口混在一起。

PayNow 参考：

- 后台大类：`payment` / `收款 / PayNow`。
- 后台子类：
  - `/stores/:storeId/admin/payment/settings`
  - `/stores/:storeId/admin/payment/records`
  - `/stores/:storeId/admin/payment/i18n-catalog`
- 员工运行时：
  - `/stores/:storeId/payments`
  - `/stores/:storeId/payments/present/:terminalCode`
  - `/stores/:storeId/payments/display/:sessionNo`

预约排队叫号参考：

- 后台大类：`reservation_queue` / `预约排队叫号`。
- 后台子类：
  - `/stores/:storeId/admin/tables`
  - `/stores/:storeId/admin/settings`
  - `/stores/:storeId/admin/share-template`
  - `/stores/:storeId/admin/public-booking`
  - `/stores/:storeId/admin/call-screen`
  - `/stores/:storeId/admin/reservation-queue/i18n-catalog`

国际化菜单边界：

- 每个产品线应有自己的国际化菜单入口。
- 不要共用一个“国际化字典”菜单再让用户自己分辨 namespace。
- 前端入口要带产品线 scope，例如 `productLine=payment` 或 `productLine=reservation_queue`。
- 后端也要按产品线过滤可编辑 namespace，不能只做菜单拆分。
- 保存时必须校验 key 是否属于当前产品线，避免 PayNow 入口改到预约排队叫号文案。
- 旧共享路由可以保留 redirect，但不能继续作为主菜单入口。

PayNow 国际化边界参考：

- route：`/stores/:storeId/admin/payment/i18n-catalog`。
- API scope：`productLine=payment`。
- namespace：`payment`。
- category：`quick_pay`。
- Flyway seed：PayNow 自己的 tenant-editable i18n keys，例如 quick payment 提示文案和展示等待文案。

预约排队叫号国际化边界参考：

- route：`/stores/:storeId/admin/reservation-queue/i18n-catalog`。
- API scope：`productLine=reservation_queue`。
- namespace 白名单包括 `reason`、`public_booking`、`reservation_share`、`queue`、`call_screen`、`reservation_meal_period`。

实现检查点：

- `TenantAdminNav` 按产品线大类渲染，不把不同产品线子菜单混到同一个数组里。
- `useStoreVisibleApps` 或等价能力来自后端持久化状态，不用租户 code 特判。
- router route name 要体现产品线，例如 `tenant-admin-payment-i18n-catalog`。
- 旧 route 要有清晰兼容策略，例如 redirect 到所属产品线。
- UI source test 覆盖菜单分组、路由、文案 key、scope 参数。
- service/controller test 覆盖产品线 scope 过滤和跨产品线 key 拒绝。

## 测试矩阵

发布前至少覆盖：

- Migration：产品线 seed 存在。
- Migration：既有用户得到应有权限。
- Migration：无关角色没有得到高权限。
- Service：主流程成功。
- Service：关键领域失败。
- Service：幂等 replay 和 conflict。
- Provider：payload/adapter 输出稳定。
- Controller：App Gate 注解和错误码。
- UI/source：路由、API、共享组件接线正确。
- UI/source：租户后台菜单按产品线分组，未开通产品线不可见。
- UI/source：产品线国际化入口带正确 scope。
- Permission denied：权限拒绝时 UI 显示明确原因。
- Frontend build：前端 bundle 编译通过。

PayNow 验证命令参考：

```powershell
mvn -q "-Dtest=PaymentMigrationTest#grantsPaymentIntentPermissionsToExistingStoreStaff" test
mvn -q "-Dtest=PayNowPaymentUiAcceptanceValidationTest#payNowPagesUseRpbNativePaymentApiAndQrComponent" test
mvn -q "-Dtest=PaymentMigrationTest,PayNowPaymentUiAcceptanceValidationTest,PaymentIntentControllerTest,PaymentIntentServiceTest" test
npm run build
```

## 部署清单

部署前：

- 确认 exact commit。
- 确认 worktree 干净。
- 从 detached clean worktree 构建。
- 后端变更时确认 JAR 包含新 Flyway migration 和 Flyway PostgreSQL 插件。
- 前端变更时确认 artifact 来自同一 commit。
- 备份 backend JAR 和 frontend 目录。
- 明确生产写入 smoke 是否允许。

后端部署：

- 替换 `/opt/rpb/app/reservation-platform.jar`。
- 重启 `rpb-backend`。
- 确认 service `active`。
- 确认启动后 `ERROR` 数量为 `0`。
- 确认 Flyway 最新版本和 migration 成功。
- 确认健康/认证行为符合预期，例如 `/api/v1/auth/me` 返回 `401`。

前端部署：

- 备份 `/opt/rpb/frontend`。
- 解压 bundle 到 release 目录。
- 切换 `/opt/rpb/frontend`。
- smoke 关键公网路由和产品线路由。

部署后：

- 查 access log 中的 `403`、`500`、产品线 endpoint 失败。
- SQL 检查 active 用户缺失权限数量。
- 发布说明记录 commit、migration、备份路径、路由 smoke、服务状态和回滚方式。

PayNow 生产 smoke 没有执行真实收款写操作，因为没有受控测试 PayNow 标识。支付、订单、通知、账单、预约写入类产品线也应遵守这个原则。

## 发布说明模板

每次产品线发布说明至少包括：

- 版本/日期/commit。
- New。
- Changed。
- Fixed。
- Migration。
- Permission impact。
- Validation commands。
- Risk and accepted gaps。
- Rollback notes。
- Production deployment result。
- Backup paths。
- Smoke routes and status codes。
- 是否跳过生产破坏性写入。

## PayNow 踩坑清单

- 不要把菜单可见当作 API 权限完整。
- 不要只补租户管理员，忽略员工工作流。
- 不要只写既有账号 migration，忘记未来账号默认权限。
- 不要把 App Gate `PERMISSION_DENIED` 显示成普通“操作失败”。
- 不要没有受控测试数据就做生产写入 smoke。
- 不要从 dirty worktree 部署。
- 不要把前端同浏览器状态误当成跨设备同步。
- 不要把产品订阅、门店启用、运行时动作权限混成一个概念。
- 不要把 provider payload 逻辑写进 POS、预约或页面组件。
- 不要把不同产品线的租户后台菜单混在同一个功能列表里。先产品线大类，再产品线内子类。
- 不要把 PayNow 国际化字典和预约排队叫号国际化字典共用一个后台入口。
- 不要只拆前端菜单，不拆后端 i18n scope。菜单拆了但 API 仍返回全量字典，产品线边界仍然是假的。
- 不要只修复某个租户的菜单可见性。产品线菜单显隐必须对未来新增租户、新增门店持续生效。
- 不要让员工端读取后台完整配置 API。把运行时默认配置拆成窄 DTO，避免泄露 merchant profile。
- 不要接受 PayNow 手机号裸存。手机号输入可接受 8 位本地号，但保存和生成 SGQR 前必须规范化为 `+65xxxxxxxx`。
- 不要用真实业务收款单测试配置页。配置页测试码应生成独立 0.10 QR，不写入 payment intent/session。

## 下一条产品线模板

复制本段到下一条产品线计划里，逐项填空。

```text
Product line:
- app_key:
- display name:
- entry route:
- tenant/store scope:
- target users:
- billing model:

Reference source:
- existing system or workflow:
- reusable business process:
- things not to reuse:

Permissions:
- platform:
- tenant admin:
- store manager:
- staff:
- existing-user migration:
- future-user defaults:

Backend:
- module package:
- domain objects:
- application services:
- provider adapters:
- source adapters:
- repositories:

API:
- settings endpoint:
- create endpoint:
- read/list endpoint:
- idempotency:
- stable errors:

Frontend:
- admin settings route:
- admin product-line menu group:
- admin submenus:
- admin i18n route:
- admin i18n productLine scope:
- staff workflow route:
- customer/display route:
- navigation entry:
- error mapping:

Data:
- migration number:
- tables:
- tenant/store columns:
- constraints:
- backfill checks:

Tests:
- migration:
- service:
- provider:
- controller:
- UI/source validation:
- tenant admin product-line menu:
- product-line i18n scope:
- permission denied:
- frontend build:

Deployment:
- commit:
- backend artifact:
- frontend artifact:
- backup path:
- Flyway result:
- smoke routes:
- skipped destructive writes:
- rollback:
```

## GO 标准

全部满足后，才建议标记产品线生产就绪：

- 设计文档存在，且和实现范围一致。
- 产品线 seed 和 App Gate entry 已部署。
- 既有账号和未来账号权限都正确。
- 租户/门店 app 激活已验证。
- 租户后台菜单按产品线显示已验证。
- 产品线国际化入口和后端 scope 已验证。
- 后端 focused tests 通过。
- 前端 build 通过。
- 权限拒绝有明确用户提示。
- 使用 clean commit 产物部署。
- 生产 Flyway 状态已核对。
- 发布说明包含备份和回滚细节。
