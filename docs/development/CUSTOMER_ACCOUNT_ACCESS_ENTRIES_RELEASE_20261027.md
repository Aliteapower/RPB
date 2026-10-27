# 顾客激活码及密码找回邮件设置入口发布

2026-10-27（Asia/Singapore），用户明确授权“提交部署”。

## Production Readiness

GO。97项前端回归、vue-tsc/Vite及差异检查通过；真实组件静态夹具确认入口、邮件独立加载及319px中英文无溢出。沿用现有tenant.admin.manage、租户管理员与门店范围，管理员入口与路由/页面双重控制；邮件只调用原配置接口，未开启新权限或产品能力。激活码仍需当面身份确认，不执行真实签发或发信。

仅发布D:\RPB对应ubuntu@43.134.69.75的/opt/rpb/frontend及FRONTEND_RELEASE_SHA，后端保持b7d2d360、Flyway V085，不重启后端。无API、迁移、依赖、环境或真实SMTP配置变化，Reservation、Queue、Walk-in、Seating、Cleaning业务逻辑不变。严格提交任务文件并核对12项原有无关文件SHA256，从提交后的Git archive校验blob、重新安装依赖/回归/构建。

可用空间约480MiB，当前前端约108MiB，按前端备份、制品和至少200MiB余量检查，不清理既有文件。持有独占发布锁，完整备份前端和标记，保留旧哈希资源后原子切换index；失败恢复备份index及标记，不重启后端或回写数据库。验证新增邮件/顾客入口、公共资源、匿名及跨店拒绝、QA只读邮件配置（确认无明文密码字段）、原商品/存酒/Excel、服务身份、环境及业务指纹。专用QA会话finally撤销。

## 发布结果

完成后补充功能SHA、备份路径及上线证据。证据target/customer-account-access-entries-release-20261027/。
