# 顾客账号导航样式生产发布

2026-10-27（Asia/Singapore），用户明确授权“提交部署”。

## Readiness

GO。仅前端样式及插槽接入，无 API、权限、数据库迁移、依赖或环境配置变化。到店激活码、忘记密码邮件设置复用租户侧栏 nav-item，桌面各占一行；手机沿用原横向导航。原共享入口仍负责权限、门店及地址生成，其他产品线入口保持原样。

任务提交 0c43211644e0121d7b87beed9c32bf672c120bf5 已推送，仅含两个 Vue 文件及发布说明。保留 12 项无关工作区文件并核对 SHA256 不变。实现阶段 89 项回归和桌面/390px 手机真实组件验收通过；从提交生成干净 Git archive 核对 2,884 个 blob，再运行 npm ci、100 项前端回归、vue-tsc/Vite。首次打包被消息中断导致输出不完整，未上传部署；使用新的 source-verified 目录重新打包，所有日志无 NUL、归档 278 个文件与构建目录逐字节一致后才发布。

## 制品与切换

前端功能 SHA：0c43211644e0121d7b87beed9c32bf672c120bf5。后端保持 e1792292e872d1eb8fa38eb5428156b0eb099cdf，JAR 哈希保持 0c11eee8cee251239b9460fe1bd467cc2f598dea2cf606538f4a4554917ca7b9，无重启。

前端归档 SHA256：83cf6535f8d58c3821eddea88e5d6cc349ede57667f432c1f0ef80d94cbc170f。
index SHA256：d75dd9053e3f02ec3787313afcc5c75efc3bed791218885024326c4b695f9b84。
不可变目录：/opt/rpb/releases/20261027-customer-access-nav-style-0c432116。
备份目录：/opt/rpb/backups/20261027-customer-access-nav-style-0c432116-before。

持有独占发布锁，完整前端同盘硬链接备份并逐项比对；资源仅追加、不覆盖旧哈希资源，index 原子替换。失败自动恢复备份 index 和 FRONTEND_RELEASE_SHA，无需后端或数据库回退。未清理既有制品和备份，本次未触发回退。

## 最终验收

- 92 个公开页面入口返回新 index，44 个静态资源字节一致，84 项匿名拒绝（72 项401、12 项403），4 项匿名产品目录为空。
- 41 项 QA 认证只读检查成功，含共享顾客/邮件配置读取、跨店和顾客身份拒绝、商品、当前寄存及3种 Excel 导出。专用 QA 会话 finally 撤销，没有真实激活码签发、密码修改、SMTP 配置更改或发信。
- 278 项新版前端文件与正式目录一致，7,982 个旧静态资源保留且字节一致。
- 153 项业务及配置指纹在发布前、发布后、最终复核一致；范围排除 QA 租户、会话、限流和审计等易变数据，不代表整库字节相等。
- Flyway 085|85|0。后端 PID2305763、InvocationID c139cd76bb654f018d67fba4219c183b、NRestarts0；后端和 Nginx/PostgreSQL/Redis/Docker 身份、环境文件不变，观察期无新增 ERROR。

GO，提交、部署和最终复核完成。后续发布记录提交不改变线上功能 SHA。证据目录 target/customer-access-nav-style-release-20261027/，服务器 /home/ubuntu/customer-access-nav-style-release-20261027/。本轮没有新增真实手机/微信实机验收。
