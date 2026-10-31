# 异常考勤迁移编号与线上V120对齐

2026-10-31，D:/RPB。范围：解决迁移目录冲突、验证本地升级与制品；未提交、推送、部署或改写线上数据库。

## 根因与处理

本分支基线为已发布V113，本轮4个尚未发布的异常考勤迁移使用V114–V117；另一个workforce-guided-payroll分支已将不同内容的V114–V120发布上线。因此单独重命名4个文件仍不够：本分支必须同时包含线上已经执行的正式迁移，否则Flyway会报已应用迁移缺失或校验和不一致。

本次只读提取线上rpb_acceptance.public的Flyway元数据与运行JAR内SQL资源，核实V1–V120共120份资源均与已发布提交`e2457db5b48d60bd3ad70f57f221c5a34eac35b3`一致（仅统一CRLF/LF比较）。未读取或下载业务行、环境密码及真实数据库备份。正式SQL从该提交的Git对象提取，不从另一工作区未提交内容复制。

保留正式V1–V113，补入正式V114–V120原文：

| 版本 | 正式迁移 |
| --- | --- |
| V114 | workforce_position_basis_salary_defaults |
| V115 | workforce_segment_settlements |
| V116 | workforce_compliance_policy_contracts |
| V117 | workforce_leave_accounts |
| V118 | workforce_leave_encashment |
| V119 | workforce_segment_wage_events |
| V120 | workforce_leave_evidence_guards |

只顺延本轮未发布的4份文件，文件内容SHA256不变：

| 原开发编号 | 新编号 | 文件 |
| --- | --- | --- |
| V114 | V121 | workforce_attendance_policies.sql |
| V115 | V122 | workforce_attendance_daily.sql |
| V116 | V123 | workforce_leave_entitlements.sql |
| V117 | V124 | workforce_attendance_system_evaluation.sql |

没有执行Flyway repair/baseline或修改已执行历史，没有通过忽略缺失迁移来跳过验证。Maven资源复制不会清理旧名字，仅删除已逐字核实的4份旧target/classes/db/migration生成副本；没有mvn clean或删除target（本地PostgreSQL数据目录位于target，不能整体清理）。

## 回归保护

新增`src/test/resources/workforce/released-v120-migrations.json`固定正式120份SQL的文件名、Flyway校验和与仅规范换行后的SHA256，来源为线上只读元数据且已与正式提交核实，不含业务数据或凭据。

- WorkforceMigrationCatalogTest：保护正式SQL未变、新考勤编号和目录版本唯一完整。先运行RED，2项分别因缺少正式V114和缺少V121失败，日志target/migration-reconcile-20261031/catalog-red.log。
- WorkforceAttendanceMigrationUpgradeTest：实际Flyway空库安装至V124、V113→V124的11次升级、V120→V124的4次升级；全部校验并检查重复迁移0；逐一匹配正式120份迁移的checksum/script，比较原有业务表行及既有Flyway记录全字段不变。合成已发布工资、合同、年度假期账户、授额lot及ledger保留；合同不可变触发器仍拒绝改写。
- 同时复核四类策略、每日有效来源、年度权益和系统审计的PG行为；不是只比较文件名。

第一轮整合3条迁移路径均完成DDL安装，但两条历史记录断言误把schema创建行计入版本上界，额外选入升级第一行而失败；按真实最后installed_rank修复测试查询，不修改迁移或数据库历史。原失败日志保留为migration-first-integration.log。

## 实际验证结果

已确认三条真实迁移路径通过：空库124次、V113升级11次、V120升级4次；validate成功，重复迁移0；旧数据、既有Flyway记录和合同不可变守卫通过。使用本任务新建的隔离schema，不在当前服务库安装迁移。

相关6类测试共113次执行，0失败/错误（含继承夹具的重复执行，不宣称113个唯一业务场景）：

| 测试类 | 执行数 | 失败/错误 |
| --- | ---: | ---: |
| WorkforceMigrationCatalogTest | 2 | 0 |
| WorkforceAttendanceMigrationUpgradeTest | 3 | 0 |
| WorkforceAttendancePolicyPostgresTest | 34 | 0 |
| WorkforceAttendanceDailyPostgresTest | 37 | 0 |
| WorkforceLeaveEntitlementPostgresTest | 22 | 0 |
| WorkforceAttendanceNormalEvaluationPostgresTest | 15 | 0 |

命令：`mvn -q '-Dtest=WorkforceMigrationCatalogTest,WorkforceAttendanceMigrationUpgradeTest,WorkforceAttendancePolicyPostgresTest,WorkforceAttendanceDailyPostgresTest,WorkforceLeaveEntitlementPostgresTest,WorkforceAttendanceNormalEvaluationPostgresTest' test`。退出0；日志migration-green.log、逐类test-summary.json保存在target/migration-reconcile-20261031。目录保护只要求既有1–124完整且不重复，不禁止以后合法追加V125及之后版本；调整后单独再跑目录测试2/2通过，日志catalog-final-green.log。

`mvn -q -DskipTests package`退出0，日志package.log。最终包SHA256为`d218e48a01a08931057ca80dc30cef2d9d2ca040aaef9f9c1c3ff89d4194bd28`，位于D:/RPB/target/reservation-platform-0.0.1-SNAPSHOT.jar。

直接读取JAR验证：124份版本化SQL完整且无重号，120份正式资源hash匹配，4份顺延文件内容SHA256不变，4个旧开发文件名均无残留。与上次本地已验收QA包比较，2619个运行class与93个依赖JAR逐字节相同；这证明本轮没有引入Java/依赖变化，不代表本分支已合并线上另一分支全部应用功能。验证脚本与artifact-verification.json位于本次target目录。

`git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check`通过。迁移只读保护、升级数据保护、已有不可变触发器及打包资源检查均通过；本次迁移协调可交付，未执行生产发布。

## 原有本地验收快照

既有8107及本轮已发布合成工资位于旧开发顺序安装的独立schema，属于旧包冻结验收证据。本次保留其数据、原JAR与历史，不把其中开发V114–V117强行改写成线上正式历史；该快照继续运行原包，不能作为新版Flyway验证schema。新升级验证只使用本任务新建的隔离schema，连接前读取D:/RPB/target/local-postgres-current.txt（D:/RPB拥有的127.0.0.1:61644/postgres、postgres空密码）。

未来完整浏览器/发薪验收应新建V124库或从真实V120来源升级；不使用repair把旧开发库伪装成V120，也不清空已保留的新工资验收证据。

## 发布与范围边界

本次解决迁移目录、编号与数据库升级顺序，不将另一分支的整套合同/假期/分段结薪Java及Vue功能自动覆盖到当前脏工作区；那些应用层合并仍须独立审查及相关业务回归。迁移通过不等于整套线上功能已合并或生产可直接发布。

既有实际旧工资清理结果不变；本次没有再次清理生产或改变服务。上线前仍需复核实时线上版本、以正式恢复库演练候选应用，并按明确发布授权执行。原视觉验收缺口不由本次迁移测试替代。


## 数据库与代码审查

正式120份资源以只读线上校验和及Git来源双重核实；仅未发布4份更名，现有FK、租户/公司/员工作用域、不可变记录、系统actor约束不改。实际升级后既有表和历史记录逐字段比较，合同历史改写仍被旧触发器拒绝。目录回归防止再次重号，包验证防止残留旧资源。没有新增Controller、API、权限、配置或依赖，也没有借协调编号整合其他业务模块。保留无关工作区改动。


## 后续合并发布衔接（2026-10-31）

用户随后明确授权合并、验证、提交部署。当前整合已包含已发布分支应用层功能与异常考勤实现，新增V125区间请假医疗凭证引用；正式V1–V120仍保持不变。本节更新前文的当次范围边界，具体集成回归、恢复演练与实际发布结果见 `../release-notes/2026-10-31-workforce-exception-integration.md`。未重复清理旧工资。
