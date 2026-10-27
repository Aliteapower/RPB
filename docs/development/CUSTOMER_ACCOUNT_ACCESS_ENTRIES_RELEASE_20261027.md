# 顾客激活码及密码找回邮件设置入口发布

2026-10-27（Asia/Singapore），用户明确授权“提交部署”。

## Production Readiness

GO。97项前端回归、vue-tsc/Vite及差异检查通过；真实组件静态夹具确认入口、邮件独立加载及319px中英文无溢出。沿用现有tenant.admin.manage、租户管理员与门店范围，管理员入口与路由/页面双重控制；邮件只调用原配置接口，未开启新权限或产品能力。激活码仍需当面身份确认，不执行真实签发或发信。

仅发布D:\RPB对应ubuntu@43.134.69.75的/opt/rpb/frontend及FRONTEND_RELEASE_SHA，后端保持b7d2d360、Flyway V085，不重启后端。无API、迁移、依赖、环境或真实SMTP配置变化，Reservation、Queue、Walk-in、Seating、Cleaning业务逻辑不变。严格提交任务文件并核对12项原有无关文件SHA256，从提交后的Git archive校验blob、重新安装依赖/回归/构建。

可用空间约480MiB，当前前端约108MiB，按前端备份、制品和至少200MiB余量检查，不清理既有文件。持有独占发布锁，完整备份前端和标记，保留旧哈希资源后原子切换index；失败恢复备份index及标记，不重启后端或回写数据库。验证新增邮件/顾客入口、公共资源、匿名及跨店拒绝、QA只读邮件配置（确认无明文密码字段）、原商品/存酒/Excel、服务身份、环境及业务指纹。专用QA会话finally撤销。

## 发布结果

功能提交0399cc640ffe4dfe6b43a975d60155afda242566已推送并部署前端；后端保持b7d2d360c142e48c0e32e3b754a9da350d1b6bda，没有打包或重启。不可变前端/opt/rpb/releases/20261027-customer-account-access-entries-0399cc64，完整备份/opt/rpb/backups/20261027-customer-account-access-entries-0399cc64-before，备份逐项与原目录一致。本轮无数据库备份、迁移、恢复或SMTP写入。

干净Git archive核对2,880个blob，npm ci、97项前端回归与vue-tsc/Vite成功。12项无关工作区文件SHA256与发布前一致，未带入提交和制品。前端归档SHA256为8ba7612efc06c7ae2a686cd7c8c8639172f1f75dfaeb438c18c64329cc5489fd，index为14094c41860e8162aebae340fc96327eaca4acb6d0006bd2b7e7d2439200f70b。

四个域名88个页面入口返回新index，含独立邮件设置、顾客管理、钱包更多/设置/卡项和公开预约配置；39项公开资源逐字节一致，80项匿名拒绝及4项匿名空目录通过。41项QA认证只读检查通过，新加共享邮件配置读取、顾客身份拒绝及跨店拒绝，并确认响应没有明文密码字段。原商品查询、当前寄存及3种Excel导出通过；临时QA会话finally撤销，没有真实激活码签发、SMTP配置变更或邮件发送。

272个前端制品文件与正式目录一致，7,713个旧静态资源字节保持不变。153项业务及配置指纹在发布前、发布后及最终复核一致，口径排除QA租户、会话、限流和审计等易变数据，不代表整库字节相等。后端、Nginx、PostgreSQL、Redis、Docker状态及进程身份、环境文件均不变；Flyway仍085|85|0。后端active，PID2282248、InvocationID139b71f87eda4f509d8e8d5483ca1f9a、NRestarts0；观察及最终复核无新增ERROR。

GO，发布及最终验证完成，未触发回退。后续发布记录提交不改变线上前端功能SHA。证据target/customer-account-access-entries-release-20261027/；服务器证据/home/ubuntu/customer-account-access-entries-release-20261027/。真实邮件投递和手机/微信实机未验收。
