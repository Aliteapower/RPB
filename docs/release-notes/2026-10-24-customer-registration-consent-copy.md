# 顾客登记同意文案发布

2026-10-24，新加坡时区；工作区 D:\RPB。用户明确授权“提交部署”。

## 变更与范围

功能提交 `2a69887401fd7d1164f9ef05819513c491499379`，已推送至 origin/codex/paynow-payment-product-line-staging。将共用顾客登记说明改为“此登记同意包含营销推送同意或钱包扣款授权。”，同步英文说明。商户登记和顾客自助注册使用同一来源。

仅修改 CustomerPrivacyNotice 的中英文文字，版本仍为 customer-profile-v1。没有新增营销发送、钱包扣款能力，没有迁移、配置、权限、App Gate、API 结构或租户／门店隔离变更。历史登记保存的告知原文不回写。原工作区其他钱包组件改动及未跟踪文件保留，未提交、打包或上传。

## 验证与审查

- 主工作区及干净提交归档分别通过19项登记与隐私确认API／服务测试；干净归档 Maven package 成功。未声称全量测试通过。
- 新旧JAR逐项比对，条目集合相同，仅 CustomerPrivacyNotice.class 内容变化，其他条目字节一致。
- 新JAR SHA256：`a8e2e7796c2ad3fd4ec37cb5b73879bd825febe313f6908fb4775a2c58c5203e`。
- code-review：变更限于共用文案，历史快照读取、同意校验及业务执行逻辑不变。production-readiness：GO，发布锁、原版本校验、数据库／JAR备份及自动程序回退均已落实。

## 发布与线上证据

目标 ubuntu@43.134.69.75，/opt/rpb，服务 rpb-backend。只发布后端，RELEASE_SHA 为功能提交，FRONTEND_RELEASE_SHA 保持 `e81919d18a21e4b8a2c26ebb1c68a3ed9d18cec0`，前端index哈希保持。

持 /home/ubuntu/rpb-ledger-release.lock，临时阻断nginx到目标后端流量，停止后端后备份数据库并验证dump目录可读。备份目录 `/opt/rpb/backups/20261024-privacy-copy-2a698874-before`，含旧JAR、版本标记、967919字节数据库dump及业务指纹。发布目录 `/opt/rpb/releases/20261024-privacy-copy-2a698874`。

原子替换JAR后启动，通过就绪、数据和日志检查，再恢复入口流量。116张业务／配置表的数量及指纹一致；排除会话、限流、审计等自然变化表。Flyway保持070/70/0。nginx、PostgreSQL、Redis、Docker进程身份不变。

booking、platform.booking、20000000.booking、sx.booking.yumstone.sg共4个公网域名的registration-notice接口均返回准确的新中英文文案。auth/me匿名401。最终PID1223087，InvocationID94623839eb5940f7af5b307df778e1af，active，NRestarts0，启动日志无ERROR，临时流量规则已撤销。未提交有效顾客登记或执行生产资金、积分、营销操作。

## 回退与限制

如需回退，持同一发布锁，阻断目标后端流量，停止rpb-backend，从上述备份恢复旧JAR及RELEASE_SHA，启动并验证就绪后恢复流量；不恢复数据库覆盖业务数据。发布脚本包含失败时同等自动回退保护，本次未触发。

已打开的表单需刷新以读取新说明。本次为说明文字发布，没有改变营销或钱包扣款的实际执行规则。

证据：target/privacy-copy-tests.log、privacy-copy-clean-build.log、privacy-copy-artifact-proof.json、privacy-copy-deploy.log、privacy-copy-live-proof.json、privacy-copy-final.log。
