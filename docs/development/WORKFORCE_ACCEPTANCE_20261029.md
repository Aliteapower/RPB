# 员工考勤计薪整体验收

2026-10-29；项目 D:\RPB；仅本地，未提交、推送、部署。覆盖既有一至五阶段，保留已有业务代码与钱包改动。

## 验收矩阵

| 项目 | 结果 | 证据或限制 |
| --- | --- | --- |
| 同一员工完整业务链 | 通过，82项API检查 | 建档/绑定原账号、门店授权、实际打卡、补卡、请假、工时核准、计薪、确认、发布、本人查看、分次付款、报表、对账 |
| 金额独立预期 | 合成样例通过 | 实际40h、带薪4h、无薪4h；44h×20=880；员工CPF176、雇主CPF150、公司SDL2、净薪704、成本1032；304+400=704，余额0 |
| 权限、幂等与只读性 | 通过 | 未授权工资/跨公司报表拒绝，重复请求不重复支付，超付拒绝，差额0.01精确显示，对账不修改业务数据 |
| 浏览器交互 | 已覆盖关键流程 | 员工提交补卡、经理批准，原始4条打卡保留；更正后50分钟；9月冻结金额未变；报表与API一致 |
| 三类CSV | 文件级通过 | BOM/CRLF、列数18/20/15、前导零000101、金额精度与隐私字段检查；未验证Excel自动类型识别行为 |
| 当前定向后端测试 | 15项执行通过 | 诊断、升级、API边界、审计失败事务回滚；见 readiness-test-summary.json |
| 实际Flyway增量升级 | 通过 | 真实V087旧表/权限资料升级V088..V091，旧资料不变，默认授权正确，重复迁移0项；非仅空库DDL |
| 候选构建与范围 | 通过 | 150个运行源文件白名单及SHA256；候选前后端构建通过；钱包文件等于HEAD，未混入8个钱包本地改动 |
| 本地回退演练 | 9项通过 | 旧HEAD应用启用Flyway读取V091 schema；关闭产品拒绝新业务，员工/工资/付款摘要不变；恢复原产品设置 |
| 真实历史工资对账 | 待资料 | 缺公司、已结算月份与脱敏工资文件路径；本轮880样例不代表真实工资验收 |
| 原生Excel | 环境受阻 | 启动原生Excel返回 GetCursorPos / 0x80070005 访问被拒绝；无已连接文档会话；待解锁可用桌面 |
| 打印/PDF/纸质输出 | 未通过验收 | 点击打印时浏览器输入超时，未取得预览/PDF/纸质输出；不能据此判断产品打印功能正常 |
| 实际手机及现场管理员 | 待设备 | 既有桌面窄屏证据可复用，但不是实际手机验收；需安全可访问环境，不能外露带合成账号代理 |
| 实际生产基线及发布授权 | 未核实/未授权 | 本地HEAD不等同生产SHA；未操作生产 |

## 本轮修复

WorkforceEndpointSupport 原先吞掉持久化异常，排障无法关联。新增 WorkforceFailureDiagnostics：服务器生成UUID，以 X-RPB-Request-Id 返回；只记录请求方法、路由模板、异常类型和合法SQLState。保持500错误码与响应体兼容，不记录薪资内容、异常消息、堆栈、原始路径参数或SQL绑定值。已先复现测试失败再实现；数据库错误和审计内部错误均验证安全日志。无新迁移、权限或计薪规则调整。

## 环境及可复核证据

- 唯一数据库：target/local-postgres-current.txt，127.0.0.1:61644/postgres，postgres/空密码，runtimeWorktree=D:\RPB。原数据库服务及8095/5195服务保留。
- 本轮后端8096/代理5196；成功验收schema为 workforce_browser_6b3d0ca974744b0c9b681036f8462dd6。所有资料为合成资料。历史排班仅在此schema以SQL建事实，后续补卡/核准/计薪/付款均经过实际API；当天四次打卡经过实际API。
- 两次准备失败分别源于跨门店复用班次、请假类型，均被现有门店约束正确拒绝；修正验收fixture后使用新schema，保留 attempt-1/2 证据，未绕过业务校验。
- 当天班次尚未结束，UI正确保留“班次未结束”的核准限制；未绕过该限制。浏览器补卡更正当日出勤，不影响9月冻结工资。
- 本轮证据目录：target/workforce-acceptance-20261029。关键文件：api-result.json、browser-result.json、csv-result.json、upgrade-result.json、rollback-result.json、scope-result.json、release-files.json、artifacts.json、readiness-test-summary.json及相应日志。
- 截图：correction-staff-pending.png、correction-manager-approved.png、report-accepted.png。
- 此前阶段五的后端286项、前端71项、111组流程/API及中英文窄屏证据见 WORKFORCE_PHASE_FIVE_20261028.md。本轮复用未变业务/前端证据；15项定向执行不代表重跑全项目或重跑全部旧测试。
- 本轮基线 d92107a0746f053df4d8247c14cbe496f5054eb8。src基线2479文件保存在 source-baseline.json；已有src文件仅诊断入口变更，新src仅诊断助手和两类测试。其他原改动保留。
- 独立审查三项技术问题（安全诊断、真实增量迁移、发布白名单/回退）已在本地关闭；未发现新的具体缺陷。生产及外部验收仍待完成。

合成工资预期参考 [CPF 2026费率表](https://www.cpf.gov.sg/content/dam/web/employer/employer-obligations/documents/CPFcontributionratesfrom1Jan2026.pdf) 与 [SDL官方说明](https://www.cpf.gov.sg/employer/employer-obligations/skills-development-levy)。仅覆盖既有2026普通工资方案；未扩展为完整申报、银行发薪或真实公司法定合规验收。

## 下一步

收到脱敏已结算工资文件后，在隔离环境以实际公司规则逐项核对工时、请假、CPF/SDL、净薪、付款与差额，再由管理员核对；通过可用原生桌面及真实手机完成Excel/打印/现场操作验收。随后核实生产SHA、配置和备份回退条件并重新判定；生产部署仍需单独明确授权。
