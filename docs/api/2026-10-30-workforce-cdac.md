# CDAC法定规则与自动计薪

复用既有法定规则版本、薪资方案及本月核对，不新增页面或接口路径，不迁移/回填历史配置。计算职责分为statutory/CdacRules的声明式档次和payroll/CdacPayroll的适用、覆盖和重复扣款保护。

## 配置与兼容
- StatutoryPolicyConfig新增可选cdac `{effectiveFrom,effectiveTo,source,bands:[{upperWage,amount}]}`。日期采用月首至月末，须在所属政策的有效区间内；工资上限严格递增，末档null，每档金额非负且最多2位小数，最多20档。沿用政策启停、版本、全租户管理员权限、官方来源及审计。
- 官方2026/2027年度模板附带年度CDAC档次；老规则未设置cdac保持null，省略序列化，不能静默补配置。编辑器可显式载入年度模板的CDAC部分，核对保存后生效。跨年复制调整日期并清空来源，保持草稿。
- PayProfile/ProfileWrite新增可选cdac `{status:applicable|not_applicable|exempt|manual,reference}`；exempt必须记录已核对的豁免依据，manual须有说明。applicable仅接受方案CPF身份为citizen/spr（不凭姓名判断族群）。未配置的旧方案继续原手工项目行为。已用于确认工资的方案仍禁止修改，只能新建生效方案。
- InputWrite/LineInput新增可选cdacReview `{amount,note}`：仅对applicable方案手动覆盖本月自动金额，必须填说明；仍计算并保存原标准额与差额。首次核对默认自动；明确回到自动传cdacAutomatic=true且不传覆盖金额。旧客户端省略新字段时保留现有覆盖，避免误清。已配置方案切回手动须选manual并填写依据，空值只用于旧方案兼容。旧输入重放和JSON指纹不变；同批次核对变更仍增加输入及批次版本，不能暗改已确认金额。

## 计算与快照
CDAC按本公司本月普通工资总额（grossPay，非CPF上限后基数，不减员工扣款/CPF，不含报销）选择档次。零工资不扣款；入离职月按实际本期工资选档，不再按天数重复折算。applicable缺少当月生效CDAC规则阻止确认。exempt/not_applicable扣零，manual/旧方案沿用人工项目。

applicable、exempt或not_applicable与任何固定/本月手工kind=cdac项目并存时，明确阻止确认，不能自动叠加、删除或忽略。启用自动前须人工移除旧手工项。额外贡献及特殊情形可用有说明的本月核对覆盖；不替员工提交豁免或官方申报。

PayrollLine新增可选cdacCalculation `{mode,basis,standardAmount,amount,lowerWage,upperWage,policyVersion}`，记实际选档、政策版本及手动/豁免状态；实际扣款仍使用既有cdac组件，进入现有明细/汇总/CSV，不修改CPF/SDL基数或雇主费用。员工工资单只投影计算结果，不包含reference、覆盖note等管理说明。历史字段无回填，旧快照不重新计算。

## 错误和验证
非法配置/资料400，沿用401/403/409及AppGate。计算阻断新增CDAC_POLICY_MISSING、CDAC_DUPLICATE、CDAC_PROFILE_INVALID、CDAC_REVIEW_INVALID，交给既有工资确认守卫；source fingerprint包含新设置及政策修订。

测试：2000/3500/5000/7500边界及以上、零工资、高于CPF上限仍用总工资、日期范围和修订、适用/不适用/豁免/人工、覆盖差额和说明、重复扣款、旧JSON/快照、保存重放/历史冻结、跨公司/门店/租户/AppGate、前端新增档次/表单/只读历史及明细。仅本地验证，不提交、推送或部署。

依据（2026-10-30核对）：
- https://www.cpf.gov.sg/employer/employer-obligations/contributions-to-self-help-groups
- https://www.cdac.org.sg/cdac-funding-info-information-for-employers
