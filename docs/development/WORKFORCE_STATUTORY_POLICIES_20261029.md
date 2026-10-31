# CPF／SDL配置与管理去重：本地验收

日期：2026-10-29。工作区 D:\RPB，保留既有未提交工作；未提交、推送或部署。

## 结果

- 管理新增统一CPF／SDL设置页和一个配置编辑器。2026、2027官方模板通过API载入草稿，核对并保存后才参与计薪。年龄档、工资阈值、五张费率表、渐进系数、OW上限、SDL和舍入参数可维护；不执行用户脚本。
- 按完整生效月份解析租户启用政策，启用区间不能重叠。每次修改追加历史修订；工资来源包含完整政策身份和配置，修订后待确认工资需重算。已确认、已发布工资快照及付款保持原值。
- 员工资料生日优先用于非外籍CPF年龄配对，生日次月换档；无档案生日时保留旧薪资方案生日作兼容。外国员工仍计算SDL；SPR首月需要分段时继续阻止自动计算。
- 仅覆盖全部租户门店、且所有门店通过AppGate的总部管理员能维护共享规则。普通经理、员工和部分门店总部账号不能维护；未增加现有账号权限。
- 管理首页删除公司下拉；工资模板与个人方案列表保留各自授权公司选择。删除岗位、公司、班次模板等页面重复局部导航，保留共享返回管理与面包屑及实际功能分页。
- 工资批次、报表、对账及工资单查询日期范围统一2000—2100年；是否能计薪仍取决于该月的有效政策，不默认套用2026费率。

## 已运行验证

1. Calculator旧10项＋配置8项全部通过；配置测试包含2026五表×五年龄×11工资边界共275组结果比较、2027变化、生日／SPR周年、修改阈值／费率／舍入／SDL和负雇主缴费拒绝。
2. 首轮Maven定向81项通过，包括策略PG集成30项、原工资PG24项、生日配置9项和上述18项计算测试。后续11项通过：完整政策身份／冻结快照2项、年份1项、策略API2项、原工资API3项、报表API3项。
3. 前端161项相关测试通过，包含真实组件交互、公司范围、防重复提交和409后重新加载最新版本；npm run build通过（vue-tsc与Vite）。
4. 真实本地浏览器保存并启用2026官方政策v1，核对历史记录；工资模板公司切换后仅显示目标公司记录且新增入口参数同步。岗位页已无重复导航。
5. 本地HTTP覆盖2027模板、草稿保存、同内容重试、修改及不可变历史、旧版本409、启用重叠409、非SGD400、跨站403、经理及员工403、当前工资摘要及原工资快照读取。
6. 对24类原业务表逐行哈希比较全部不变，包含员工、工资、付款、打卡、核准、附件、年历及调休。仅新增政策数据、审计和验收会话。

证据位于 target/workforce-statutory-policies-20261029/：frontend.log、build.log、backend-tests.log、backend-final.log、runtime-api.json、data-before.json、data-after.json及页面PNG。任一必要门店AppGate拒绝时禁止共享规则写入的补测1项通过，单独记录于appgate-test.log。已补测返回管理时保留授权公司上下文，无授权查询参数不会传递。

## 本地运行与迁移

先读取 target/local-postgres-current.txt，确认 runtimeWorktree=D:\RPB、port=61644；本次只对自有 workforce_browser_6b3d0ca974744b0c9b681036f8462dd6 验收schema应用V099，未修改public或其他项目schema。后端仍8096、代理5196，其他后端未动。

V099仅新增政策header与revision两表，无业务数据回填、无权限扩张。当前验收租户2026官方模板已启用；2027模板保存为未启用草稿，测试修改后恢复官方内容，修订历史保留。新租户或无覆盖月份需先配置，否则STATUTORY_POLICY_MISSING阻止确认。

## 官方依据和范围

- https://www.cpf.gov.sg/content/dam/web/employer/employer-obligations/documents/CPFcontributionratesfrom1Jan2026.pdf
- https://www.cpf.gov.sg/content/dam/web/employer/employer-obligations/documents/jan2027cpfcontributionrates.pdf
- https://www.cpf.gov.sg/employer/employer-obligations/how-much-cpf-contributions-to-pay
- https://www.cpf.gov.sg/employer/employer-obligations/skills-development-levy

仍仅SGD普通工资OW；AW、奖金／AWS、SPR首月分段、跨期补缴及自动向官方申报不在本轮。官方变化需管理员核对并保存修订，不自动覆盖。

## 审查

独立审查APPROVE：已修复全租户规则的部分门店权限边界、同费率替换政策的身份指纹、独立舍入导致负雇主CPF、409后加载旧表单及跨年报表限制。无剩余实质问题；此记录不授权生产发布。
