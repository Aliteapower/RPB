# Code Review Report

## Scope

本地合同政策、假期账本、工时核准及未休年假折现，接入现有请假、工资变更、月度／分段／补发和工资单。规格见 [业务规格](WORKFORCE_LEAVE_CONTRACT_COMPLIANCE_SPEC_20261031.md)，接口见 [API 契约](../api/2026-10-31-workforce-leave-compliance.md)。开发审查时未发布；现已按后续明确授权发布，见[生产记录](WORKFORCE_LEAVE_COMPLIANCE_PRODUCTION_RELEASE_20261031.md)。

## P0 - Must Fix

未发现未解决的 P0。Controller 仅调用所属应用服务；政策、额度、折现分别拥有持久化与业务职责，现有业务通过事务适配器接入。

## P1 - Should Fix

最终独立审查提出的三项问题已修正，并增加回归：

1. 工资变更因基线改变转为 stale 时，必须释放折现预占。拒绝、撤回、失效共用释放路径，重放不得重新锁住余额。
2. 已采用法定合同且属于加班保障范围时，即使旧工资规则关闭加班，也必须读取跨月同周的完整工时；不能漏算本月首日的周加班。
3. 同年更换雇主不能被旧雇主账户阻止建账。合同、额度查询与办理聚焦当前雇主，新雇主不复制原余额、服务起算或证据。

额外复查修正：采用新政策后的重叠请假按当前雇主检查，防止同一时间重复扣减；折现获益员工不得审批自己的折现，即使申请由其他管理员创建。

## P2 - Improvement

桌面及手机登录后视觉验收尚未完成。Computer Use 无法可靠识别 Windows 浏览器网址，被平台停止；不以组件挂载测试替代实际视觉结论。页面已复用 WorkforceLayout、wf-panel、网格及普通操作区，视觉一致性仍待真实视口确认。

## API / Database / TDD Review

- 沿用租户、公司、门店 App Gate 及工资权限；员工管理权限不能替代金额权限。员工本人只读自身额度，不能管理自身合同或创建／审批自身折现。
- 写接口有身份、同源与专用请求头校验，响应 private/no-store。幂等回执绑定操作者、门店及原始请求；权限仍在重放前校验。
- V116–V120 为新增版本，旧迁移和已冻结工资不回填。政策／合同版本、信用批次、账本流水、正式消费证据由数据库防覆盖；外键、唯一消费与事务回滚验证覆盖月度、分段及补发。
- 请假申请沿用员工锁及原事务，预占、批准、取消冲回不另起旁路；正式工资确认与折现核销同事务。原始打卡不被核准覆盖。
- PostgreSQL 验证仅使用本工作树 target/local-postgres-current.txt 指定运行时；测试自建隔离 schema。浏览器 schema 应用新增迁移前已备份；并未向生产执行迁移。
- 已观察纯规则／集成测试失败后修正实现，并覆盖并发、余额不足回滚、医疗依据、服务年、结转不延长到期、报价变动、独立审批及历史快照保护。历史无合同员工沿用旧计算入口。

## Test Coverage

可复核证据位于本地 `target/leave-contract-compliance-20261031/`：最终后端 `final-package-selected.log` 与精确方法清单 `final-test-selection.txt`，前端 `frontend-release-tests.log` 和 `frontend-release-build.log`，运行时接口 `api-smoke-result.json`；完整合成链路及公民扣缴用例保存在对应 PostgreSQL 测试源码。最终结果与数量见发布说明。

扩大到整类测试的 `final-package.log` 未通过：继承的父类旧用例与子类初始化夹具相冲突，出现同租户 E001 重复等错误，已停止该轮。最终选择本次各类自行声明的方法，避免误跑继承用例；旧计薪回归采用此前独立运行的结果，不声称全仓或整类套件全部通过。两次遗漏 TypeScript／Vue loader 的 Node 命令属于测试启动错误，改用项目 workforce-register 后的前端结果为最终证据。

## Final Decision

APPROVE_WITH_COMMENTS：代码与非视觉验收按上述证据交付；桌面／手机视觉验收明确保留，不据此宣称可生产发布或所有验收已完成。第一模型仅处理标准日／周合同，特殊轮班／豁免、同年天／小时转换及其他亲职假需专项建模，不推断计算。
