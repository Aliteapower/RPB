# 租户模板发布及计薪变更审批实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. 不提交、不推送、不部署；无设计转写二次审批节点。

**Goal:** 完成发布→范围可见→门店采用→独立变更申请→租户批准有效版本→计算证据及批量升级闭环。

**Architecture:** 在workforce/payroll内分出版/采用、申请、守卫/解析、影响预览及批量服务，复用现有权限、审计、配置表、source锁与计算器；Controller不访问仓储。

**Tech Stack:** Spring Boot/Java21、PostgreSQL/Flyway、Vue3/TypeScript、node:test。

**Spec:** ../specs/2026-10-30-workforce-end-to-end-payroll-design.md第13节；用户当前完整要求优先。

## Global Constraints

- D:/RPB当前树增量，基线HEAD52d20557与target/workforce-template-governance-20261030/baseline保存的1291份当前文件；保留全部既有未提交/未跟踪内容及钱包8文件，不清理、不重置、不提交推送部署。
- PG严格用target/local-postgres-current.txt的本项目运行时61644/postgres，postgres空密码；测试只用新随机隔离schema或明确归属本轮的schema，不重置已有业务schema。
- 自由员工全额基本工资、历史/付款快照、租户公司门店权限保持；跨公司工资归属、已发布金额更正不新增规则。
- 不得杜撰AW/补缴税务公式、人工阈值、自动批准、自创同人禁止；用户已新增授权奖金/AWS/AW/跨期补缴，待官方设计核实后在阶段5/6补齐，未知金额null并说明，不按0处理。
- 每阶段红绿测试、接口/数据库/UI/权限复核及真实日志；没有覆盖的必要入口必须列出，不能将阶段1发布完成当成完整审批完成。

## Review Focus

- 新店自动可见必须重新验证scope/门店权限，不能以同步扩大工资权；发布后才新建的匹配门店可见但未启用。
- 已发布/已提交内容不可变，重复命令与竞态不能使审批快照和实际写入不同；相关字段在同一申请聚合。
- 原API、批量CDAC、仓储直写、岗位映射/采用、未来月份及作废重建不能跳过批准或丢失批准证明。
- 旧有效资料无伪审批、未确认计算/缓存明确过期、冻结JSON/付款不变，个人工资单不能泄露内部申请说明。
- 影响未知不能零差异；升级三方冲突不自动合并，批量独立结果/事务且部分失败可恢复。

### 阶段1：租户发布、范围与门店可见/启用

**Files:** 新增workforce/payroll/governance中Publication DTO/Repository/Service及API Controller；V108；独立模板库Vue页/组件、routes及管理入口；src/test/java/com/rpb/reservation/workforce/WorkforcePayTemplatePublicationPostgresTest.java及API/security；前端真实组件测试；阶段API/开发记录。

**Interfaces:** 发布快照/作用域/母版固定版本、门店采用及materializedTemplateId可被后续阶段精确解析；不主动写个人方案或批次。保存精确API契约供阶段2/3消费。

- [x] 核对原校验/权限/表结构，写并运行失败测试：现有/新增门店、未来门店scope、未启用、重复启用、租户/公司/门店隔离、母版固定版本、发布升级不覆盖。
- [x] 实现不可变发布、动态可见和显式预览/启用，门店采用来源版本与独立materialized模板；不扩大工资读取权。
- [x] 实现模板发布/门店目录/采用状态UI及新增管理入口，保持原legacy模板可读；相关PG/API/精确安全/UI/类型验证。
- [x] 更新API/开发记录/迁移说明并独立复核，记录尚未接入审批入口，不宣称完整审批完成。

### 阶段2：门店派生、个人及月度金额申请/审批

**Files:** governance中ChangeRequest DTO/Repository/Service、ImpactPreviewService、相关Controller；V109；个人方案/月度输入/门店派生独立申请UI；测试及契约。共享PayrollDtos/校验若需抽取只拆纯校验，不重写金额计算。

**Interfaces:** 统一request id/revision/hash、subject/effectiveDate聚合、submitted固定快照、approved有效证明、按月输入版本及审批事件供阶段3守卫/捕获消费。与publication服务单向依赖，不循环调用服务。

**独立实施边界：** 阶段2A负责Java/迁移/后端测试/API；2B负责Vue页面/组件/route及node前端测试，只读消费2A的PayChangeDtos与API契约。2A不改前端、2B不改Java/SQL/API；父任务统一验证契约并审查阶段2整体，避免重复实现或同时编辑同一文件。

- [x] RED覆盖同员工同日期合并、草稿/提交不可改、撤回重提、驳回、批准待生效、原方案继续使用、缺方案阻断及权限/来源基线过期。
- [x] 保存母版/本店/原方案版本、patch和完整结果，提交与审批幂等、事务/审计一致；批准前重检当前权限/员工公司日期规则及冻结约束。
- [x] 预览复用现有capture/calculator只读替换，不写工资/缓存；未知null、确定标准差额、风险提示不自定阈值。
- [x] 可操作的申请与租户审批UI：before/after、理由、生效/月份、影响员工与待计入提示；单独测试PG事务回滚/并发/重复、API/security/UI。
- [x] 阶段API/记录及独立复核。

### 阶段3：写入口闭合与有效版本计算

**Files:** PayrollConfigurationService/Repository、PayrollBatchService/Repository、CdacBulkService、PositionSalaryDefaultsService、PayrollSources/Incremental/Lineage/Workbench、必要Profile/LineInput可选证据、其他实际写入口；governance守卫/解析；迁移及兼容测试；对应Vue旧保存入口分流与状态提示。

**Interfaces:** 所有金融写入只能匹配合法直接采用或精确已批准版本；捕获只用legacy-valid/批准有效结果，来源证明入快照/指纹，月度版本不因批次重建丢失。工时核准独立。

**独立实施边界：** 3A负责Java/迁移/后端测试/API与入口审计，3B负责旧维护表单到申请的前端衔接/有效来源显示及组件测试；前端等3A错误码/批量结果契约明确后实施，不把前端判断作为后端许可。各自不编辑对方目录，整阶段仍需独立复核与最终真实浏览器验收。

- [x] 形成逐项入口清单及必要性裁定：模板/个人/月度/CDAC批量/映射/导入/考勤规则/工作方式/法定/事实数据；必要入口先RED绕过测试。
- [x] 统一守卫禁止旧接口/仓储旁路，原样直接采用免重复审批、个人覆盖必审批；同日申请完整结果与实际保存一致。
- [x] 新增兼容证明保留迁移前有效模板/方案/月度输入，不伪审批、不回写旧JSON；未确认输入/计算按规则过期；future月度及作废重建解析不丢批准。
- [x] 捕获/缓存/工资明细追溯有效采用/批准版本；待审/驳回仍旧方案，无方案明确阻断；冻结工资/付款及本人脱敏不变。
- [x] 复测所有必要入口、并发/回滚/权限/旧JSON/自由全额与原核准计算发布付款；UI与文档显示未完成管控范围，独立复核。

### 阶段4：批量审批、升级差异及冲突体验

**Files:** governance BulkApproval/Upgrade DTO/服务及Controller、模板/申请列表与差异组件；测试、契约与开发记录。

- [x] RED覆盖门店/月筛选、每申请独立事务与权限、部分成功/stale/失败、重复请求/恢复、不能审批已改内容。
- [x] 三方差异显示母版前后与门店修改，冲突必须显式选择/申请；保留旧版不修改员工，升级固定目标版本且不自动合并。
- [x] 减薪/新增扣款/追溯提示及金额幅度排序；未知影响不可零值；未来批准/有效/有新版本等维度分开。
- [x] PG/API/组件、类型构建、最终入口审计/历史指纹及独立整轮代码审查；必要缺陷已定点修复。
- [ ] 新增治理/财务界面的真实浏览器与窄屏验收（连接环境受阻，不以HTTP/组件替代）。
- [x] 更新原MD、API及本地实施记录，报告各阶段证据/未覆盖及未验证风险；成果本地保留，无commit/push/deploy。


阶段4代码/自动验证及独立审查通过；真实浏览器/窄屏仍因连接环境未验收，不能勾选为完成。继续不依赖该环境的阶段5/6，最终保留明确缺口。阶段5A只写Java/SQL/后端测试/API，5B在稳定契约后独立负责UI与组件测试；避免与内核并行改同文件。

### 阶段5（新增授权）：奖金／AWS／AW分类、年度额度与扣缴

用户已明确要求支持计薪，不能仅保留申请功能。官方核对已完成，执行spec13.11及target/workforce-template-governance-20261030/bonus-aw-arrears-design-check.md F1/F2；阶段5已实现、自动验证及独立审查通过。

**Files/Interfaces:** payroll工资事项分类/不可变wage event，statutory V2政策能力及明确OW/AW月累计输入，statutory/annual年度事实、预测revision、AW额度分配与复核；PayrollSources/Calculation/Incremental统一调用。新增年度上下文管理UI/事项申请UI，沿用阶段2批准proof，不新增另一套审批。迁移以当时最大版本顺延。

- [x] F1：eventId、业务类别和OW/AW分开；服务/决定/合同应付/贡献月/结算月证据与分类验证；旧ow_additional和旧JSON兼容；V1无AW能力不得默默计薪。
- [x] F2：年度12月actual/forecast/known-zero/unknown、年度OW受约束总额、AW有序额度/费率分段；当前有效批准方案作可审阅预测，缺历史/工时不当零；年终/离职复核与退款待办。
- [x] 同月原始OW+AW选TW档，分别应用OW/AW ceiling后统一舍入；T1–T5/年龄/低工资边界、AW额度100或0仍影响TW档、两店共享/两公司独立、并发不超耗、拆批与整批等价。

- [x] 官方分类、OW/AW所属期、年度上下文及低工资带/舍入方案经设计复核，补充精确实现任务与数据契约。
- [x] 奖金/AWS/AW接入统一申请、有效月度版本、年度记录/预估与年终复核，未知来源阻断；既有OW/自由全额不回归。
- [x] PG/纯计算官方样例/权限/并发/历史JSON/付款隔离、组件及独立审查。
- [ ] 新增页面真实浏览器/窄屏验证。

### 阶段6（新增授权）：独立跨期补发与补缴

原工资和付款保持不改，新补发/少缴以独立批准记录及来源期证据实现，不以付款冲正改工资。执行spec13.11及研究报告F3/F4，controller不计算，仍复用唯一计薪内核。

**Files/Interfaces:** payroll/supplemental差额batch/items/snapshots和原单链；statutory/settlement责任账/remittances/allocations/冲正；aw-reconciliations调用阶段5内核。API固定supplemental-batches、statutory-year-contexts、aw-reconciliations、statutory-remittances精确路由后配安全；同公司同员工同年度和公司月份串行锁序明确，保留batch→source，不引入source→batch。

- [x] F3：当前奖金、后来追溯加薪、原已到期漏计、仅法定少缴、年度AW复核分流；事项只能消费一次；既有已算未付继续原支付，不复制工资；已扣未缴不再扣，雇主错误员工份额不自动追扣。
- [x] 附录B公开官方oracle：逐原月目标整数责任减已确认责任再结算，跨龄分币差额6540/3519/3021；总excess0而员工+1/雇主−1不能结清。旧责任与实缴分离；s7(9)AW重估合法可追回与普通雇主漏扣成因分流，不自动扣工资；混合费率年度退款资格/待办独立显示，不执行自动退款。
- [x] 同月SDL个人上下限/公司floor仅一次，CDAC目标月额减已入账/已扣/实缴分别列账；manual/override/opt-out保留；5000+2500且已扣缴1.50只差0.50，纯CPF差额不增SDL/CDAC，SHG发现当年/跨年规则。
- [x] 独立法定缴纳分配、部分登记/冲正/命令重试/外部回执，本金与官方核定利息分开；负差额待退款/追回不净抵工资；原期政策/身份/责任/年度事实缺失分别阻断。
- [x] F4：年度缺资料/AW剩余与复核、差额前后/原单/工资现金余额与法定余额、工资单脱敏、独立状态UI；权限/锁并发/FK/幂等/冻结hash及原工时→计算→发布→付款自动回归。

- [x] 工资原已到期漏发、后来决定追溯加薪、CPF少缴等分类；所属期政策/原已缴证据/年度重算/SDL累计差额及员工份额处理方案明确，缺数据阻断。
- [x] 接入独立补发/补缴流程、审批、不可变来源与分离登记，防重、幂等及并发；不自动转账/申报。
- [x] 旧工资/付款指纹、独立补缴差额及来源期/决定期、权限/未知值/跨年度、自由全额及全流程回归，最终全范围审查/MD/证据更新。

- [ ] 本轮新增页面真实浏览器/窄屏验收；旧390/320截图只属于当时版本。

最终代码及自动/本地HTTP验证已完成，详见[2026-10-31本地实施记录](../../release-notes/2026-10-31-workforce-template-governance-financial.md)。整轮终审F1旧CDAC空快照和F2跨年结算月均已定点关闭。最后27后端/内核、32组件及类型/Vite通过；前面各轮证据仅按未变范围复用，不相加为单次全量。三条金融实际HTTP及完整前端DTO验证通过，原工资/付款/年度快照保持；没有提交、推送或部署。

支持组合以canonical及API为准：低工资AW年度额度截短的未核实交叉档仍明确阻断；真实浏览器、真机、银行/CPF平台、性能和生产验收未执行。不能因为代码和合成HTTP通过而勾选上述外部验收。
