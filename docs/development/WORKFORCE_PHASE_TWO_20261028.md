# 员工考勤与薪资第二阶段实施记录

日期：2026-10-28。状态：本地实现、针对性回归及实际API/浏览器验收完成；未提交、未推送、未部署。

## 规格与计划
- `docs/superpowers/specs/2026-10-28-workforce-phase-two-design.md`
- `docs/superpowers/plans/2026-10-28-workforce-phase-two.md`
- `docs/api/2026-10-28-workforce-phase-two.md`
- 第一阶段基础继续保留：`docs/development/WORKFORCE_PHASE_ONE_20261028.md`。

## 已实现
1. 门店班次模板：明确跨夜、无休息/达到门槛扣固定时长/固定窗口/实际休息进出、带薪休息、停用及版本冲突保护。
2. 简易周排班：员工/班次/日期独立表单，跨店冲突及相邻时段，取消保留历史；复制周最多100条，整批原子回滚，同命令重试不重复。
3. 本人打卡：上班/休息开始/休息结束/下班状态机，服务端UTC时间，进行中session跨店唯一，按钮防重复，未确定命令持久化恢复。
4. 出勤：计划快照、实际上下班、休息区间、事实时长、缺卡/未结束/超长异常，员工仅本人，店长仅本店；独立详情保留原始事件及补卡历史。
5. 补卡：完整出勤区间、多个实际休息段、覆盖自动休息、原因；员工提交/撤回，另一名管理员批准/拒绝，禁止本人审批；批准更新有效出勤且原始事件不变。
6. 普通员工切店使用新增 `Home.clockStores`，仅本人工作分配与Auth访问范围交集；原 `Home.stores` 管理范围不变，避免扩展员工管理表单。
7. 紧凑中英文独立页面，管理端班次排班/出勤二级导航，员工今日打卡/我的出勤；390px窄屏检查通过。

## 模块归属
- `workforce/scheduling`：SchedulingDtos、SchedulingRepository、ShiftService、ScheduleService。
- `workforce/attendance`：AttendanceDtos、AttendanceCalculator纯计算、AttendanceRepository、AttendanceAccess、ClockService、AttendanceQueryService、CorrectionService。
- `workforce/api/WorkforceEndpointSupport`：共用同源写保护、身份解析、no-store及稳定错误映射；第一阶段Controller签名保留，新Controller仅调用应用层。
- WorkforceEmployeeService沿用员工事务锁，活动出勤期间禁止改变工作门店/公司，保留一般资料维护。
- `src/modules/workforce`：独立班次/排班/打卡/出勤/补卡页面，phasePage控制异步及切店隔离，time纯函数处理门店时区与DST，phaseText双语。无全局样式污染。
- 未修改原钱包组件、依赖文件、组织基础数据及其他产品的默认业务权限。

## API / Database Review
V089新增6表：workforce_shifts、workforce_schedules、workforce_attendance_sessions、workforce_clock_events、workforce_corrections、workforce_schedule_copies。复合外键防跨租户引用；排班/活动出勤唯一、pending补卡唯一、版本/区间check、范围索引。

班次快照存入排班，后续改模板不影响历史；复制时按当前启用模板新建排班，已存在快照不变。所有写入与现有Audit同事务。shift/schedule upsert显式限制tenant/store/employee，猜其他租户ID不覆盖数据。补卡时间及休息端点统一到PG微秒精度后比较/入库。事件仅追加，无覆盖/删除接口。

精确GET/PUT路由加入本地安全配置；没有扩大其他路径。本阶段未新建角色、会话或订阅机制，工资权限没有成为考勤越权途径。

## 测试与证据
### Java / 真实PostgreSQL
最终命令：
```powershell
mvn -q '-Dtest=AttendanceCalculatorTest,Workforce*Test,AuthPrincipalStorePermissionsTest,StaffStoreAccessPolicyTest,StaffPermissionsServiceTest,StaffPermissionsPostgresTest,StoreSubscriptionAppGateTest,StoreProductSettingsServiceTest,ProductLineEntryRegistryTest' test
```
共97项测试执行，0失败、0错误、0跳过；其中第二阶段PG类27项包含继承的11项第一阶段回归及16项第二阶段场景。全部读取`target/local-postgres-current.txt`（本轮61644，owner D:\RPB），在自行创建的独立schema验证，结束仅清理其自身schema。

覆盖：休息门槛/交集/带薪/实际覆盖、跨夜、open不算工时、超长异常；原始事件/快照保留、命令重试、跨店并发、相邻排班、周复制中途冲突整体回滚、补卡陈旧版本/本人审批/跨店重叠拒绝、审计失败回滚、活动归属保护、跨租户upsert、普通员工切店范围。

### 前端
```powershell
node --test src/test/frontend/workforce.test.mjs src/test/frontend/staff-permissions.test.mjs src/test/frontend/staff-permissions-panel.test.mjs src/test/frontend/staff-store-assignment.test.mjs
node --import ./scripts/tests/workforce-register.mjs --test scripts/tests/workforce-phase2.test.mjs
npm run build
```
前一组15项、第二阶段18项，共33项通过；类型检查及Vite构建通过。新增纯函数/真实Vue renderer覆盖时区/DST、多段休息、状态动作、双击与重挂载恢复、切店旧响应、本人不能审批、无员工档案总部审核、复制4xx解除锁定/网络保留UUID、隐藏休息不误提交、空日期不崩溃、个人切店与管理范围分离、今天已下班和历史记录区分。日志`target/workforce-phase-two-validation/frontend-tests.log`及`target/workforce-phase2-frontend-build.log`。

### 实际API
`node scripts/validation/workforce-phase-two-api.mjs`，真实Spring Boot和现有认证过滤器，共22组检查通过：建档/授权→班次→排班→本人上班→休息进出→下班→出勤→补卡→店长审批，及重复UUID、冲突、本人审批拒绝、模板不漂移/原始事件保留、个人切店范围。结果`target/workforce-phase-two-validation/api-result.json`。

独立本地验收schema：`workforce_browser_59f846b0389c412dbc7a7e8cf4d600ed`；后台8092、静态预览5192，均127.0.0.1。没有对public、原runtime业务schema或生产执行V089。

### 实际浏览器 / UI Review
CUA验证：周排班和班次列表；独立表单创建固定休息班次成功；切换测试员工后有本人双店选项；出勤详情显示事实结果和4条原始事件；员工提交第二份补卡成功，切换测试店长后审批成功。中文/英文390px打卡页无重叠；桌面出勤/审核页结构清楚。没有真实手机、微信或现场位置验证。

截图：`target/workforce-phase-two-validation/attendance-desktop.png`、`correction-approved-desktop.png`、`clock-mobile-en.png`。

本地proxy的`/__fixture/role/*`及测试会话仅用于隔离fixture，不是产品接口；代理不能部署。fixture和构建产物都在ignored target，含测试会话的fixture.json不可提交。

## 审查与修复记录
独立只读审查先发现两项：
- 昨夜排班在今日移出门店后仍可首次IN：真实PG先观察未拒绝，再增加锁后当前storeIds验证；已有session收尾保留。
- 纳秒补卡原请求重试409：真实PG先复现，再将所有端点统一微秒。两项最终均通过；审查者针对性复核APPROVE，没有未解决必要问题。

前端联调修复：账户绑定控件基础保持；自动休息关闭override时清除隐藏休息请求；复制确定4xx后释放目标周，网络失败保留命令；清空日期避免RangeError；下班后显示完成状态而不是要求联系管理员。

## 本地验证环境问题
旧阶段后台持有默认target JAR，打包无法重命名；同时测试与打包共用target导致短暂NoClassDefFoundError。这一轮受干扰结果未计入通过。

新增`scripts/validation/workforce-package.ps1`从原pom复制到ignored临时POM，仅重定向源码/资源与输出目录，依赖及原pom未变；独立打包到`target/workforce-phase-two-package`，启动脚本复制到本fixture的backend-runtime.jar。恢复后97项最终测试通过，独立打包、实际接口和浏览器都通过。没有停止或删除原后台/原文件来绕过占用。

## 剩余边界
- 必须先有排班；未排班不自行猜班次。排班调整只支持今天至90天内；工作时区DST歧义拒绝，不猜偏移。
- 账号打卡不证明员工在现场，未增加GPS/动态码/人脸；不提供离线回填原始打卡时间。
- 原始事件不可改，事实工时尚非核准工时；请假、法定加班、工资、CPF、工资单在后续阶段。
- 产品停用/到期仍按原AppGate限制，历史数据保留；本阶段未新增到期历史查阅政策。
- 未执行全项目所有测试、生产迁移或真实设备验收，不能据此声称全产品无任何回归。
- 保留第一阶段及用户已有工作区改动；不提交、不推送、不部署，不自动进入第三阶段。
