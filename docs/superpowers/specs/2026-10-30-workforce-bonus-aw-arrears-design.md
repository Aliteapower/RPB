# 奖金、AWS／AW 与跨期补发补缴设计核对

核对日期：2026-10-30（Asia/Singapore）。工作区：D:/RPB。性质：独立研究／builder-check；产物为本报告及同目录 `cpf-aw-public-calculator-oracle-20261030.json` 合成试算证据，没有修改业务代码、运行项目测试／SQL、提交、推送或部署。公开 CPF 计算器试算只使用虚构数据，没有登录、真实员工资料、申报或资金操作。

本轮用户已授权同时支持奖金／AWS／AW 和跨期补缴，并继续四阶段审批治理；历史工资金额、计算快照和付款事实不可回写，跨公司工资归属原规则不变，实际转账和自动 CPF 申报不在授权内。本文件补充主方案第 13 节、13.9 与最新追加的 13.10，不能再将这些新能力统称为“未来考虑”，也不把开发具体化变成新的审批节点。父任务已安排原四阶段完成后接阶段 5／6；下文 F1–F4 是这两个财务扩展阶段内部的任务拆分，不改变该顺序。

## 1. 结论与现有边界

需要增加“工资事项分类与日期证据、按雇主／员工／年度的工资事实账、AW 年度额度及复核、独立差额批次、法定缴纳登记”五项能力。现有 OW-only 算法不能把参数改成 OW+AW 后直接复用。工资金额审批继续复用本轮治理申请，法定计算仍只有一套可信内核；独立差额批次不复制出第二套工资规则引擎。

已读源码（本次读到的状态，父任务仍在并行实现）：

| 路径（均在 D:/RPB） | 已核实限制 |
| --- | --- |
| src/main/java/com/rpb/reservation/workforce/statutory/StatutoryCalculator.java:16 | 旧入口固定 2026、VERSION 为 SG-CPF-2026-OW-v1；带 config 的入口可用保存政策，但没有 AW 金额／年度上下文。 |
| 同文件:28 | 普通工资决定低工资档、CPF 基数只截 OW ceiling；totalWages 只用于 SDL，不参与 CPF 工资档选择。现有外籍 CPF=0、生日次月年龄档、SPR T1–T5 和月中取得 SPR 阻断均应保留兼容。 |
| statutory/StatutoryDtos.java:12 | Trace／Result 仅有单一 cpfWages、rateCode，无未截顶 TW、OW／AW 分基数、年度额度、原期／结算期和差额分解。 |
| statutory/StatutoryPolicies.java；StatutoryPolicyConfig.java:4 | 政策有 year／日期／version；schemaVersion=1，仅 OW ceiling、年龄／工资档、五种费率表、SDL、舍入和 CDAC，无年薪 ceiling、AW 能力版本、年度结算算法证据。准确文件名是 StatutoryPolicyConfig，不存在 StatutoryConfig.java。 |
| statutory/OfficialStatutoryTemplates.java:6 | 只提供 2026／2027 模板，文本明确月中 SPR、AW／AWS、跨期补缴须另行处理；不是有任意历史年度完整政策。 |
| statutory/StatutoryPolicyRepository.java:16 | resolve 选择覆盖整月且 enabled 的政策，缺少返回 null、重叠冲突；不能把当前政策当历史 fallback。 |
| payroll/PayCalculator.java:62；PayItemKinds.java | 所有收入只有 ow_allowance／ow_additional／ow_accommodation。ow_additional 名字有 additional，实际仍归 OW。无 AW、所属期或应付日期。 |
| payroll/PayrollCalculationService.java:40 | 调用 calculate(month, cpf, grossPay, grossPay, config)，所有 gross 都是 OW；CDAC 也直接取当批 gross，SDL 只在当批全公司归集取整。 |
| payroll/CdacPayroll.java；statutory/CdacRules.java | applicable 按传入 gross 取月档，override 使用该月人工金额；manual 项已由 PayCalculator 扣除，Decision.deduction 返回 0 以免再扣；exempt/not_applicable 返回 0；gross=0 特判 0。无同月跨批累计／已扣／实缴余额。 |
| payroll/PayrollDtos.java | Item 有 label／kind／amount／proration／itemId／source；Batch 只有单 month，Payment 是员工工资付款登记；没有 CPF remittance、原单更正关系或年度完整性。 |
| src/main/resources/db/migration/V091__workforce_payroll.sql:28 | 有 tenant/company/month 的非 voided 普通批次唯一索引；直接再建同月普通 batch 会冲突，不应删除该约束来获得补发能力。 |

旧 JSON、历史 policy revision、已发布工资和付款记录保持字节及语义兼容；不能把旧 ow_additional 全部迁移成 AW，也不能根据 label 包含“奖金”重分类历史工资。

## 2. 官方依据与直接实现约束

以下全部为官方原始资料，均于 **2026-10-30** 查验。链接旁的规则为概括；“设计推断”单独标注。没有采用搜索中的商业软件文章作为依据。

### 2.1 金额性质、AWS 和日期

- **OW** 要同时属于该月雇佣报酬、并在下月 14 日之前已到合同应付日；不满足才是 AW。分类看合同应付日，不因行政延迟的实际付款日而改变。年度绩效奖是 AW 的例子，名称不是判定条件。[CPF：OW 与 AW](https://www.cpf.gov.sg/service/article/what-are-ordinary-wages-ow-and-additional-wages-aw)
- AWS、奖金、可变报酬是否应付及 AWS 比例／折算来自合同、手册或集体协议，不默认给所有人一个月基本工资。MOM 也列出 1988-08-26 前未付 AWS 的雇主适用的一月上限条件；系统不得无公司事实就自动套用或自动豁免。[MOM：可变工资](https://www.mom.gov.sg/employment-practices/salary/variable-wage-components)、[MOM：AWS 资格与计算](https://www.mom.gov.sg/faq/salary/am-i-entitled-to-aws-and-how-is-it-calculated)
- **后来决定的追溯加薪**：例如 4 月才决定回溯 2–3 月，回溯额为 4 月 AW。**原本 2–3 月已应付但遗漏**：按原月 OW 补少缴，可能有迟缴利息。两者不可只用一个“补发”类型。[CPF：追溯加薪分类](https://www.cpf.gov.sg/service/article/are-retrospective-salary-increments-classified-as-ordinary-wages-ow-or-additional-wages-aw)
- 离职后才到期的奖金用其到期月；原应付、后来因疏漏才补付则仍用原应付月。不能统一归离职最后月，也不能统一归实际付款月。[CPF：离职员工奖金所属月](https://www.cpf.gov.sg/service/article/i-am-required-to-pay-cpf-contributions-for-a-bonus-to-an-ex-employee-would-cpf-contribution-for-the-bonus-be-contributed-for-his-last-month-of-employment-or-the-month-when-it-is-due-or-payable)

**设计推断：**一个事项至少区分 servicePeriod（取得报酬的工作期间）、decisionDate（形成新追溯权利时）、contractualPayableDate、contributionMonth、settlementMonth、actualWagePaymentDate。业务类别 bonus／aws／retroactive_increment 与法定分类 OW／AW／non_wage 分离；后端依据结构化事实验证分类及理由。未知应付日期不能通过随意选一个月变成有效审批内容。

### 2.2 年度 AW ceiling、估算和复核

- ceiling 按**每个员工、每个雇主、每个日历年**独立计算：102000 减该年受 CPF 约束的 OW 总额。2026 月 OW ceiling=8000；不是所有 AW 都按 8000 封顶，也不是用未截顶年 gross 去减。[CPF：AW ceiling](https://www.cpf.gov.sg/service/article/what-is-the-additional-wage-aw-ceiling)、[CPF：OW ceiling 历史](https://www.cpf.gov.sg/service/article/what-is-the-ordinary-wage-ow-ceiling-mbr)
- 每次 AW 到期时按本年度预计 OW 更新额度；年终或离职最后月（较早者）按实际 OW 复核。少缴随该结算月处理，超缴进入退款流程，不能静默抵未来员工工资。[CPF：年中 AW 计算与年末复核](https://www.cpf.gov.sg/service/article/how-do-i-calculate-cpf-contributions-on-additional-wages-aw-paid-before-the-end-of-the-year-last-month-of-employment)
- 本次另读官方 **2026 AW 示例**（21 页），支持年度归属按 AW due/payable date、按先后发生的 AW 分配额度、重入职继续同雇主年度累计、低工资档及历史费率复核。[CPF：2026 AW ceiling 示例](https://www.cpf.gov.sg/service/sfc/servlet.shepherd/document/download/069IW00000KUuZCYA1)

**设计推断：**年度上下文要有 12 个月 OW actual／forecast／known-zero／unknown 标记、OW 截顶值、全部 AW 事件及原已确认 CPF 责任。未来月份可依据当前有效批准工资和已知入离职日期生成可审阅预测；波动工时及未知历史不可估成 0。已知全年每月受 CPF OW 8000 时 AW ceiling=6000 是公式结果，不是系统固定 AWS 上限。现有 company 归属保持；各门店属于同一公司时不能分别获得年度额度。不同公司是否同一法定雇主不能猜，缺实际雇主映射时阻断相关计算，而非改写跨公司工资归属。

额度变化需重放本年度 AW 时间序列，保留每次消费／释放／重算来源。仅存一个“剩余额度”数字无法处理早期 AW 后补、离职再入职及年龄档变化。已冻结事件不改写；新复核版本引用它们并记录差额。

### 2.3 CPF 年龄、SPR、低工资档和舍入

- 2026 官方表包含 T1（公民／SPR 第三年及 F/F）、T2/T3（SPR 首／次年 G/G）、T4/T5（F/G）。必须按当期年龄、SPR 年次及批准选项选表；既有费率不能因有 AW 变成统一 37%。TW 档为 ≤50、>50–500、>500–750、>750；中档员工份额采用对应表的渐进公式。[CPF：2026 五张费率表](https://www.cpf.gov.sg/content/dam/web/employer/employer-obligations/documents/CPFcontributionratesfrom1Jan2026.pdf)
- **档位依据未截顶 OW+AW**。例如 OW=500、当月 AW 很高但 AW 剩余额度仅 100，仍按正常档对受约束基数算，不能因基数只有 600 而用渐进档；AW 额度用完也不意味着正常档 OW 退回 employer-only。[CPF：2026 AW 示例，第 21 页](https://www.cpf.gov.sg/service/sfc/servlet.shepherd/document/download/069IW00000KUuZCYA1)
- 同月 OW 与 AW 贡献先合计未舍入数，再将 CPF 总额四舍五入到整数、员工份额向下到整数、雇主=总额−员工。不要分别对每奖金行或每补发批次取整后相加。[CPF：2026 费率表，计算步骤](https://www.cpf.gov.sg/content/dam/web/employer/employer-obligations/documents/CPFcontributionratesfrom1Jan2026.pdf)
- 生日后的次月改变年龄档。AW 年度复核补缴仍用该笔 AW 原所属月份适用费率，不用年末年龄统一重算。[CPF：年龄变动与 AW 复核](https://www.cpf.gov.sg/service/article/how-do-i-calculate-cpf-contributions-on-additional-wages-aw-when-there-is-a-change-in-age-group-during-the-year)
- SPR 首年从生效日开始，首／次周年的次月切换年次；月中获 SPR 的 OW 需按适用期间处理，AW 看 payable date 是否已取得 SPR。现有整月入口对此阻断是能力限制，不是“当月都不缴”的法律规则。[CPF：新获 SPR 的 OW／AW](https://www.cpf.gov.sg/service/article/do-i-need-to-pay-cpf-contributions-for-my-foreign-employee-who-has-recently-obtained-singapore-permanent-residence-status)

**设计推断与必要限制：**新 V2 纯计算请求明确 rawOW、rawAW、cpfOWBase、cpfAWBase、bandWages、rateSegments、policyRevision、roundingScope。新普通／补发收入都走按月合并内核；纯 OW 的旧结果回归必须等价。AW 年度复核保留原 AW 分段费率、结算期及舍入范围。附录 B 已用官方计算器核实 T1 正常档跨原月、跨年龄及分币的逐月目标差额，允许直接据此实现，不再因“跨原月有分币”泛化阻断。低工资渐进档结合 AW ceiling、月中身份分段等仍须按各自完整公式／证据判断，不能用此次正常档结果代替；计算器对低工资月份不适用也不表示已经有明确官方公式的组合不能实现。[CPF：AW calculator 使用范围](https://www.cpf.gov.sg/employer/tools-and-services/calculators/additional-wage-ceiling-calculator/)

### 2.4 补缴、付款、更正、员工份额和利息

- CPF 未缴／少缴需补缴；已多缴是退款／调整，工资申报数字错误是独立改正事项，不能把所有差异统一成负付款。[CPF：更正与补缴](https://www.cpf.gov.sg/employer/compliance-and-rectifications)
- 原缴纳月／年、CSN 或员工填错属于 adjustment。[CPF：调整 CPF 缴纳](https://www.cpf.gov.sg/employer/compliance-and-rectifications/applying-for-adjustment-of-cpf-contributions)
- 雇主错误导致已经支付工资后遗漏扣员工 CPF，不能从随后月份工资扣回。非雇主错误也有已缴、书面同意送交 CPF／CPF 书面许可、六个月等条件；单纯系统审批不是这些条件。[CPF：收回员工 CPF 份额](https://www.cpf.gov.sg/service/article/how-do-i-recover-the-employee-s-share-of-cpf-contributions)
- 上述普通遗漏不得覆盖法定 AW 重估追加缴费：First Schedule 第4段与 Act s7(9)另有追回依据，期限为追加缴费应缴年度结束后六个月；不能把所有年度 AW 员工差额标为法律禁止收回。产品仍不自动扣工资，须识别责任成因、适用规则、已缴与已扣事实及期限；详见 B.5。[CPF Act s7](https://sso.agc.gov.sg/Act/CPFA1953?ProvIds=pr7-)
- CPF 月末到期；翌月 14 日（假日按官方规则延）为执法节点，迟缴利息从到期后第一天计，月率 1.5%、最低 5 元；官方还给出日数比例及最终向下取整示例。不能把“14 日”写成计息起点。[CPF：迟缴利息](https://www.cpf.gov.sg/service/article/how-does-cpf-board-calculate-interest-on-late-payment)

**设计裁定：**工资付款登记、CPF／SDL 缴纳登记各自独立。原工资已付 100%，仅欠 CPF 时不得再生成一笔向员工付工资的应付额；原工资已算但未付时优先继续原工资付款，而不是补发同一收入。补发“原未入账工资差额”和“法定责任差额”必须是两组字段。employeeCpfLiabilityDelta 不等于 recoverableFromEmployee；默认不自动向以后工资追扣，需合法凭据才能进入独立处理。利息是雇主费用，分离本金、估算值、官方核定值及支付登记；本轮可支持官方核定金额录入与对账，不必自造跨月／部分支付复杂计息器，更不从员工净额扣除。

### 2.5 SDL 及现有 CDAC 的交叉影响

- SDL 对新加坡工作的员工含外籍，按个人月总工资 0.25%，最低 2、最高 11.25；个人精度保留后合计公司数，再向下取整数。[CPF：SDL](https://www.cpf.gov.sg/employer/employer-obligations/skills-development-levy)
- 官方 SDL FAQ 明确奖金／津贴计入该月工资；例子是工资加奖金一起算，不能对奖金单另收一次最低额。[官方 SDL FAQ，D.2](https://file.go.gov.sg/sdl-generic-faq.pdf)
- 当前 CPF 页面指 SDL 补缴经 GoBusiness 的 SDL 功能处理。系统只保存相应外部回执，不把 CPF 工资付款登记当 SDL 已完成。[CPF：SDL 补缴，2026-10-28 更新](https://www.cpf.gov.sg/service/article/how-do-i-make-back-payments-for-skills-development-levy-sdl)
- 现有 CDAC 以月总工资档计，补发同月不得重复扣完整月额；遵从已保存 CDAC 政策及 opt-out／override 证据，做该月重算差额。[CPF：SHG 月工资与缴纳](https://www.cpf.gov.sg/employer/employer-obligations/contributions-to-self-help-groups)
- CPF 官方补充申报指南要求先合计同月 OW+AW，再扣既有缴纳。其 CDAC 示例 5000 工资加 2500 奖金，目标月额 2 元，减已缴 1.50 后仅补 0.50；SDL 同样减既缴额，已达个人最高额时新增奖金 SDL 差额为 0。[CPF：Additional Submission 指南，第 10、13–14 页](https://www.cpf.gov.sg/content/dam/web/employer/making-cpf-contributions/documents/Howtoperformadditionalsubmissionusingeedb.pdf)
- CDAC 官方说明离职后下一月发奖金仍需按收到工资月份计算；它不是把所有奖金摊回服务月。[CDAC：雇主 FAQ，第 17 问](https://www.cdac.org.sg/cdac-funding-info-information-for-employers)
- **历史 CDAC 漏缴与 CPF 规则不同：**官方现行指引只要求补“发现遗漏／少缴当年 1 月起”的 CDAC／ECF／SINDA，早于该年度的漏缴不要求补。发现日期必须有证据；不能把所有历史工资调整都自动生成历史 CDAC 待缴。[CPF：SHG 漏缴／少缴指引，2026-09-22 更新](https://www.cpf.gov.sg/service/article/what-should-i-do-if-i-have-omitted-or-under-paid-self-help-group-shg-contributions)

**设计推断：**SDL 同公司同月累计责任的新总额减已入账责任，既考虑个人上下限又考虑公司整数舍入，不简单将“新批次 SDL”累加。CPF 单纯补缴本金或 AW 年度额度复核没有新增工资时，不再产生 SDL／CDAC。追溯事项如 SDL 归属依据不完整，须单列 unresolved，不自动认为等于 CPF 月；常规原期漏计与当期新奖金分清。现有非自动 CDAC 的人工核准口径保持，不能未经批准把固定选择改成自动。

**CDAC 实现补充：**自动模式保留现有 2000/3500/5000/7500 上界和 0.50/1/1.50/2/3 档位（由有效政策提供，不写死在补发服务）；基数为该 CDAC 月全部工资，不使用 CPF 截顶基数或单笔奖金。分开计算“目标月责任−已入账责任”的新账务差额、“目标月应扣−已实际扣款”的可扣差额和“待缴责任−已缴”的汇缴余额。manual 固定项目不可按补发重新应用一次；override 必须绑定整月有效批准版本，无证据不自动切回 automatic。跨年补缴按 discoveryDate 适用 SHG 指引，且保留历史原扣款／已缴事实，不能为了当前不要求补而删除历史记录。此项变更只扩展现有 CDAC，不擅自加入其他 SHG 业务。

## 3. 支持的业务命令与账务边界

| 场景 | 工资收入及现金 | CPF 责任／所属月 | 处理 |
| --- | --- | --- | --- |
| 当前奖金／AWS | 新批准收入，只出现一次 | 依据事实分 OW／AW；AW 消费当年额度 | 可在普通未冻结工资整合，或独立 supplemental 批次 |
| 后来决定追溯加薪 | 新的正差额，引用服务期间 | 决定产生应付权利的当期 AW | 不改旧工资单；与当期收入合并法定计算 |
| 原期本来应付但漏计 | 原期收入差额，引用原单及项目 | 原期 OW／原期 AW 依原性质重算 | 独立 correction，读取原期政策及全年上下文 |
| 原工资已算且没付 | 没有新增收入 | 原计算责任已存在 | 继续原单付款／补缴登记，禁止复制工资 |
| CPF 原已算但没缴／少缴 | 工资收入差额=0、员工应付=0 | 同一责任余额 | 独立 statutory-settlement 及 remittance 分配 |
| AW 额度年末／离职复核 | 无新增奖金收入 | 原 AW 分项费率、结算期责任调整 | reconciliation，不能再次计 gross／SDL |
| 原已多付／多缴 | 记录负差额／待退款 | 保留本金及责任历史 | 不自动追回、不负工资付款、不自动抵未来工资 |

批次生命周期沿用 draft→calculated→confirmed→published，审批申请生命周期独立。发布原批准差额时冻结来源。未批准新增经济内容不得进入计算，首次模板原样采用免审条款不适用于员工已有工资上的新增奖金／补发。补发只改变获批的必要经济字段，不顺便改变员工长期工资标准。

## 4. 可实现模块与迁移方案

以下为建议实现接口名，尚未成为现有 API。

1. `payroll/supplemental`：事项、补发／更正批次、原单引用及员工差额工资单；不持有自己的合同计薪公式。`payroll/governance` 的批准 revision 承载这些事项的精确 payload，统一写守卫核对。
2. `statutory/annual`：年度上下文读取、OW 预测证据、AW 时间序列分配及 reconciliation；纯计算与 JDBC 分离，输出所依赖所有年月的 fingerprint。
3. `statutory/settlement`：法定责任账与 CPF／SDL 登记、分项分配、冲正、余额及回执；不得调用工资 PaymentService 冒充已缴。
4. `StatutoryCalculator` 新能力版本负责明确 OW／AW 输入和分段计算，旧 OW 入口保持；`PayrollSources` 捕获同月累积＋年度事实，`PayrollCalculationService` 统一汇总净额与贡献差额，`PayrollIncremental` 将年度账／预测／审批／政策 revision 加入缓存键。

建议新增表而不是改写旧 batch 的 company/month 唯一键：

| 新实体 | 最小约束与作用 |
| --- | --- |
| supplemental_batches／items／snapshots | tenant/company/id 唯一；员工和原 batch/item 必须同租户同公司；kind、settlementMonth、originMonth、approvalRevision、version；冻结不可覆盖 |
| wage_events | 一份经济收入一个稳定 eventId；来源 normal/supplemental/external-opening + sourceId/version/itemId 唯一；原事件与更正链明确；同事件不能既入普通又入补发 |
| statutory_year_contexts／month_facts／forecast_revisions | tenant/company(employee-employer mapping)/employee/year 键；12 月 completeness、非雇佣零值证据、每月原始与 CPF 工资；预测和实际分离 |
| aw_assessments／allocations | revision、年度上下文 hash、按原 AW 的额度及费率分段、原计算责任、目标责任、delta、settlementMonth |
| statutory_liability_entries | CPF employee/employer、SDL、CDAC、interest 独立分项；原责任与 delta 引用；事件／assessment 唯一消费 |
| statutory_remittances／allocations | 实缴日期、external reference、CPF contributionMonth、凭据、分项金额；正额登记、可审计冲正；实际支付不由系统发起 |

迁移编号在落地前重新查最大值，不能预占 V109 等父任务已使用序号。所有外键含 tenant/company；不可只靠 UUID 和应用层 where。旧工资快照只读进入投影；如果需物化 ledger，以原 batch/employee/item 的稳定引用幂等映射，不重新写 JSON。已有冻结工资代表工资／责任证据，不代表真实 CPF 已缴；现有工资付款记录同样不证明 CPF 已缴。历史缺失时建立“待补事实”而非默认零 opening balance。

并发：确认补发前必须串行保护同公司员工年度 AW 额度；SDL 最终确认还要串行保护公司月总额，避免两个员工补发各自消费同一个公司舍入差。保持原 batch→source 排他确认锁的顺序；批准仅写稳定有效配置、不得在 source 共享锁中更新 batch。新年度／月度锁必须在实施前定义统一序并用并发测试证明，不能混入 source→batch 反向路径。确认提交时重新核对全年／同月 fingerprint，变化返回 stale／409 并保留提交回执。

## 5. API 与必要输入

保持 `/api/v1/stores/{storeId}/workforce`、PayrollAccess 完整工资权限、AppGate、总部审批权限及当前本人限制；跨店不能扩大公司员工可见面。

建议契约：`supplemental-batches` 草稿／预览／calculate／confirm／publish；`statutory-year-contexts/{companyId}/{employeeId}/{year}` 只读准备度与已审核数据 revision；`aw-reconciliations` 预览／生成差额；`statutory-remittances` 登记／冲正／分配读取。路径在正式 API 文档中定稿，controller 不计算。

每一请求／响应至少包含：

- actor 上下文派生 tenant；companyId、employeeId、commandId、expectedVersion、currency=SGD。
- eventId、businessType、amount（decimal string）、classification、reasonCode／evidenceReference、serviceFrom/To、decisionDate（追溯加薪必填）、contractualPayableDate、contributionMonth、settlementMonth；actualPaymentDate 属付款登记，不能用它反推分类。
- 原 batchId／line／item／snapshotHash、原已入账工资／贡献、历史更正链；外部 opening 必须有来源和覆盖期间，不能只给一个无来源总数。
- AWS 手工已核定金额可走批准；如要求自动推算，另需合同基数／比例／服务期间／舍入等明确规则，不擅自补一个月标准。
- 原期 CPF 身份／生日／SPR 生效日／选项生效证据、每个需要月份的 policyId/version/year/capability；年度 OW 事实和预测版本、全部 AW 事件、现有责任及实缴分别保存。
- response 分开展示 grossDelta、employeeCashPayable、employeeCpfLiabilityDelta、employeeCpfRecoverable、employerCpfDelta、sdlDelta、cdacDelta、interest、refundRequired、sourceFingerprint、missingEvidence、issues；无法确定的金额 null，不能 0 冒充无需补缴。
- 结果保存 rawOW/rawAW/bandWages、受 CPF OW/AW、AW ceiling 前后及已消费、费率分段、原额／目标额／差额、舍入 trace、政策／审批证据。员工工资单只给本人必要金额／原期链接，内部审批说明与证件／账号原资料继续脱敏。

建议明确阻断码：`WAGE_CLASSIFICATION_EVIDENCE_MISSING`、`ANNUAL_WAGES_INCOMPLETE`、`AW_FORECAST_MISSING`、`ORIGINAL_PERIOD_POLICY_MISSING`、`STATUTORY_CAPABILITY_UNSUPPORTED`、`ORIGINAL_LIABILITY_AMBIGUOUS`、`EMPLOYEE_CPF_RECOVERY_NOT_ALLOWED`、`AW_CONTEXT_STALE`。缺政策不要统一误报为 CPF_PROFILE_INVALID；与真实零金额区分。

## 6. 历史年度和不确定性处理

1. **历史政策缺失即阻断受影响行／批次确认。**补 2025 OW 不能拿 2026 的 8000 ceiling；现有模板不提供 2025，不临时造一个历史表。现有 StatutoryPolicyService 已允许总部保存 2000–2100 年范围的自定义政策及整月有效期，直接复用这一入口加入经官方年度资料核对的完整版本；保存来源、有效范围及能力版本后再算，无需新增“全历史内置模板”前提。原有快照足够证明旧“已入账责任”，不一定足够证明应当正确的新“目标责任”。
2. **原期身份事实缺失即阻断。**不能用员工当前 SPR／公民状态和今天年龄重建过去责任；原期事实变更需保留更正证据，不能倒写历史工资档案。
3. **年度数据缺失即阻断。**同公司外部工资／既往 AW、年初导入、同年离职再入职均需合并。不同雇主数据不可合并，无法确定法定雇主不能猜。缺失月与有证据零值分别编码。
4. **官方资料存在可见排版／数值不一致。**本次打开并截图确认 2026 AW 示例第 2 页 total 行仍有与月行／6000 公式不一致的旧总数；其他个别示例也出现正文与表内月份／算术不一致。实现 oracle 应采用清晰官方规则、费率表和人工复算一致的例子；有矛盾的具体例子不能直接复制为测试期望。此不确定性不影响已明确的 OW/AW 分类、年度公式或费率原则。
5. SDL 当前服务门户抓取 502；基础费率以 CPF 当前页面、奖金口径以政府域名 SDL FAQ 交叉支持。特殊跨期 SDL 月份归属／特殊身份与低工资 AW 复核如证据不足，隔离组合并继续其余已核实路径，而不是扩展“猜测支持”。

**目前没有必须先问用户才可开始开发的新增业务选择。**已授权的补发／补缴可按上述通用机制和缺数据阻断实现。仅当具体实际事项缺合同应付日、AWS 合同规则、外部年度工资／实缴、法定雇主映射或合法追回凭据时，需向持有事实的人补充信息；这属于运行期资料要求，不是另加设计审批。若要实际退款／追回、自动申报／真实转账，则另有授权范围，本文件不启动。

## 7. 增量实施阶段和验收清单

先完成已授权治理四阶段；以下细项落入父任务阶段 5（F1/F2，奖金与 AW）和阶段 6（F3/F4，跨期补发补缴及综合验收），最终统一验收。任何未实现必要项不得用“已全面支持 AW／跨期补缴”收尾。

### F1 分类、数据契约与政策能力

- 固定上述事件、日期、账务责任／现金分离和 API；新增不可变差额实体及年度完整性表；挂接治理批准 proof。
- versioned StatutoryPolicyConfig V2 及新计算输入；旧 V1 只能 OW，无 AW 能力时明确阻断；不回写已有 policy/config。
- 测试：旧 ow_additional 仍 OW；改 label 不改分类；相同服务期但不同决定／应付日期分别进入原期 OW／当期 AW；日期伪造／无证据拒绝；跨公司／跨租户 FK；旧 JSON 反序列化；未审批金额不可进入计算。

### F2 OW+AW 与年度计算

- 实现同月全收入聚合、OW/AW 双 ceiling、年度事件顺序、预计 OW 证据和年末／离职复核；扩缓存与确认来源核对。
- 费率表测试覆盖 T1–T5、各年龄档、生日／SPR 周年次月、F/G／F/F 依据；月中身份场景支持到何种程度据实际证据验收，未支持组合必须明示。
- 阈值测试覆盖 TW=50、500、750 边界及邻近分值；OW 低于阈值但 AW 推高；AW 截顶至 100 或 0 后仍按 raw TW 档；OW>8000 且 AW>0 不整体截 8000。
- 舍入测试验证 OW/AW 先合计再取整、员工 floor、雇主相减，拆成两批与一次结算等价；不可对两批各扣一次整月份额。
- 年度测试：全月 capped OW→6000、薪资调整重估、预测变化退款提示、缺历史月阻断／有证据零月、离职、同年重入职、跨年奖金 dueDate、不同公司独立、两店同公司共享额度、两份并发 AW 不超耗。
- 年度复核测试保留原 AW 费率分段；原月／结算月、舍入方式与官方明确例子一致；有矛盾示例不作 oracle。

### F3 差额批次与法定缴纳账

- 实现原已发布工资引用、原期经济差额、当期追溯 AW、纯 CPF 少缴、年度 AW 复核四种可追踪路径；负差额仅进入待处理退款／追回，不自动净抵。
- 新法定责任与 remittance 登记支持部分支付、分配、同命令重试、冲正和外部凭据；本金／利息分开。
- 测试：原已付／未付／部分付情况下不重复支付收入；原 CPF 已扣未缴不再扣员工；原雇主错误漏扣不得扣未来工资；合法追收证据缺失阻断；原工资／付款／JSON hash 保持；重复更正链不双计；命令冲突及响应丢失幂等恢复；确认并发来源变 stale。
- SDL／CDAC 测试：普通+补发同月上下限只应用一次、公司 SDL 总数 floor 差额、多个员工并发补发公司合计一致、纯 CPF 差额不新增 SDL/CDAC、opt-out／override 保留；CDAC 5000+2500 目标 2、已扣/缴 1.50 则对应差额 0.50；同档新增奖金差额 0；manual 不重复；发现当年／往年漏缴不同处理；原已扣未缴不能再次从员工扣。

### F4 工作台、员工展示和本地综合验收

- 加年度完整性／缺资料引导、AW 剩余与复核到期提示、差额批准前后预览、原单链、工资与法定缴纳两套余额、利息核定／退款待办；不能用“已付款”表示 CPF 已提交。
- 工资单本人脱敏展示原期／新增收入／法定差额，避免把补发差额混成第二份完整月工资；审批状态、生效状态、付款状态分开展示。
- 运行真实模块行为测试与已有 OW 回归；再按项目 pointer 在隔离 schema 验证迁移、锁顺序、FK、幂等和冻结不变；不使用系统 5432。此次设计研究没有执行这些验证。
- 本轮仍 local-only，不 commit/push/deploy；没有真实银行、CPF／SDL 平台申报、实际退款或员工追缴验收证据。

## 8. Builder-check 最终裁定

可以按现有授权实施，无需先让用户选择技术架构。父计划阶段 5／6 应纳入 F1–F4：年度事实完整性、TW 低工资档、年终／离职 AW 复核、原期政策与身份、SDL/CDAC 同月累计及 CDAC 跨年补缴规则、工资与 CPF 登记分离、禁止双计和禁止自动追回是必要行为，不是可选优化。主方案第 13.10 已记录追加授权，旧文“不实施差额批次”仅属于旧授权；本研究不直接改主方案。

## 附录 A：阶段 5／6 可用的人工复算 oracle

补充查验日期：2026-10-30。以下是依官方公式**人工构造并复算的测试数据**，不是生产员工数据，也不是已运行测试结果。所有金额为 SGD 十进制；除 A.7 明示年龄变动外，CPF 场景均是 2026 年 T1、55 岁及以下公民、整月身份无变化。每个 fixture 必须提供所列年度／原单／已缴证据，不能只硬塞 expected 数字。CPF 数例仅隔离验证 CPF，SDL/CDAC 不隐含为 0，分别按对应例验证。

### A.1 全年 OW=8000：AW 年度额度为 6000

输入：同一雇主／员工 2026 年十二个月各有 OW=8000 的已核实完整事实；当年此前 AW=0；12 月新 AW=10000、分类及应付日期已核实。计算：

- 全年受 CPF OW = 8000×12 = **96000**；AW ceiling = 102000−96000 = **6000**。
- 12 月 raw TW = 8000+10000 = **18000**，正常档；受 CPF OW=8000、AW=6000，总基数 **14000**，剩余 4000 AW 不吸引 CPF。
- 当月 CPF 总额 = 14000×37% = **5180**；员工=14000×20%=**2800**；雇主=5180−2800=**2380**。
- 若 12 月 OW 责任已记账为总额2960／员工1600／雇主1360，则新责任差额为**2220／1200／1020**。若该 OW 本金已缴2960，待缴本金为2220；实缴证据未知则待缴为 null，不自动用2220代替实际余额。

回归断言：不能整体把18000截成8000；不能把10000全部当AW受约束工资；不能将6000作为所有员工固定年度额度。依据：[AW ceiling](https://www.cpf.gov.sg/service/article/what-is-the-additional-wage-aw-ceiling)、[2026 T1费率与舍入](https://www.cpf.gov.sg/content/dam/web/employer/employer-obligations/documents/CPFcontributionratesfrom1Jan2026.pdf)。本例完全由规则复算，未使用 AW 示例 PDF 的错误 total 行。

### A.2 OW=500、AW=1000：AW 余额仅100或0也不能退回低工资档

两组独立 fixture：全年 OW 每月500，全年受 CPF OW=6000、AW ceiling=96000；12 月本次之前的有效 AW 消费分别为95900和96000，所有历史事件已对账。本次 raw OW=500、raw AW=1000，**raw TW=1500>750**。年度上下文及本月其他 AW 事件完整，下面“既有 OW-only”是原本月仅 OW 计算责任，不是本年全部已缴额。

| 项目 | 余额100 | 余额0 |
| --- | ---: | ---: |
| 本次受 CPF AW | 100 | 0 |
| 本月受 CPF OW+AW | 600 | 500 |
| 月目标 CPF 总额（37%） | 222 | 185 |
| 月目标员工份额（20%） | 120 | 100 |
| 月目标雇主份额 | 102 | 85 |
| 既有 OW-only 总额／员工／雇主 | 85／0／85 | 85／0／85 |
| 新责任差额：总额／员工／雇主 | **137／120／17** | **100／100／0** |

“余额0→本次全部CPF差额0”是错误结论：AW 虽不增加缴费基数，仍通过 raw TW 改变 OW 的工资档。该差额是责任计算结果，能否从员工现金中扣除仍须根据付款时点／合法扣缴条件另判，不因本表自动授权跨月追扣。依据：[2026 AW 示例第21页对未截顶TW的明确说明](https://www.cpf.gov.sg/service/sfc/servlet.shepherd/document/download/069IW00000KUuZCYA1)、[T1低工资档与正常档](https://www.cpf.gov.sg/content/dam/web/employer/employer-obligations/documents/CPFcontributionratesfrom1Jan2026.pdf)。

### A.3 先 OW 后同月 AW：合并舍入必须先算目标再减旧额

输入：本月 OW=1004，随后同月应付 AW=101；完整年度证据表明 AW 剩余额度≥101；无其他本月收入。此处两次工资都属于同一月的正常付款，排除雇主漏扣后跨月追回情形。

| 项目 | 先前 OW-only | 加AW后的完整月目标 | 本次补充责任差额 |
| --- | ---: | ---: | ---: |
| 总额未舍入 | 1004×0.37=371.48 | 1105×0.37=408.85 | 不单独对差额原始值舍入 |
| CPF总额（HALF_UP整数） | 371 | 409 | **38** |
| 员工未舍入 | 1004×0.20=200.8 | 1105×0.20=221 | — |
| 员工份额（FLOOR整数） | 200 | 221 | **21** |
| 雇主份额（总额−员工） | 171 | 188 | **17** |

错误做法对101单独套正常费率后取整，会得到总额37、员工20，再加旧额成为408／220，均少1。若同月旧员工份额200已经合法扣除，则本次补充员工份额是21；只隔离 CPF 时，新工资现金=101−21=80，旧现金804加80等于整月净额884。此80不是包含CDAC等后的最终工资净额。依据：[2026表计算步骤4](https://www.cpf.gov.sg/content/dam/web/employer/employer-obligations/documents/CPFcontributionratesfrom1Jan2026.pdf)、[官方补充申报按整月目标减旧缴纳](https://www.cpf.gov.sg/content/dam/web/employer/making-cpf-contributions/documents/Howtoperformadditionalsubmissionusingeedb.pdf)。

### A.4 SDL：个人上下限一次、公司取整一次，差额不能另取整

输入：同一公司同月只有以下三名适用SDL员工，旧工资及SDL责任完整，公司旧SDL本金15已缴且有回执；新增奖金同月应付，其他工资不变。

| 员工 | 旧月工资 | 旧个人SDL | 新增工资 | 新完整月工资 | 新个人SDL | 个人精度差额 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| A | 500 | 2（最低额） | 100 | 600 | 2（最低额） | 0 |
| B | 4400 | 11 | 200 | 4600 | 11.25（最高额） | 0.25 |
| C | 1000 | 2.50 | 200 | 1200 | 3 | 0.50 |
| 合计 | — | **15.50** | — | — | **16.25** | **0.75** |

旧公司责任=floor(15.50)=**15**；新公司责任=floor(16.25)=**16**；新增公司责任及本例待缴=16−15=**1**。不能 floor(0.75) 得0，也不能给 A 的额外100再收2元最低额。若 B 后续再加同月1000，B仍11.25；没有别的变化时新增SDL责任为0。个人归因0.75与公司支付差额1之间的0.25是公司取整变化，应存明确 rounding reconciliation，不能强塞给任一员工造成个人上限超额。

依据：[SDL个人规则与公司汇总向下取整](https://www.cpf.gov.sg/employer/employer-obligations/skills-development-levy)、[同月补充SDL减原额](https://www.cpf.gov.sg/content/dam/web/employer/making-cpf-contributions/documents/Howtoperformadditionalsubmissionusingeedb.pdf)。

### A.5 CDAC：5000+2500只新增0.50，已扣与已缴分开

输入：applicable＋automatic，有效2026政策，无override、无manual项目；同一CDAC月旧工资5000、旧CDAC责任1.50，员工已扣1.50，agency实缴1.50都有证据；新奖金2500。月工资=7500，目标CDAC=**2**；新责任=2−1.50=**0.50**、本次应扣差额0.50、待缴差额0.50。

变体：旧已扣1.50但agency已缴证据明确为0，则新增责任仍0.50、员工新增扣款仍0.50，但**月待缴余额为2**。不能向员工再扣2，也不能把仍未缴的旧1.50忽略。已扣或已缴未知时对应余额为null，不把unknown当0。依据：[CPF官方CDAC补充申报同例](https://www.cpf.gov.sg/content/dam/web/employer/making-cpf-contributions/documents/Howtoperformadditionalsubmissionusingeedb.pdf)、[SHG月档](https://www.cpf.gov.sg/employer/employer-obligations/contributions-to-self-help-groups)。

### A.6 原雇主漏扣与原已扣未缴：工资现金都不能再生成一遍

共同原期事实：工资gross=1000，原月OW合法完整，T1正常档，应有CPF总额370／员工200／雇主170。以下只是CPF本金分账测试；SDL/CDAC有各自记录，利息未获核定时为null，不包含在370中。两个场景均没有新增工资收入。

| 账目／动作 | 雇主错误漏扣、工资1000已付 | 已扣200、净工资800已付，但CPF未汇缴 |
| --- | ---: | ---: |
| 原工资gross／真实已付现金 | 1000／1000 | 1000／800 |
| 原已入账CPF责任（fixture明确） | 0 | 370（员工200＋雇主170） |
| 原实际员工扣款 | 0 | 200 |
| CPF实际已缴（有未缴证据） | 0 | 0 |
| 正确累计CPF责任 | 370 | 370 |
| 本次新增责任delta | **370**（员工分项200＋雇主分项170） | **0** |
| 当前CPF本金待缴 | **370** | **370** |
| 新工资gross／向员工付款 | **0／0** | **0／0** |
| 可自动从以后工资扣回 | **0，不允许** | **0，已经扣过** |

第一个场景的“员工分项200”是CPF责任构成，不是对员工的应收款。应另标 unrecovered employee share=200／recovery prohibited，不能改写原单为净额800、制造员工欠200，或自动加扣未来工资。该fixture不授权生成新的gross-up合同报酬。第二个场景只结清已有370责任，不新增370费用／责任，也不重复扣200。登记付款370及其分配后，待缴余额才成为0；登记本身不代表系统发起真实转账。若缺未缴证据，待缴余额未知，应阻断待缴确认但保留可核实的责任计算。

依据：[CPF员工份额收回限制](https://www.cpf.gov.sg/service/article/how-do-i-recover-the-employee-s-share-of-cpf-contributions)、[CPF欠缴补缴](https://www.cpf.gov.sg/employer/compliance-and-rectifications)。利息必须保留 `unknown / estimated / assessed / not_applicable-with-evidence` 等可区分状态；没有付款日期、计息期间或官方核定，不能以“本例本金370”推出总欠款370。

### A.7 年度复核跨原月份／年龄：已核实与尚未核实的界线

**可以直接断言的整数数例（无舍入歧义）：**员工2026年6月满55岁；1–6月OW各8000、7–12月各5000，年OW=78000、最终AW ceiling=24000。历史AW仅2月20000、8月4000。已有责任证据显示仅2月6000 AW 已入账并缴CPF（总额2220／员工1200／雇主1020），其余AW尚未计入；所有OW责任及缴纳已正确处理。这里是输入一个需要复核的历史状态，并不表示允许已知OW变化后延迟复核。

最终AW额度按事件顺序覆盖2月20000、8月4000。因此新受约束部分是2月剩余14000和8月4000，分别用原期费率：

| 原AW月份 | 新受约束AW | 原期总／员工费率 | 新总额 | 新员工份额 | 新雇主份额 |
| --- | ---: | --- | ---: | ---: | ---: |
| 2月（55岁及以下） | 14000 | 37%／20% | 5180 | 2800 | 2380 |
| 8月（55岁以上至60岁） | 4000 | 34%／18% | 1360 | 720 | 640 |
| 新增合计 | 18000 | 分段，不能统一年末费率 | **6540** | **3520** | **3020** |

最终AW累计责任=原2220+新6540=**8760**，员工4720、雇主4040；不把新增18000再记一次奖金gross，不新增SDL/CDAC工资基数，不自动从年末员工工资收回3520。2月、8月是费率／来源月份，12月是本fixture的复核结算月；这两个字段不能覆盖成同一个值。依据：[原AW月份费率规则](https://www.cpf.gov.sg/service/article/how-do-i-calculate-cpf-contributions-on-additional-wages-aw-when-there-is-a-change-in-age-group-during-the-year)、[官方2026示例第13页关于优先覆盖早期AW的说明](https://www.cpf.gov.sg/service/sfc/servlet.shepherd/document/download/069IW00000KUuZCYA1)。本表是独立构造整数例，不复制PDF错误总数。

**年度复核舍入的核实程度：**

1. 已确认：原AW费率要保留；新额度优先覆盖较早AW；同月普通OW+AW先合计后舍入；CPF总额HALF_UP、员工FLOOR、雇主为差额。另有官方2026-09-18 FAQ明确 AW ceiling calculator 在**月层面**对TW取整，与自动年度退款按全年总缴纳计算可能差几元。[CPF：AW calculator与年度自动退款为何不同](https://www.cpf.gov.sg/service/article/why-does-the-excess-cpf-from-the-additional-wage-ceiling-calculator-differ-from-the-automatic-refund-and-how-do-I-calculate-my-employee-s-share)
2. 因此可确定反例：不得把多个原月AW合并后统一套年末年龄；不得把年度总工资一次乘费率取整；不得用年度自动退款的分配算法替代AW月度责任复算。
3. **后续核实已解决原疑点：**附录 B 保存了公开官方计算器的分币跨年龄逐月责任、原已缴不足、总额为零但员工／雇主反向差额等完整请求／响应；SSO 官方搜索正文也取得 First Schedule 与 s7(9)相关条款。原“跨原月分币尾差尚无证据”的判断由 B 取代，不能继续作为这类正常档组合的阻断理由。
4. **可实现规则：**逐原工资月重建目标TW／AW资格及原月费率，算目标整数总额、员工份额、雇主份额，分别减该原月已确认整数责任；结算月只汇总这些整数差额，不对合计额再乘费率或再舍入。已缴差额另减实际汇缴证据。准确的数例和剩余边界见 B.2–B.4。
5. A.7整数例和 B 的分币例均可作 oracle；这仍是官方研究及外部只读试算证据，不是 RPB 实现已经验收。缺原月事实、原政策、旧责任或实缴证据仍按缺项阻断对应动作。`AW_RECON_ROUNDING_EVIDENCE_MISSING` 仅可用于确实缺少规则证据的特殊组合，不可用于所有跨月／跨龄／带分币的年度复核。

本附录只增加可审阅的公式／期望数据和证据边界，没有修改测试文件、执行业务代码或连接数据库。

## 附录 B：公开官方计算器补证——跨原月分币复核与结算

查验日期：2026-10-30；主要响应保存于 UTC 13:28:15–13:28:16（新加坡21:28），第五组具体时间见JSON。**结论：本次验证范围内，逐原月目标整数责任减旧整数责任、再在结算月按分项相加已有明确官方计算结果支持，可以实现，不需要另设永久阻断或额外审批节点。** 年度自动退款、原月责任修订和员工现金扣回仍是三个不同事项。

### B.1 可复现路径、原始证据与适用范围

官方入口：[Additional Wage ceiling calculator](https://www.cpf.gov.sg/employer/tools-and-services/calculators/additional-wage-ceiling-calculator/)。该页公开加载的 [clientlib-base JavaScript](https://www.cpf.gov.sg/etc.clientlibs/web-forms/clientlibs/clientlib-base.min.8ea2bb4b1a0f746f17b60d4fcaee93d6.js) 中 `ANNUAL_WAGE_CEILING_SERVICE` v1.1.0、`isMock=false` 指向 `POST https://www.cpf.gov.sg/bin/web/forms/calculator/additionalWageCeiling`。公开代码提供请求／响应映射；实际费率计算在服务器端，**没有声称拿到了服务器算法源码**。

证据文件：[完整合成请求及官方JSON响应](../../development/evidence/2026-10-30-cpf-aw-calculator-oracles.json)。包含5组请求、响应、HTTP状态、时间及公开路径；均为HTTP200、`success=true`、`returnCode=00`。复现时对每个case的`request`原样JSON编码，发送`Content-Type: application/json`、官方入口`Referer`及新的随机UUID `Action-ID`；没有账号凭证或个人身份号码。`computeCpfExcessContribution=N`计算责任，`Y`加入按月已缴雇主／员工份额。此公开路径仅供研究复现，**不应成为RPB生产实时依赖，也不是已获授权的CPF申报接口**。

官方页面限定公民／SPR第三年起、私营或非可享退休金雇员，且每月TW>750。下面全部满足该范围；不据此声称 SPR首／次年、月中身份变化或年度中低工资月已经外部交叉验算。报告其他已明确公式可独立用于这些组合，真正缺公式／事实才阻断。DOB全部虚构，用来定位年龄档，没有真实员工资料。

### B.2 跨55岁、含分币、旧AW额度仅6000的精确 oracle

固定输入：DOB=`1971-06-01`，2026年1–6月OW各8000，7–12月OW各5000；2月AW=20000.49，8月AW=4000.49，其余AW=0。同一雇主全年事实完整；6月满55岁，7月起34%／18%，此前37%／20%。年OW=78000；最终AW ceiling=24000；按应付先后，2月AW资格20000.49，8月资格3999.51（另0.98不吸引CPF）。

官方 `cross_age_final_liability` 返回：

| 原月／分项 | 月OW责任：总／员工／雇主 | 最终月OW+AW责任：总／员工／雇主 | AW归因差额：总／员工／雇主 |
| --- | --- | --- | --- |
| 2月 | 2960／1600／1360 | **10360／5600／4760** | **7400／4000／3400** |
| 8月 | 1700／900／800 | **3060／1619／1441** | **1360／719／641** |
| 全年AW归因合计 | — | — | **8760／4719／4041** |

复算：2月月目标总额HALF_UP((8000+20000.49)×.37)=10360，员工FLOOR((8000+20000.49)×.20)=5600；8月HALF_UP((5000+3999.51)×.34)=3060，员工FLOOR((5000+3999.51)×.18)=1619。雇主分别取差；AW归因是完整月减同月OW责任，不对分币AW单独舍入后任意归并。

第二组 `cross_age_shortfall` 将既有已缴设为：所有OW正确已缴；仅2月既有AW资格6000对应2220／1200／1020已缴，其余AW未缴。作为本fixture，既有已确认责任恰等于这些已缴值；这不是对生产数据“已确认=已缴”的默认推断。

| 原月 | 原已缴月总／员工／雇主 | 官方excess总／员工／雇主 | 新增责任及本fixture待缴总／员工／雇主 |
| --- | --- | --- | --- |
| 2月 | 5180／2800／2380 | -5180／-2800／-2380 | **5180／2800／2380** |
| 8月 | 1700／900／800 | -1360／-719／-641 | **1360／719／641** |
| 合计 | 全年30180／16200／13980 | **-6540／-3519／-3021** | **6540／3519／3021** |

其余月份差额为0；全年目标36720／19719／17001。官方负`excess`表示**实缴不足**，不是向员工产生负工资付款，也不是自动追收指令。

有区分力的错误算法：把新增资格2月14000.49、8月3999.51分别乘员工原费率后汇总再floor，会得到FLOOR(2800.098+719.9118)=3520；官方结果却是**3519**。总额同为6540不能掩盖员工多1、雇主少1的错误。结算月不得把这些历史差额与结算月新OW／AW放入一个原始基数再取整。

### B.3 多缴、分项反向差额与自动年度退款不可混算

`aw_excess_below_annual_limit` 保持同一全年工资，所有月份按B.2最终正确责任缴纳，仅8月人为多登记员工1、雇主1，以隔离测试多缴检测。官方返回已缴36722、目标36720、excess总2／员工1／雇主1。因36722低于37740，不能把这2元称为“必然年度自动退回”。它是AW计算器检测的月目标超缴，需相应退款／更正处理证据。

`zero_total_opposite_share_differences` 再改8月已缴为总3060／员工1620／雇主1440，其余仍正确。该值也对应误把8月全部AW4000.49都纳入资格后的份额（总额碰巧不变）。官方返回：

| 字段 | 返回值 |
| --- | ---: |
| `totalCpfExcessPaid` | **0.00** |
| `totalCpfExcessPaidEmployee` | **1.00** |
| `totalCpfExcessPaidEmployer` | **-1.00** |

所以total=0不能表示已完成对账。RPB须保留员工多缴1、雇主少缴1及原月引用，进入合适的外部更正／退款处理；本试算没有证明可以自动挪用员工1元抵雇主1元，也不授权向员工再次扣款。

官方 [AW calculator与自动退款差异FAQ](https://www.cpf.gov.sg/service/article/why-does-the-excess-cpf-from-the-additional-wage-ceiling-calculator-differ-from-the-automatic-refund-and-how-do-I-calculate-my-employee-s-share)（页面更新2026-09-18）明确：AW ceiling calculator按月TW舍入；年度自动退款按全年实缴超过37740的部分，二者可能差数元；较低费率下超AW ceiling也可能未超年度缴费上限。其2025单一费率示例为40000−37740=2260，员工FLOOR(2260÷.325×.17)=1182。**该年度退款比例公式不替代本例跨年龄逐月责任，也未证明多费率年度退款应选哪一个单一比例。** 混合费率自动退款分配以官方退款明细／适用规则为依据，不能拿年末费率猜；这是退款登记的证据边界，不是阻断已核实的AW补缴责任计算。

### B.4 可实现责任账算法及结算口径

以下是基于官方逐月响应及法规的实现设计，不声称已经运行RPB业务测试：

1. 固定年度事实、政策revision、身份／原费率、事件应付顺序和旧责任版本；以最终或当前合法估算OW重放AW资格。不能更改原冻结工资单。
2. 每原月调用统一OW+AW内核，按rawTW选档、分OW/AW基数计算，得到整数`targetTotal/targetEmployee/targetEmployer`。同原月多个事件归为一个舍入范围，AW归因可取整月目标减OW基准。
3. 责任修订为逐月`target - previouslyRecognised`，三个分项同时保存；实际待缴为`target - netRemitted`，另有已扣／可收回字段。不得因为公开工具只接受已缴额，就用已缴额代替RPB旧已入账责任。两者不一致时会计增量与资金余额本来就不同。
4. 复核结算月汇总逐原月整数差额，不再乘结算月费率或重舍入。不新增AW收入、不新增SDL/CDAC工资基数；当月新工资有自己的月目标差额。原月与结算月字段并存。
5. 相反符号的员工／雇主差额分别保留；负责任修订、超缴退款、待补缴本金均不等于员工现金付款。不得仅total为0就关闭，也不得默认跨人／跨月／跨份额净抵资金操作。

[CPF Act First Schedule](https://sso.agc.gov.sg/Act/CPFA1953?ProvIds=Sc1-)第2(5)段要求按AW成为应付的顺序重算；第4(1)段要求重算应缴超过已缴时补差；第5段给出年龄次月及总额／员工舍入。此次直接open仍403，但官方域名限定检索返回了相应正文，没有引用第三方转录。第9段还保留2025年前AW适用旧第2–4段，历史年度不能直接套现行条款。

[CPF Regulations reg2](https://sso.agc.gov.sg/SL/CPFA1953-RG15?ProvIds=pr2-)第2(3)款将该法定AW重算差额的支付期限联系到应重算月份结束后14日。这里只支持“按重算期结算、保留原费率来源”的分离设计；不推导普通历史遗漏也自动取得这一期限，不重新猜本报告未核定的利息金额。

证据支持本次T1正常档、跨月跨55岁、带分币、旧已缴不足／多缴／份额反向差额；不是对所有年份、SPR变化和低工资带的盲目外推。历史policy缺失、实际工资未知、旧责任或实缴不全，继续按对应缺项处理；不得将B.2已证明的组合继续以`AW_RECON_ROUNDING_EVIDENCE_MISSING`一概拒绝。

### B.5 s7(9)合法AW追加缴费追回分流

[CPF Act s7(9)](https://sso.agc.gov.sg/Act/CPFA1953?ProvIds=pr7-)官方检索正文明确：在First Schedule允许追回的AW追加缴费情况下，不受普通s7(6)同次工资扣回限制，可从雇主应付工资中在**追加缴费应缴年度结束后六个月内**追回。First Schedule第4(2)段联系适用的AW可追回比例和雇主已支付的追加缴费。此法定分流与“原雇主错误漏扣普通CPF”不同；不要求每宗符合该条款的年度AW追加缴费都另找普通漏扣的CPF特别许可，但也不能仅有一条内部审批就声称法定事实已满足。

设计上至少区分`ordinary_employer_omission`、`ordinary_non_negligent_recovery`、`aw_statutory_reassessment`、`already_deducted_unremitted`及`unclassified`等成因，并记录法律依据／版本、应重算期、追加应缴年度、实际缴纳与原已扣事实、可收回上限、截止日期及具体处理凭证。**合法可收回不等于自动扣工资**；本轮继续禁止系统自动从以后工资扣回。用户若后来授权具体合法扣回，应走已有经济变更治理及独立现金事项，不改原工资快照，不创造新审批层级。

例如2026年度依法应缴的AW追加份额，s7(9)文字对应最迟2027-06-30；这是期限示例，不是当前fixture已授权扣回的金额或操作。晚发现历史欠款时先确定追加缴费原本应缴年度，不能把发现年当新期限；员工已离职／没有可付工资时也不凭空生成工资扣款。s7(11)所述Board可酌情豁免无法收回部分须有Board决定，系统不自动减免。

### B.6 应并入父规格／计划的最小文字变更与验收点

- Spec 13.11“计算与年度事实”在“保留原AW费率分段”后补：按原月完整TW目标整数总／员工／雇主责任减原已确认责任，再在结算月汇总整数差额；实缴余额另算，禁止跨原月原始金额合并舍入。引用B.2及证据JSON。
- Spec 13.11“独立差额及分账”补：普通雇主漏扣、s7(9)法定AW重估追回及原已扣未缴按成因分流；仍不自动从工资收回。年度自动退款与AW月责任重算分离，total=0但员工／雇主反向差额仍须处理。
- Plan阶段6增加B.2的6540／3519／3021、B.3的0／+1／−1行为断言，原月归因与结算月聚合同时验证；原责任≠实缴时验证两个余额。阶段5的同月舍入继续使用A.3，公开工具同样返回38／21／17，证据case=`same_month_rounding`。
- 删除／收窄任何“所有跨月分币AW复核未核实所以阻断”的表述；仅保留真正缺历史政策、工资事实、身份规则、旧责任／已缴资料或特殊组合公式证据的阻断。不得声称本报告外部试算已经替代RPB实现测试。

本次仅更新报告及经父任务追加授权保存的合成JSON，没有业务代码、测试文件、SQL、真实申报或资金操作。

## 附录 C：低工资档与 AW 年度截短的窄边界

查验日期：2026-10-30；本次公开月度计算器响应时间 UTC 14:07（新加坡22:07）。只补阶段5提出的低工资／AW ceiling交叉问题，不撤回附录A/B已验证正常档与年度复核结论。

### C.1 结论和可实现范围

设 `T=rawOW+rawAW` 为完整原月工资，`B=cpfOWBase+cpfAWBase` 为应用年度额度后的基数。

| 情形 | 本次裁定 | 依据性质 |
| --- | --- | --- |
| T≤50 | 总额／员工／雇主均0；先按rawT判定 | 官方2026费率表 |
| 50<T≤500，AW截短或未截短 | 员工0；总额=HALF_UP(原政策雇主费率×B)，雇主等于总额；不可对B重新用50元免缴门槛 | 费率表线性公式与First Schedule第2段AW超额不缴的联立推导，非截短情形计算器实测 |
| 500<T≤750，AW未截短 | 原费率表渐进公式；使用完整T，同月总额HALF_UP、员工FLOOR、雇主取差 | 法规／费率表与本次官方月度试算一致 |
| **500<T≤750，cpfAWBase<rawAW** | **目前仍未核实渐进员工份额与被截短基数的组合公式；该原月责任计算保留unsupported** | 两个公开计算器均无法在其声明支持范围内表达这一组合；未找到明确官方操作样例 |
| T>750，AW截短或额度0 | 仍按rawT正常档，对OW／AW受约束基数计算 | 已有A.2与B证据，不受本次缺口影响 |

这里unsupported只针对有实际AW截短的渐进档月；不是“所有低工资AW”“所有年度中出现低工资”“所有金额带分币”都阻断。其他身份分段、历史政策及资料完整性守卫仍各自成立。若年度复核确需重算一个不支持原月，该员工该次复核不能产出猜测的完整应缴金额；不妨碍无依赖的员工／月份处理。

### C.2 法规与线性 employer-only 档为何可确定

[CPF Act First Schedule](https://sso.agc.gov.sg/Act/CPFA1953?ProvIds=Sc1-)第1段表受第1A–7段约束；第5(f)段TW包含该月OW和应付AW，第2段规定超过年度AW额度的部分不缴。55岁及以下T1的50<T≤500表项为17%TW、员工0。[2026官方费率表第1页](https://www.cpf.gov.sg/content/dam/web/employer/employer-obligations/documents/CPFcontributionratesfrom1Jan2026.pdf)相同。[CPF计算步骤](https://www.cpf.gov.sg/employer/employer-obligations/how-much-cpf-contributions-to-pay)还明确先由工资档选率，再对受CPF约束工资计算。SSO本次仍通过官方域名检索正文查验，没有采用第三方转录。

**法规联立推导：**该档没有500元渐进扣除项，17%(OW+AW)可线性拆为17%OW＋17%AW；排除年度超额AW后，基数只剩B。因此无需猜测任何渐进分配比例。其他已配置年龄／SPR政策取其对应雇主费率，不一律17%；没有适用政策仍不能计算。

可作人工公式验收数据，但不得标成“公开计算器已模拟年度截短”：

- T1年轻公民、OW400、AW100、余额40：rawT=500、B=440，员工0，总／雇主HALF_UP(440×17%)=**75**。完整年度可构造为12个月OW各400，AW ceiling97200，原AW已消费97160，本次资格40；年度上下文必须真实提供，不能只传余额数字。
- OW40、AW60、余额0：rawT=100、B=40，员工0，总／雇主HALF_UP(40×17%)=**7**。不得因B≤50重新选免缴档。相应完整年度可构造为12个月OW各40、AW ceiling101520、原已消费101520。此处测试的是原始档位与线性基数分离。
- T≤50时本来就是零档；不能用上例反推“所有小于50的原始工资仍需缴”。

500<T≤750时，雇主**未舍入的线性部分**同样只能针对受约束B；但最终雇主份额仍为已舍入总额减已舍入员工额，员工渐进项未明确前，不能据此把完整总额／雇主整数结果宣称已确定。

### C.3 渐进档缺口的具体证据与不可用替代方法

T1年轻公民费率表写总额`17%×TW + .6×(TW−500)`、员工`.6×(TW−500)`；该公式在无年度截短时明确。官方2026AW示例第21页明确档位依据截短前TW，但例子均是rawT>750，**不是渐进档与年度截短的证明**。[2026AW示例](https://www.cpf.gov.sg/service/sfc/servlet.shepherd/document/download/069IW00000KUuZCYA1)

有区分力的待核实fixture：OW400、AW200、年度余额100，rawT=600、B=500。全年OW可为4800、AW ceiling97200、原已消费97100，本次实际AW使全年工资超过102000。不能把未截短结果162／员工60／雇主102直接当作本例目标；不能将AW偷偷改为100后送月度计算器并声称那就是原600工资档；也没有官方证据证明应按600档有效员工比例乘500，或直接将渐进式中的TW替换成B。**本报告不在60、50、0等竞争计算结果中任选一个员工金额，也不把该人工fixture存成数额oracle。**

两个公开工具的能力边界：

1. [月度CPF calculator](https://www.cpf.gov.sg/employer/tools-and-services/calculators/cpf-contribution-calculator)明确全年工资超过102000时使用AW calculator。其公开JavaScript请求仅传年月、出生日期、身份／费率代码、OW、AW及操作类型；没有年度已消费AW、剩余额度或独立rawTW／受约束AW输入。它不能表达这个fixture。没有向API添加臆造字段，也没有利用可能接受的未知字段伪造支持。
2. [年度AW calculator](https://www.cpf.gov.sg/employer/tools-and-services/calculators/additional-wage-ceiling-calculator/)明确每月TW必须>750；低于／等于750需另请求计算。本次没有对该工具发送低TW请求，没有把绕过前端的响应当oracle。

公开月度服务输出虽含费率系数字段，但没有暴露“年度AW截短渐进项”的计算实现，不能从这些字段推断隐藏分配公式。官方FAQ／费率表／First Schedule已查验的段落也未给出解决该非线性分配的操作规则。本次未联系CPF、未发消息或提交个人计算请求；没有向用户提出新的确认事项。

### C.4 新保存的公开月度基线

[合成请求／官方响应及边界JSON](../../development/evidence/2026-10-30-cpf-monthly-low-wage-oracles.json)。从页面公开JavaScript `CPF_CONTRIBUTION_SERVICE v1.0.0`读取的服务为：`POST https://www.cpf.gov.sg/employer/tools-and-services/calculators/cpf-contribution-calculator/_jcr_content.contributionCalculator.json`。文件含完整输入、HTTP状态、UTC时间、完整响应和能力边界；全部合成DOB=`1990-01-01`、月份`2026-10`、公民、无年度截短。不是RPB项目测试结果。

| case | OW／AW／rawTW | 官方总额 | 官方员工 | 雇主=总−员工 |
| --- | --- | ---: | ---: | ---: |
| uncapped_TW500 | 400／100／500 | 85 | 0 | 85 |
| uncapped_TW600 | 400／200／600 | 162 | 60 | 102 |
| uncapped_TW750 | 500／250／750 | 278 | 150 | 128 |

三组均HTTP200、success=true、服务returnCode=00；750例的未舍入总额277.50变278，仍在渐进档。保存这些数据是为证明无截短基线及工具输入边界，**没有把它们扩大解释成年度截短oracle**。

### C.5 应并入 canonical 的最小修订

父任务可将本附录并入 `docs/superpowers/specs/2026-10-30-workforce-bonus-aw-arrears-design.md`，并将新JSON复制至其证据目录；本角色未修改canonical文件。

- 第2.3“设计推断与必要限制”以C.1精确守卫取代笼统低工资阻断：整月适用政策已知时，`500<rawTW<=750 && cpfAWBase<rawAW`才是本次未核实的ramp交叉；原始TW≤50的全免档与线性employer-only档分别按C.1处理，不按截短后的B重新选档。
- 计算内核／阶段5验收新增：50、500、750边界；未截短500／600／750的公开oracle；rawT100但B40仍雇主7；rawT500但B440雇主75；rawT600且AW截短时返回明确不支持原因、不返回猜测金额。
- 渐进截短原因码可沿用项目既有不支持组合机制，并携带rawTW、OW／AW资格、政策revision及缺失规则说明；不要把它混作缺年度资料、缺原责任或跨月舍入缺失。500<T≤750但AW完整纳入时不得因有AW而拒绝。
- 该缺口不撤回附录A.2正常档额度100／0、B的跨年龄分币复核，也不触发新用户审批或实时调用CPF依赖。支持组合通过测试即可继续；确有这一原月依赖时只阻断受影响计算确认。
