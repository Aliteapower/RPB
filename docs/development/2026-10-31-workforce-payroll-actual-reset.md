# 旧工资实际清理与两项前端基线修复

日期：2026-10-31，项目D:/RPB。用户明确授权“执行实际旧工资清理”，并要求一并修复两项前端问题。本轮已执行实际线上/本地数据清理；没有提交、推送或发布新前后端代码。

## 线上执行结果

核实目标为ubuntu@43.134.69.75的rpb-backend，数据库rpb_acceptance，public schema。以实际运行进程环境核实数据库连接，不输出密码。持有既有`/home/ubuntu/rpb-ledger-release.lock`互斥锁；先真实备份恢复至独立数据库演练，再停止后端保存最新完整custom备份、清理、核对保护对象并启动原版本。

实际线上已为V120，不能照抄本分支旧V112范围。读取运行JAR内V114–V120迁移SQL及真实FK，明确补充以下6张工资专用表：

- workforce_position_basis_salary_defaults
- workforce_pay_segments
- workforce_pay_segment_days
- workforce_pay_segment_commands
- workforce_pay_segment_payments
- workforce_segment_aw_assessments

原42表加6表共48张；没有自动按前缀扩大清理。线上新合同、合规政策、假期账户/额度/台账、请假、员工及其他产品均保留。重置工具默认42表未被放宽；本次私有操作脚本显式设置上述经审计的48表清单，并复用同一事务、锁、OID及未知依赖拒绝检查。

**实际清理29条工资专用记录，48表清理后均为0，重启后再次只读复核仍为0。** 包含模板1、个人方案3、批次3、输入1、命令9、计算3、工资变更申请/版本/事件各1、生效证据5、旧月份标记1。付款登记原为0。

清理前后其余209张表全行聚合指纹及序列状态完全一致；包含员工、账号、合同、排班、打卡、请假、法定费率、Flyway、共享审计及其他产品。没有DROP schema、CASCADE、关闭触发器或改写迁移历史。

备份与证据保留在服务器私有目录`/home/ubuntu/payroll-reset-20261031-2310/`；没有下载实际业务数据或秘密环境到本地。

- 最新停服恢复点：`actual-before.dump`，SHA256 `2555c53b7ec85ba899cbaca1798e402580698e859fbbdddc668509c16ceadb9a`，字节数24184374；pg_restore目录校验通过。
- 恢复演练数据库：`rpb_payroll_reset_rehearsal_edcef1fef8d1`，已禁止新连接；演练同样48表清29条、209表及序列不变。
- 第一次恢复因原钱包SQL校验函数依赖public搜索路径而失败，仅发生于新建演练库，未清理生产；保留该失败库并禁止连接。第二次仅将恢复SQL的会话search_path从空改为public,pg_catalog后完整恢复和清理演练通过，未修改生产函数或约束。恢复详细日志仅保存在服务器。
- 线上实际结果：actual-result.json、actual-protected-before.json、actual-protected-after.json及post-health.json；本地仅保存聚合结果target/payroll-reset-20261031/online-{actual-result,postcheck}.json。

清理后原JAR SHA256保持`e91a6bf363eba16960ec149995524cd85f30d8d528fabc4e24749192ac3a3253`；新Invocation为`bddd0170392040f58418282cf29cc489`、PID 3833274，active、NRestarts=0。启动日志确认应用启动，当前Invocation ERROR为0；公开workforce页面200，匿名payroll-home为401；Flyway仍V120且失败0。最初探针尝试不存在的actuator/health得到404，后改用实际存在的应用入口及权限端点复核，没有把404当成服务故障或健康通过。

## 本地执行结果

执行前读取D:/RPB/target/local-postgres-current.txt，确认D:/RPB拥有的127.0.0.1:61644/postgres、postgres空密码。当前公共/日记账schema无工资表；实际旧工资位于历史本地验收schema。逐一核对D:/RPB/target内原fixture、端口和schema归属，不依据名称推定所有权。

对9个有旧工资的验收schema编制显式清单，按该schema实际已安装版本选择原42表中的已存在表；不为清数据补装迁移、不静默跳过未知外部依赖。每库先完整schema备份，然后使用原工具同一严格事务守卫执行，并核对所有非工资表指纹。

| schema | 目标表数 | 删除行数 | 剩余行数 | 保留表数（指纹一致） |
| --- | ---: | ---: | ---: | ---: |
| `workforce_browser_08fe57e39e7e4b898c1fb70c7c4f8811` | 42 | 176 | 0 | 198 |
| `workforce_browser_15e08b99be3c43ef91fb2587a3304b40` | 42 | 176 | 0 | 199 |
| `workforce_browser_6b3d0ca974744b0c9b681036f8462dd6` | 7 | 43 | 0 | 192 |
| `workforce_browser_70fa22bea90f401889e79cbf7bd85261` | 6 | 20 | 0 | 179 |
| `workforce_browser_8c95805ed5814a7ea4068f7b50c800b7` | 33 | 88 | 0 | 198 |
| `workforce_browser_b293644c61fc40a5b31e43f6e2c923b3` | 6 | 21 | 0 | 179 |
| `workforce_browser_ceeab57877054d7f94a3125637dd8280` | 42 | 176 | 0 | 199 |
| `workforce_browser_d7f348e77beb42d8ba81ef130f366dc7` | 9 | 27 | 0 | 196 |
| `workforce_browser_e06f3d21e3d5403eafc1bc95536c9c8e` | 10 | 14 | 0 | 197 |

合计清除741条旧工资记录，各目标表剩余0。备份、SHA、逐表结果位于D:/RPB/target/payroll-reset-20261031；local-explicit-manifest.json包含每库确切表名与原fixture来源。没有清理空的历史schema、清理工具单测fixture或其他工作区运行库。

临时停止的5个旧D:/RPB实例（8098/8100/8102/8104/8106）均以原参数、原JAR恢复，均监听对应端口；仅将已核实的原5个PID文件更新为新PID。另一工作区8499、本轮新版8107及其他服务未停止。原旧实例的启动日志保存在本次target目录。

本轮新版schema`workforce_browser_e01d21e4a61645cea6b614638182c5d5`未清理；新工资22条专用记录、已发布批次及四名员工薪单保持。再次API读取确认指纹和金额不变，原负例保护哈希一致，付款0。

## 两项前端问题修复

1. scripts/tests/workforce-phase4.test.mjs：原断言只允许正式薪单列表请求，未计入已合法存在的本人补发组件。保留正式与补发功能，严格校验两个个人列表端点，并新增正式详情、补发详情与只读个人端点隔离测试；保留新加坡跨年及空态断言。
2. scripts/tests/workforce-loader.mjs：裸JSON导入经Vue/TS测试加载链进入Node时缺type=json。仅在测试loader补importAttributes.type=json，继续由Node原生JSON parser处理，不改产品代码或改写JSON数据。

RED保留于target/attendance-ui-broad-final.log；补loader前再次复现日志frontend-baselines-before-loader.log。修复后两文件61/61、loader相关回归114/114通过；根任务统一相关套件**275/275通过**，含新增1例，日志target/attendance-ui-baselines-fixed.log。`npm run build`（vue-tsc及Vite）通过，日志target/attendance-ui-baselines-fixed-build.log。本轮没有新增或改变后端产品代码，未无依据重复Maven。

## 仍需处理

- **生产迁移编号冲突：**本分支新考勤V114–V117与线上另一轮已安装的V114–V120语义不同。后续发布前必须协调相关分支、迁移及服务接口；不能改线上Flyway历史或直接部署本分支。此次只清工资数据，线上原代码和迁移保持。
- 移动端、完整中英文和员工端视觉范围仍按此前验收记录待办，不能以275项组件测试代替。
- 清理后的旧工资配置需要按新规则重新建立。未重建真实工资、代录付款、银行转账或正式申报。
- 需要恢复时先停写并评估清理后增量，服务器完整备份只作为恢复点；不得未经核对覆盖其他产品新业务。备份和演练库保留，没有进行额外清理。


## 后续迁移冲突处理

用户随后要求处理编号冲突，已按正式V1–V120保留、本轮迁移顺延V121–V124落地，未改线上历史或发布。前述“生产迁移编号冲突”的处理与验证以[迁移对齐记录](2026-10-31-workforce-migration-reconciliation.md)为准；数据清理及备份结果保持。
