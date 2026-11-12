# 员工考勤与薪资：第一阶段实施记录

日期：2026-10-28。状态：本地实现与验证完成；未提交、未推送、未部署。后续从本文件及关联规格继续，不能靠聊天记忆推断阶段状态。

## 依据与范围
- 规格：`docs/superpowers/specs/2026-10-28-workforce-design.md`
- 计划：`docs/superpowers/plans/2026-10-28-workforce-phase-one.md`
- API/数据库合同：`docs/api/2026-10-28-workforce-phase-one.md`
- 本阶段实现：新产品注册/开关/入口、公司发薪配置、员工基本资料、公司归属/工作门店历史、仅打卡账号及既有账号绑定、明确门店管理员授权、公司工资权限范围配置、本人基础资料。
- 打卡、排班、证件上传、请假、工时核准、工资公式、CPF计算、工资单均未实现；没有虚假可操作入口。

## 模块与文件
后端 `workforce/application` 拆为 Access、EmployeeService、CompanyService、AdminService、HomeService、Audit、DTO；`persistence` 为独立 Directory/员工/公司/授权适配器；`api` 只调用应用服务。Auth 新增 `WorkforceAccountProvisioner`，复用已有账号/角色/权限/门店访问及密码编码器，不使用旧员工批量默认权限。

前端 `src/modules/workforce` 独立路由、API、类型、上下文、文案、限定作用域 CSS。列表、员工基本资料、工作门店、公司列表/配置、管理员列表/授权、本人资料分别为页面。复用 ProductPageHeader，中英文、390px 窄屏、加载/错误/空状态。

共享接入只涉及产品 manifest、AppGate 对 workforce 的显式门店订阅要求、平台建号新增 workforce 权限、本地安全精确端点、产品管理名称/类型、员工首页产品卡及路由。不改预约/收款/钱包/存酒/账本原权限与规则。未触碰用户原有钱包组件修改。

## 关键实现决策
1. 工作分配与既有账号门店访问分离：首次绑定必须在授权者范围内，工作店须属于账号已有范围；绑定不改密码、角色、其他产品权限或账号门店。日常编辑保留绑定时，不因账号停用或拥有额外旧产品门店而阻断维护。
2. 第一阶段新增门店须先在既有账号管理中授予登录访问；本模块不会隐式扩展旧账号门店，以免扩大其他产品权限。
3. 门店管理员按显式 workforce 委派管理；原有 store_manager 即使兼 tenant_admin 也不是总部管理员。总部需 workforce.admin.manage + tenant.admin.manage + tenant_admin，且非 store_manager/platform_admin，仍受既有门店范围约束。
4. 员工全部工作店都在管理范围内才允许编辑；只部分覆盖时，仅返回姓名/编号/岗位/在职状态和可见门店，隐藏联系方式、发薪公司、账号及其他门店。
5. 公司配置及工资授权要求该公司全部有效门店都在总部操作者范围内。工资授权只配置一个明确公司，可关闭；本阶段没有工资读取/计算接口。
6. 新员工 PUT 使用客户端 UUID 和 expectedVersion=0，可安全恢复同内容创建；每员工事务 advisory lock 防止重复账号。其他更新版本不符返回409，需刷新核对。审计失败整体回滚。
7. 员工入职日期创建后不可修改；公司变更和门店分配变更当天生效并保留历史。未来调动/历史修订需后续明确规则，不偷偷覆盖历史。
8. 发薪配置停用阻止新增和调入，不删除已有员工。

## 数据库与权限评审
V088 新增5张表（公司配置、员工、公司历史、工作店历史、管理员授权及相应索引）并注册 workforce 和 disabled 零价格记录。没有任何租户订阅、门店订阅或产品开启回填。

新增 `workforce.view`；仅真实总部管理员增加 `workforce.admin.manage`，旧店长不获总部权限。租户/公司/门店/账号复合外键、员工编号租户唯一、账号唯一关联、版本与日期约束。没有删除接口。

迁移和验收只作用于 pointer PostgreSQL 中自动测试独立 schema、`workforce_browser_379bda89fa8a426e8c044d55e6a225cf`；`public` 和原 runtime schema 未应用 V088。自动测试在结束后仅清理其自行创建的 schema，浏览器 fixture 保留供复查。

## 验证证据
### 后端 / TDD Review
先观察新模块缺失导致的测试失败；权限测试转绿。新增审查问题回归先复现停用账号编辑失败，再修复并通过。

命令：
```powershell
mvn -q '-Dtest=Workforce*Test,ProductLineEntryRegistryTest,AuthPrincipalStorePermissionsTest,StaffStoreAccessPolicyTest,StaffPermissionsServiceTest,StaffPermissionsPostgresTest,StoreSubscriptionAppGateTest,StoreProductSettingsServiceTest' test
```
11个测试类，共65项，0失败、0错误、0跳过。其中 WorkforcePostgresTest 11项使用 pointer 实例真实 PostgreSQL：全迁移、仅打卡权限、创建重试、并发创建、绑定保留原授权、停用账号/门店管理员日常维护、部分范围脱敏、公司/门店历史、审计回滚、公司停用/版本、跨租户外键和无自动订阅。

AppGate 测试覆盖未购买、门店关闭、到期；API 测试覆盖401、同源请求保护、409及no-store；原权限与产品管理相关回归通过。产品管理原测试固定5项，已改为按共享目录数量验收，当前6项。

### 前端 / UI Review
```powershell
node --test src/test/frontend/workforce.test.mjs src/test/frontend/staff-permissions.test.mjs src/test/frontend/staff-permissions-panel.test.mjs src/test/frontend/staff-store-assignment.test.mjs
npm run build
```
15项交互/权限测试通过；类型检查及Vite构建通过。新增测试验证绑定选择控件不会意外卸载、部分范围不可编辑、切回原公司恢复生效日期、PUT同源请求合同。

### 实际 API 与浏览器
`scripts/validation/workforce-api-acceptance.mjs`：真实 Spring Boot + 既有会话过滤器 + pointer 独立 schema，14组验收通过。包括首页/公司配置、建档和重试、委派后店长维护、版本冲突、禁止店长自授工资权限、跨源拒绝、跨店脱敏和现有身份接口。

CUA 实际浏览器：查看员工列表，独立新增员工表单提交成功，列表出现 WF003 测试员工；检查中文390px、英文390px表单，未发现重叠。未做真实手机或微信验证。

截图：`target/workforce-validation/employees-desktop.png`、`target/workforce-validation/employee-mobile-en.png`。

本地API后台8089，静态验收入口5191，均绑定127.0.0.1。静态代理注入的是自行创建的隔离测试会话，只用于本机验收，不能部署或作为正式登录方案。fixture.json 含测试会话，位于ignored target，不可提交。

### 本地环境处理
pointer 指向61644的已有PG未运行，按快速重启指南恢复原dataDir，未切换数据库。Windows JDK Unix Domain Socket临时路径导致Tomcat启动失败，验证脚本设置短路径 target/wf-uds 后启动成功。Vite源码资源加载超时，改用已构建dist静态验收；未修改产品运行配置。旧测试辅助进程未强制终止，静态验收使用另一空闲端口。

## Code / API Review
独立只读审查发现“旧账号范围/停用状态误阻断资料维护”；已针对性修复并回归。控制器不直接访问Repository，敏感密码不进入DTO响应/审计。新建及更新事务、版本、同源保护和最小数据范围已核对。无未解决必要审查项。

## 后续与风险
- 第二阶段才开始班次、打卡与出勤，不因本阶段完成自动进入下一阶段。
- 第一版当前默认单个发薪公司，尚无双重雇佣/公司间结算。
- 已绑定账号增加工作店前需先处理其既有账号访问范围；避免隐式扩权。
- 未跑整个项目全部测试、生产数据库迁移或真实设备验收；不能声称全产品无任何回归。
- 若后续部署，须另获明确授权；先核实迁移编号未与并行任务冲突、价格/开通策略，再进行发布评估。
