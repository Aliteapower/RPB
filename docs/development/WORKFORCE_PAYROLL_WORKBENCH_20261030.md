# 月度工资工作台本地实现

日期：2026-10-30；项目D:\RPB；Task3。HEAD保持52d20557，不提交、不推送、不部署。

## 实现

独立PayrollWorkbenchService按公司/月进行REPEATABLE_READ一致读，编排既有完整PayrollSources、PayrollCalculationService校验、PayrollProgressService双基线及原付款台账。只提取当前计算的issues，不保存或返回第二份工资金额。无批次也显示资料/计算阻断；资料人数按employeeId去重，待核准人数与班次分开，上月周context独立计数并带实际工作日入口，不再次付款；自由方案免核准。证件只按已有引用有效性检查，付款偏好不作为计薪必需项。

新增精确GET payroll-workbench端点；公司/月/batchId/expectedVersion绑定，版本或批次身份不符拒绝拼接。重新鉴权当前捕获、缓存、对上次比较保存的历史门店及前月冻结快照。AppGate与薪资公司权限沿用，employeeManage不等于工资权。旧/新版本指纹只返回摘要，不暴露原始隐私载荷。

PayrollSourceReasons仅把可证明的个人方案版本、当月持久化输入版本、有效核准、真实工时、有效核准假时和政策差异细分；旧证据不足与其他无法定位的变化用通用source，不能推测补卡/排班/雇佣等具体字段。岗位默认未应用不参与个人工资依赖。冻结工资当前来源变动只作警示，不改原快照。未计算BatchSummary金额为null，不再假称0；双基线缺失仍available=false和null差额。

独立付款投影以BigDecimal复用原登记减冲正，逐员工未付/部分/付清/零净额无需付款。批次状态仍五种，发布和付款独立。阻断预览的占位金额不可被解释成合法零工资；已确认/已发布金额与已登记付款保留；voided仍不可修改，但含阻断的原计算不能因作废而将0占位变成有效应付。付款命令、防超付、非本人、幂等及响应丢失原ID恢复均沿用，不新增转账。

前端工资首页可按公司/月办理，批次详情使用同一工作台替代两份独立查询。付款汇总、来源理由、缺项及核准入口分组件；原因优先员工姓名，时间按Asia/Singapore及语言格式化。已发布明确提示金额更正尚未支持；付款冲正不能代替工资更正。跨公司工资归属保持原状。

## 验证

准确命令、真实红绿、数量和残余边界以 `.superpowers/sdd/2026-10-30-workforce-end-to-end-payroll/task-3-report.md` 与 `target/workforce-end-to-end-20261030/task3-*` 为准。

数据库仅读取当前pointer的127.0.0.1:61644/postgres，以既有WorkforcePostgresTest随机隔离schema验证；不重启或重置原应用/原schema。浏览器连接故障，本阶段不声称视觉、窄屏、真机、银行或生产验收。无新增迁移，无业务金额更正规则。

最终本地结果：后端59执行全通过；随后仅引擎原因独立顺序修复，真实RED1→完整最新业务编译及9投影GREEN。全前端串行783/783，核准直达入口后专项23/23、npm build（包含vue-tsc）通过。scoped diff-check通过，钱包8/8哈希未变，HEAD保持52d20557。此前全前端并行一次钱包时序失败，钱包文件单跑101/101及最终串行全量通过；未修改钱包。

## 最终集成修复

双基线比较共享PayrollSourceReasons的政策身份、持久化输入版本和SourceEvidence准则；新比较带reasonBasis=snapshot_evidence_v1。旧已存比较没有完整before时只读投影legacy_comparison，明确原因未验证并保留原差额/类型，不改历史JSON。付款frozen不可修改与trustedAmounts金额可信分开，后者只含confirmed/published；voided的阻断仍令金额不可得。原作废、付款、更正、自由全额及权限规则保持。

最终修复红绿及受影响回归见 `.superpowers/sdd/2026-10-30-workforce-end-to-end-payroll/final-fix-report.md`，日志 `target/workforce-end-to-end-20261030/final-fix-*`；此前阶段结果仅按报告范围复用，不冒充本轮全量执行。
