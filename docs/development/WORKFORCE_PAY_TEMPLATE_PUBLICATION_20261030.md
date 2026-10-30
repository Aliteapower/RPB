# 工资模板发布与门店采用（本地阶段1）

状态：本地实现，最终验证与阶段审查见 `.superpowers/sdd/2026-10-30-workforce-template-governance/task-1-report.md`；没有提交、推送或生产发布。接口真相见 `docs/api/2026-10-30-workforce-template-publication.md`。

## 模块与来源

`workforce/payroll/governance` 分开发布、采用、范围与仓储；Controller 仅调服务。发布复用原工资 Rule 标准化，计算器、法定政策及工资批次没有新增公式或新规则。新入口位于 workforce 管理页“门店工资模板库”；原公司共享模板继续在工资设置维护。

母版草稿可编辑，发布 release 固定版本与 scope；future scope 保存数据库可见的完整既有门店 ID（包含 inactive/deleted），当前公司/active合法且不在该集合的新门店可见；创建事务开始早于发布、提交晚于发布不会被时间门槛永久漏掉。已有旧门店重新激活仍在集合，不会变成未来门店。该可见性不授账号门店权或工资权。

启用只增加门店候选：采用快照与对应 `workforce_pay_templates` 物化记录逐版本独立。`storeVersion` 按门店/母版递增；当前公司变更后必须重新显式启用，新公司记录不会复用旧公司物化ID。发布升级只产生 updateAvailable；个人方案、岗位映射、未确认工资及冻结/付款快照均不自动更新。

## 迁移 V108

- 新增 masters、releases、release_companies、release_stores、adoptions、commands；不修改V001–V107、旧JSON、工资和付款记录。
- scope和采用FK含tenant与公司/门店归属；采用的公司还受发布company范围FK限制。release_stores 对当前stores只FK tenant/store，不把历史公司字段绑死在可变门店公司上，避免妨碍门店正常转公司。
- 发布、采用、命令等不可变记录的UPDATE/DELETE由PG触发器拒绝；已有采用引用的原模板UPDATE/DELETE也拒绝。legacy模板仍能按原版本更新。
- `SalaryTemplate.governanceSource` 为可选新字段，仅新受管物化快照带来源，旧JSON和六参Java构造保持兼容；无历史批量补字段。
- 仅在 pointer 的61644/postgres随机测试schema执行；没有对已有运行schema运行迁移，8098/5198未重启。

## 接口给后续阶段

`TemplatePublicationRepository.adoption(tenant,store,company,id)` 按精确归属解析任何历史固定采用；`release(tenant,master,version)` 解析母版固定版本。不能以共享岗位映射或客户端给出的物化ID代替这些归属检查。

`PayrollConfigurationRepository.assertTemplateUsable` 限新候选为本店当前有效采用；`assertTemplateAccess(...,historicalSource)` 只处理本店历史来源，既有同模板方案维护不强制升级。此布尔是服务器内部读取语义，不能在后续阶段作为审批绕过。阶段2/3必须独立绑定批准内容与有效证明。

阶段1旧入口已经接入：salary-templates列表、按ID模板保存、Profile保存（重放之前）、岗位默认预览及保存。岗位legacy映射仍公司共享，引用别店受管模板时当前店预览/保存拒绝；不复制、不扩薪资权。

## 验证、限制与后续

真实RED/GREEN命令、精确计数、失败调查和最终结果统一记录在阶段报告及 `target/workforce-template-governance-20261030/task1-*`，本记录不重复维护会过期的测试计数。UI测试实际编译/渲染Vue并操作输入/按钮；未做真实浏览器视觉或手机验收。

尚未实现：门店派生申请、个人/月度金额申请及审批、精确经济写守卫、稳定月份金额证明、批准生效/作废重建、影响预估、批量结果、事实配置分类。spec13.9既有员工切换发布模板导致经济变化须审批由阶段3闭合。阶段1保留原个人保存行为，仅新增受管模板隔离；不能声称工资审批已完整上线。

## 回退约束

本轮未部署。若未来执行V108并产生采用数据，不能仅回退到不认识治理来源的旧二进制：旧列表/个人保存可能将门店物化模板重新当公司共享，造成作用域回归。回退必须保留本阶段门店隔离与不可变保护，或先停止相关工资配置入口并制定有归属证据的数据处理方案；不能删除采用/发布证据、改历史工资JSON或粗暴删除物化模板。生产发布和任何数据处理仍需单独明确授权。

## 阶段审查 fix round 1

修复母版接管路径：save在重放返回/更新前独立授权old scope，drafts按同一授权展示。旧scope不因门店停用/转公司而当新scope非法永久锁死；inactive门店可用同租户同账号仍有效的服务器门店分配证明，active旧门店仍以access.enter重检actor门店权及AppGate，不能借修复从另一店绕过产品过期/关闭。真实认证会滤掉inactive门店，PG用例模拟刷新后的principal而非假设它仍持有inactive id。

新增GET `/pay-template-publication-directory`供合法总部取得活动公司/门店（含没有门店的公司），不改Home或PayrollAccess。编辑器直接消费该目录，并保留旧失效选择供明确取消；不静默丢掉旧scope。新增真实Vue无店公司选择+future提交、失效旧店显式修复；目录API/精确安全/权限与PG回归在阶段报告中记录。

多公司发布下旧enable命令重放还须结果company与当前company一致，否则403；避免转公司后把旧公司采用当本次成功结果。
