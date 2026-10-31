# 工资项目、明细与汇总调整契约

## 范围及职责
复用payroll计算/生效方案/付款、statutory已保存规则、reporting只读投影；不另建工资页面或项目目录，不改变其他产品模块。项目仍在工资模板、个人方案与本月调整维护，可自定义名称和金额。收入分basic、overtime、ow_additional、ow_allowance、ow_accommodation；reimbursement独立列；员工扣项分deduction、cdac；employer_charge只增加雇主费用。额外收入仅普通工资，奖金/AW/AWS不在范围。现金住宿津贴计入普通工资；非现金待遇不得作为该项录入。扣项不减少CPF/SDL基数，CDAC手动确认金额不按姓名推断或自动套族群。

## API兼容
现有 `/api/v1/stores/{storeId}/workforce` 路径与权限保持。salary-templates、pay-profiles和payroll-batches输入接受上述新增kind；未知kind仍400。新增收入与原ow_allowance相同参与现有月薪固定项比例折算；本月调整不折算。旧kind及金额行为不变。
- PayProfile/ProfileWrite可选payment `{method:cash|bank|paynow|other,bank:string}`，名称最多100字；现金bank须空；银行付款须填银行。旧方案无该字段仍可读写，旧JSON省略null，不改变旧来源哈希。新付款配置随方案进入工资快照，确认后不改变。
- PaymentWrite/Payment新增可选bank，method新增paynow。银行名称和交易reference分开；旧bank付款无bank数据保留为空，新银行付款要求bank，完全一致旧请求重放仍有效；冲正沿用原付款银行。付款幂等同时核对bank，不触发真实转账。
- Payslip新增独立breakdown与payment投影；不泄露CPF身份、管理备注及账号。原金额字段保留。
- payroll-report公司/员工/冻结和预测汇总新增breakdown（各类金额、按kind+label汇总项目）；员工行新增paymentMethod/bank（工资快照计划值），付款行bank为实际登记值，不混淆。旧工资直接投影旧组件，不重算/改写。CSV保留原列顺序，末尾追加分类金额和付款方式/银行，并新增items明细导出；继续保留指纹、防公式注入及范围权限。

## 数据及迁移
V102仅新增付款bank可空字段并扩展method约束，旧行bank保持null，无回填、删除或金额更新。方案payment存在既有JSON快照，不另设表。tenant/company/employee/batch外键和审计不变；同源写保护无需扩大路径。

## 实现和验证顺序
先补测试，再实现项目分类/付款验证、只读明细投影与报表、共享前端分类组件及编辑字段，最后数据库/浏览器验收。覆盖：收入与扣项/雇主费不串算；2026各年龄/SPR/低工资/上限及SDL精度；真实表匿名24行演算及SDL差异；旧快照JSON哈希；付款银行、冲正、幂等和越权；报表分类总计、CSV及隐私；真实UI保存/刷新。只使用指针数据库和合成数据，原有业务行对比保护；不导入真实员工，不提交、不推送、不部署。

## 官方依据（2026-10-30核对）
- https://www.cpf.gov.sg/employer/employer-obligations/how-much-cpf-contributions-to-pay
- https://www.cpf.gov.sg/content/dam/web/employer/employer-obligations/documents/CPFcontributionratesfrom1Jan2026.pdf
- https://www.cpf.gov.sg/employer/employer-obligations/skills-development-levy
2026现有官方模板分档与页面表格对应，生产计算仍使用保存规则版本；不为贴合旧表修改费率或基数。SDL按每人月总工资0.25%，最低2最高11.25，再公司合计向下取整；真实表Other扣款导致的SDL差异单独报告。
