# 开发期旧工资数据重置

适用项目：`D:\RPB`。工具：`ops/workforce_payroll_development_reset.py`。

本工具用于开发期新版工资替换窗口，清空指定 schema 内**所有租户、所有期间**的固定旧工资专用表。用户已授权开发数据重置；`--apply` 是执行参数，不是新增审批口令。2026-10-31 首次验证仅使用新建合成schema；用户后续明确要求实际执行，已完成线上48表扩展清单及本地9个历史验收schema清理，详见[实际执行记录](2026-10-31-workforce-payroll-actual-reset.md)。下文默认42表工具说明及首次测试记录仍保留。

## 固定范围

依据 V091、V103、V105、V107–V112 的建表语句及真实外键，固定 42 张表。清单不会按前缀自动扩展；完整迁移至 V112 是使用前提，缺表即拒绝。

| 分类 | 表 |
| --- | --- |
| 旧工资配置、批次、工资单输入、计算与付款 | `workforce_pay_templates`, `workforce_pay_profiles`, `workforce_position_salary_defaults`, `workforce_pay_batches`, `workforce_pay_inputs`, `workforce_pay_computations`, `workforce_pay_payments`, `workforce_pay_commands`, `workforce_pay_bulk_operations` |
| 模板发布、采用和命令收据 | `workforce_pay_template_masters`, `workforce_pay_template_releases`, `workforce_pay_template_release_companies`, `workforce_pay_template_release_stores`, `workforce_pay_template_adoptions`, `workforce_pay_template_commands` |
| 工资变更及生效证据 | `workforce_pay_change_requests`, `workforce_pay_change_revisions`, `workforce_pay_change_events`, `workforce_pay_change_commands`, `workforce_pay_change_proofs`, `workforce_pay_approved_months`, `workforce_pay_effective_evidence`, `workforce_pay_legacy_months` |
| CDAC 批量设置命令收据与结果 | `workforce_cdac_bulk_commands`, `workforce_cdac_bulk_attempts`, `workforce_cdac_bulk_items` |
| 年度工资与缴费证据 | `workforce_statutory_year_subjects`, `workforce_statutory_year_revisions`, `workforce_statutory_year_commands`, `workforce_aw_reconciliations`, `workforce_aw_assessments`, `workforce_wage_events`, `workforce_wage_event_consumptions` |
| 补发与缴费台账 | `workforce_supplemental_batches`, `workforce_supplemental_payments`, `workforce_supplemental_commands`, `workforce_statutory_evidence`, `workforce_statutory_company_evidence`, `workforce_statutory_liability_entries`, `workforce_statutory_settlements`, `workforce_statutory_remittances`, `workforce_statutory_remittance_allocations` |

员工、账号、组织、门店、岗位本体、合同证件、定位、打卡、排班、请假、假别、法定政策及费率（`workforce_statutory_policies` / `workforce_statutory_policy_revisions`）、其他产品、公共审计及 Flyway 历史均不在清单内。员工原始字段和已有 CDAC 个人资料不会被修改。

## 安全与运行

环境要求 Python 3、psycopg 3（本次验证版本 3.3.4）。连接密码使用 libpq 的 `PGPASSWORD` / `PGPASSFILE`，不提供命令行密码参数。

1. 在替换窗口停止应用和写入任务，确认目标服务器、数据库及 schema。工具不负责进程停写或备份，也不提供数据恢复。
2. **本地验证必须先读取** `D:\RPB\target\local-postgres-current.txt`；端口取该文件，数据库 `postgres`、用户 `postgres`、空密码。不应复制下面命令时填入其他 worktree 或系统 PostgreSQL 端口。
3. 默认预览：输出连接数据库、schema 和各固定表的精确行数。预览也会检查不支持的依赖。
4. 同一参数增加 `--apply` 才执行清理。完成后输出清理前行数；任一步骤失败返回非零并回滚。

PowerShell 本地示例（先替换 `your_explicit_schema`；本次验证没有运行此示例到业务 schema）：

```powershell
Set-Location D:\RPB
$payrollPort = (Get-Content target/local-postgres-current.txt | Where-Object { $_ -match '^port=\d+$' }).Split('=')[1]
python ops/workforce_payroll_development_reset.py --host 127.0.0.1 --port $payrollPort --database postgres --user postgres --schema your_explicit_schema
python ops/workforce_payroll_development_reset.py --host 127.0.0.1 --port $payrollPort --database postgres --user postgres --schema your_explicit_schema --apply
```

如果环境配置了非空 libpq 密码，应在本地验证进程中明确置空；不得因此改用其他数据库。远程环境需要显式传入远程主机、端口、数据库、用户和 schema；本次未验证远程操作。

执行全程单事务，核对 `current_database()` 和显式 schema，先锁定全部固定表（实际执行为 `ACCESS EXCLUSIVE`），再验证 OID 和依赖并计数。锁等待最多 5 秒、单语句最多 60 秒。任何清单外的入向外键（含跨 schema、空表）、外部视图、继承/分区关系，以及任何 TRUNCATE 触发器均拒绝；不自动扩表清理。新增未知数据库结构必须先复核工具，不能为通过检查而删除依赖。

实际清理只有一条包含全部固定表的 `TRUNCATE ... CONTINUE IDENTITY RESTRICT`。不使用 `CASCADE`、不 DROP schema、不重置序列、不删除函数/触发器、不禁用不可变历史保护。各表的 UPDATE/DELETE 不可变触发器保留，TRUNCATE 按 PostgreSQL 规则绕过行级 DELETE 触发器。

## 验证记录

先新增测试并执行，因实现文件不存在失败（`FileNotFoundError`），随后实现工具。

```powershell
python -m unittest discover -s ops/tests -p test_workforce_payroll_development_reset.py
```

2026-10-31：读取本地运行时指针后，仅连接 `127.0.0.1:61644/postgres`，8 项 PostgreSQL 行为测试全部通过。覆盖默认预览不改数据；清理后固定表为空且员工保留；UPDATE/DELETE 不可变触发器仍生效；空的外部 FK、TRUNCATE 触发器、外部视图、继承拒绝；数据库/schema/缺表误指向拒绝；固定清单与迁移建表清单完全一致。

另在合成 schema 内使用最小外部主体表，真实执行 V091/V103/V105/V107–V112，确认真实 FK 图可单次 RESTRICT 清理，年度不可变证据可重置，员工行保留。没有伪造真实业务 schema 的迁移历史。

为遵守不清理既有对象要求，本次测试新建的 schema 均保留：

```text
payroll_reset_test_e5d999d715bd
payroll_reset_test_57b1247b9f10
payroll_reset_test_ed97b9213aaf
payroll_reset_test_df8a9a1b995a
payroll_reset_test_a276288416ed
payroll_reset_test_b133d0b5ff01
payroll_reset_test_8fafd7afc1f9
payroll_reset_test_26dc2496b9e4
payroll_reset_test_8a28edef1ab5
payroll_reset_test_973cf39dc45e
payroll_reset_test_3f683ee7a8f0
payroll_reset_test_9ba67cafbcfa
payroll_reset_test_4a0ea4ead4ca
payroll_reset_migrations_3796fef8e16c
payroll_reset_test_b3859a7e11c1
```

未验证范围：既有业务 schema、生产环境、生产账号权限与停写窗口、多进程并发 DDL、线上执行时间。合成验证没有执行任何线上清理、提交、推送或发布。
