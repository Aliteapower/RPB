# 员工考勤设置页面优化（2026-10-31，已发布）

## 结果与范围

- 员工考勤页面拆为“考勤设置 / 居家地址 / 变更历史”三个点击切换的功能页，默认打开考勤设置。
- 当前执行策略单独突出，未来安排明确标记待生效。历史标记已结束并显示执行区间；相同内容的连续记录按展示分组，保留全部日期和版本供展开查看。
- 单一考勤方式不再重复显示“方式 / 默认方式”。岗位来源展示快照和岗位版本，不显示岗位 UUID。
- 居家地址待办单独展示，已处理记录默认折叠；地址状态区分待审批、待生效、当前有效和已替代，坐标按需展开。
- 审批人通过后端只读姓名映射显示；姓名缺失时提示“姓名暂不可用”，不回退显示 UUID。

## API、权限与历史

`WorkModePage.reviewerNames` 为兼容性新增字段。查询通过地址记录与账号的 `tenant_id` 和审批人 ID 联合关联，并限制目标员工。不获取整个租户的账号目录。停用账号仍能显示姓名。

原 `HomeAddress`、审批指令、审计与策略记录保持原结构，姓名为读取时的账号显示名称，不声称是审批时姓名快照。无数据库迁移，无权限/App Gate 变更，不清理历史重复记录。原幂等重试、禁止本人审批、过期申请禁批保持有效。

前端将策略分组放在 `workModeDisplay.ts`，卡片渲染放在 `WorkModePolicyList.vue`。中英文均提供对应文案。

## 验证

- `npm run build`：通过（Vue 类型检查及 Vite 构建）。
- 新增 `workforce-workmode-ui.test.mjs`：6 项通过，覆盖功能分区、当前/未来分组、版本与来源保留、姓名及 ID 隐藏、空/错误状态、权限、重复提交及原请求重试、本人和过期审批限制。
- 扩展前端回归：phase2 / phase3 / phase4 与首批 4 项新测试共 52 项，51 项通过。唯一失败为未修改的 `workforce-phase4.test.mjs` 工资单请求断言：现有页面额外查询 `/my-supplemental-payslips`，旧断言只期待 `/my-payslips?year=2027`；本次不扩大范围修改。
- 后端 `WorkforceWorkModePostgresTest`（45）、`WorkforceWorkModeApiTest`（3）、`WorkforceClockOptionsPostgresTest`（59）、`WorkforceWorkModeLocalRuntimeSecurityTest`（1）：共 108 项，全部通过，无跳过。使用 `D:\RPB\target\local-postgres-current.txt` 指向的 PostgreSQL，在随机测试 schema 内执行；其中部分父类用例由两个套件继承执行。
- 使用项目 CRLF 规则执行 `git -c core.whitespace=cr-at-eol diff --check`，通过。
- 尝试本地合成数据浏览器验收，但浏览器工具连接超时，随后因无法可靠识别 Windows 浏览器当前 URL 停止；未完成桌面或移动端视觉验收，不声称生产验收通过。

## 发布与回退

本轮已提交、推送并与员工编辑页一起部署为 `50bebc35`，见 [生产发布记录](WORKFORCE_UI_PRODUCTION_RELEASE_20261031.md)。发布时前后端配套更新才能显示审批人姓名；前端对旧响应仍兼容，但会提示姓名不可用。回退相应前后端代码即可，无数据回退要求。
