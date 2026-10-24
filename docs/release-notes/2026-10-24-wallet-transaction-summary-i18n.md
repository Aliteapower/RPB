# 交易默认摘要国际化补漏

2026-10-24；D:\RPB；修复用户截图中交易凭证在英文下仍显示“消费”。

## 原因与修复

此前事件国际化未覆盖summary。SQL将系统默认“消费／充值”和用户说明放入同一summary字段，Vue原样显示，不能根据中文内容直接替换，否则会误译恰好相同的真实备注。

只读投影不再生成中文默认summary，无说明的bill和topup返回null；复用前端payment/topup语言键生成默认摘要。列表、交易详情及员工／顾客内嵌交易凭证复用单一展示函数。真实订单说明、个人付款说明、退款原因及积分备注原样保留，包括显式填写的“消费／充值”。

## 验证与审查

- 先添加组件失败回归，再修复；190项钱包前端测试及vue-tsc、Vite构建通过。
- 真实PostgreSQL交易测试19项通过，新增验证列表和详情默认null、个人备注“消费”原值；原有权限、跨租户、门店、顾客及关联记录隔离回归通过。
- 本地数据库严格使用target/local-postgres-current.txt所指61644端口。初次后端验证发现运行环境停止，按项目指南启动原集群后重新验证；隔离schema在测试后清除。
- API合约说明summary的null含义；类型、路由、错误码、权限和幂等语义不变。旧前端已支持null，仅可能短暂省略默认摘要，更新前端后恢复语言化展示。
- 数据库审查：仅移除只读投影默认字符串，tenant/store过滤、说明来源及查询结构不变；无表结构、迁移、数据回写或配置变更。
- code-review：职责位于既有transactions模块，统一展示函数不依赖语言状态快照；未知／自定义摘要不做翻译，语言切换实时生效。

## 发布与回退

按本会话已有“提交部署”授权发布修复。先备份数据库、JAR、前端及版本标记，校验迁移不变；后端就绪并核对原业务数据后原子切换前端。回退恢复原JAR、index及版本标记，不恢复数据库，不改已入账业务数据。

剩余限制：不代替用户执行真实生产资金或积分交易，手机内嵌浏览器已登录交互需现场复核。未知业务摘要按原文保留。

## 已发布证据

- 功能提交 `e81919d18a21e4b8a2c26ebb1c68a3ed9d18cec0` 已推送，生产前后端版本均为该提交；发布锁保护，完整备份 `/opt/rpb/backups/20261024-wallet-summary-i18n-e81919d1-before`，发布目录 `/opt/rpb/releases/20261024-wallet-summary-i18n-e81919d1`。
- 干净Git归档核对2460个Git文件，源码与已测试状态一致；干净目录190项钱包前端测试及vue-tsc/Vite通过。主工作区19项真实PostgreSQL交易测试和17项API／安全测试通过；源码一致性确认后复用后端测试结果，干净目录Maven package通过。
- JAR SHA256 `d08193a1b30faba0c6a11c1cbba87f56b3d891c9bfae2deffdb84284d624b8f4`；前端包 `f6cafaa297d709db5d840b39f5551c4d3e6964ec2601075552a40a8176ee85d2`；index `5490ef07b1d2215e6a0cafe47d3640570a5b5a7d5038f02f66b4ded113bcc4ff`。
- 逐项比对旧／新JAR：仅TransactionProjection.class及引用内联SQL常量的TransactionRepository.class变化；Repository.class在仅替换预期SQL默认文本后与新class二进制完全一致（3个UTF8常量）。73个资源仅CRLF/LF差异，配置与迁移语义不变。Flyway070/70/0保持，无新增迁移。
- 停止边界数据库备份965293字节，pg_restore目录验证通过；切换前后原业务表数量／数据指纹一致。后端PID1207534、InvocationID da807a49602341a58e132e07eed426ea、NRestarts0、active；nginx、PostgreSQL、Redis、Docker身份保持；临时RPB流量阻断已解除。
- 4个域名、12个页面入口、220个静态资源逐字节匹配发布制品；匿名auth/me401，当前后端Invocation错误日志为空。
- 对用户截图账单c7b2d6d7-acf9-46da-a59f-b1d205496998仅执行只读查询，确认cancelled、无自定义说明、默认summary为null；没有修改该账单或代替用户办理任何交易。
- 证据：target/wallet-summary-i18n-artifacts.json、wallet-summary-i18n-clean-tests.log、wallet-summary-i18n-clean-build.log、wallet-summary-i18n-backend-tests.log、wallet-summary-i18n-api-tests.log、wallet-summary-i18n-repository-constant-proof.json、wallet-summary-i18n-deploy.log、wallet-summary-i18n-assets-proof.json、wallet-summary-i18n-final.log、wallet-summary-i18n-final-isolation.log。
