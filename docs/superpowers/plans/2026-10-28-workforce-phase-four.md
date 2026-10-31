# 第四阶段实施计划

**Goal:** 公司工资月批次，核准来源→计算→冻结→发布工资单→付款登记。
**Architecture:** 独立statutory纯规则与payroll配置/来源/批次/付款/本人单，Vue独立页面，原Auth/组织/工资委派。
**Tech Stack:** Java/Spring/JDBC/PostgreSQL，Vue3/TS，BigDecimal/规范金额字符串。
**Spec:** docs/superpowers/specs/2026-10-28-workforce-phase-four-design.md
**API:** docs/api/2026-10-28-workforce-phase-four.md

## 全局约束
本地D:\RPB复用未提交前三阶段；不改钱包/依赖/旧产品默认授权，不提交/推送/部署。pointer PG新schema。普通OW/2026版CPF+SDL；不假装支持AW、跨期补缴或所有法定轮班规则。
## Review Focus
- 普通店长权限不能升级为工资；仅本人已发布单可被员工读取。
- 新增排班/补卡与工资确认竞争不能漏计，必须统一source guard锁顺序。
- 月/日薪不能从小时硬猜计薪日；日周OT不能双算，前月周上下文不能重复支付。
- CPF低工资/年龄/PR变档和SDL公司取整，禁止浮点。
- 工资确认后不可暗改，重复付款/跨员工冲销/本人确认必须拒绝。

### Task 1: 合约及纯计算
- [x] 核对旧源码、官方依据，落盘设计/API/计划。
- [x] 先写StatutoryCalculatorTest/PayCalculatorTest，观察缺实现或断言RED。
- [x] 实现纯CPF/SDL与薪资/OT，运行GREEN。
### Task 2: 存储、权限、来源
- [x] V091六表，复合FK、唯一公司月和付款冲销约束。
- [x] PayrollAccess/配置，工资scope不复用employeeManage；本人API只发布单。
- [x] 各workforce源写共享锁，PayrollSources排他读取，完整公司/跨店来源和版本指纹。
- [x] 真实PG验证越权/资格/同月唯一/快照/过期/并发和审计。
### Task 3: 批次及付款
- [x] 先测draft→calculated→confirmed→published、重复命令、无有效核准/缺方案阻断。
- [x] 实现行输入、个人方案冻结、金额汇总、状态命令、付款/冲销及本人工资单。
- [x] 验证本人不能确认/付款，超付、冲销一次、原始单保留。
### Task 4: HTTP及UI
- [x] 独立PayrollController，精确security路径、同源/no-store/错误测试。
- [x] 独立紧凑双语页面（模板、方案、批次、行、工资单、付款）。
- [x] 实组件测试、前端构建、前三阶段/权限回归。
### Task 5: 集成和文档
- [x] 独立本地fixture/backend/proxy及真实API/browser，不动原运行实例。
- [x] 独立只读审查，修复必要问题并验证。
- [x] 实施记录、release note、阶段状态，明确未部署和未支持部分。

## 执行记录
2026-10-28 用户已授权继续，设计转写不新增审批节点。模块与前端依明确API独立分工；主代理集中Maven，禁止多人共用target同时编译。

最终证据：248项后端/63项前端/83组真实API、构建、浏览器及审查闭环见 docs/development/WORKFORCE_PHASE_FOUR_20261028.md。未提交/推送/部署，阶段五未开始。
