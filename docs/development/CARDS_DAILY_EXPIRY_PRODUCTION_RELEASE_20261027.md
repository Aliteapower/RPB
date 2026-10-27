# 卡业务页面及日额度过期扣次生产发布 — 2026-10-27

## 版本与范围

项目 D:\RPB，远端 Aliteapower/RPB，分支 codex/paynow-payment-product-line-staging。
前后端功能制品均为 `f3f35ca9f2e87e30f54f497e5fdd5049deda64fb`，已推送。仅提交 27 个本轮文件；其他 12 个未提交文件 SHA256 保持一致。后续文档提交不改变线上功能制品 SHA。

服务器 43.134.69.75，服务 rpb-backend，数据库 rpb_acceptance。不可变制品 `/opt/rpb/releases/20261027-customer-cards-daily-expiry-f3f35ca9`，实际应用 `/opt/rpb/app/reservation-platform.jar`，前端 `/opt/rpb/frontend`。

## 准备与备份

- 干净 git archive 的 2,903 个文件逐一与 Git blob 校验；34 项前端测试、42 项后端测试、Vue 类型检查、Vite 构建及 Maven package 通过。
- 新旧 JAR 差异仅 10 个条目（本轮卡模块类及 V086 迁移）；依赖、配置与既有迁移保持字节一致。
- 首次发布因剩余磁盘不足在预检阶段停止，尚未切换服务或执行迁移。用户授权清理过期备份后释放约 5.18 GiB，清理记录见 RPB_EXPIRED_BACKUP_CLEANUP_20261027.md。
- 回退备份 `/opt/rpb/backups/20261027-customer-cards-daily-expiry-f3f35ca9-before`，包含旧 JAR、完整前端、前后端 SHA、配置与 19,983,479 字节数据库 dump，pg_restore 目录校验通过。

## 迁移与业务验证

- Flyway V086 成功，状态 `086|86|0`。原有 9 张卡余额、版本及状态保持不变，起算日为迁移时各卡冻结时区的业务日；没有历史追扣。
- 在既有独立 QA 租户 43d4b2f4-b3f6-4883-9e6e-474de2985011 新建临时验证卡 53c81200-3144-4553-956d-9197d0b25a0a，显式模拟一个已结束的可用日：读取后 30→29，今日使用量仍为 0；重复读取仍为 29，只有一笔 daily_expire，实际消费分录为 0。
- 顾客流水能读取 30→29 的独立过期记录；报表计入过期量；跨店读取被拒绝。QA 卡完成后置 revoked，停止未来日结，账目和审计保留。测试会话撤销。未替真实顾客消费、退款、领卡或发送邮件。
- 153 项业务表/配置指纹一致。比较沿用现有 QA 租户及会话/审计排除口径，customer_cards 仅额外排除新增的两个日结字段；剩余次数、原快照等仍参与比较，不宣称全库字节一致。

## 线上与隔离验证

- 四个域名共 32 个页面入口、20 个相关资源内容校验通过；24 项匿名接口访问返回 401。
- 47 项带身份读取检查通过，覆盖卡业务、钱包余额、客户账号、共享目录、存酒、库存和导出。
- 全部 279 个发布文件与线上内容一致，8,361 个旧资源通过备份 inode 校验保留。
- rpb-backend active，PID 2372336，InvocationID 8d062780d6764965b892fbab41bdd702，观察期内身份稳定、无新增 ERROR。
- nginx、PostgreSQL、Redis、Docker 进程身份未变，环境配置哈希一致；未修改服务配置或权限。

Production readiness：GO，已部署。桌面及手机窄屏布局使用演示数据验证；本轮不冒充微信/手机相机或真实银行支付验收。

## 制品 SHA256

- JAR：320afce90598502ce40b5d45053d3e1bea1f4ade43e7dac173691d837f18f05f
- 前端归档：3409894eb98a5ef7004a7f8d17009abb2183a21fd29416880d58523a97b136e3
- index：f58f92c2dfc9f0c209cdc96b74b79f2f906be6ab930b22df4b9073b4621d6c9d

## 回退

恢复上述备份的旧 JAR、前端入口和版本标记，仅重启 rpb-backend。保留 V086 和所有真实日结/消费事实，不恢复旧数据库覆盖新交易。V086 插入触发器已验证旧版发卡 SQL 兼容；旧程序回退后停止执行新日结逻辑。如需纠正已记日结量，另行审计补偿，不删除账目。

证据：本地 target/customer-cards-daily-expiry-release-20261027/，服务器 /home/ubuntu/customer-cards-daily-expiry-release-20261027/，含 final-check.json、迁移证明、QA 证明、清理清单与发布日志。
