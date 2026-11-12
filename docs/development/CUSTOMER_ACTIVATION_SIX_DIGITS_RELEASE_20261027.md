# 六位数字到店激活码及紧凑页面生产发布

2026-10-27（Asia/Singapore），用户明确授权“提交部署”。

## Production Readiness

GO。功能提交 e1792292e872d1eb8fa38eb5428156b0eb099cdf 已推送；严格提交 23 个本轮文件，保留 12 项原有无关文件的 SHA256。从该提交生成禁用自动 CRLF 转换的 Git archive，逐项核对 2,882 个 blob；首次默认 CRLF 归档被校验拒绝，未用于打包。干净目录 npm ci、100 项前端回归、vue-tsc/Vite、Maven package 通过。沿用同一业务代码此前已核实的后端 40 项测试，找回密码 6 位正向测试另外复跑通过。

JAR 与原线上 b7d2d360 对比，只有 CustomerActivationService（含 Issued 嵌套类）、CustomerPasswordAuthService、CustomerSecurityEmailService 的四项 class 差异，无迁移、配置、依赖和其他业务逻辑变化。新激活码为 6 位随机数字，旧字母数字码拒绝；顾客自设密码最低 6 字符，保留已有长密码。原权限、范围、10 分钟、5 次错误、限流、哈希和历史初始凭据禁登保持。各产品线共用精简页面，旧入口重定向。

## 制品和备份

前后端功能 SHA 均为 e1792292e872d1eb8fa38eb5428156b0eb099cdf。

- 后端 JAR SHA256：0c11eee8cee251239b9460fe1bd467cc2f598dea2cf606538f4a4554917ca7b9
- 前端归档 SHA256：e40fc90c53106619444dda275f066e06a0f2296b238e27d224d404ba04bcdbf6
- index SHA256：1f56d500b01a1e6df3434a1d01203f389d259d67955e66c3e7bec7f2dfbd2caf
- 不可变目录：/opt/rpb/releases/20261027-customer-activation-six-digits-e1792292
- 备份目录：/opt/rpb/backups/20261027-customer-activation-six-digits-e1792292-before

服务器空间紧张，采用同盘硬链接保留旧 JAR 和完整前端文件，切换时仅原子替换路径，新增哈希资源不覆盖旧资源；未删除既有备份。备份逐项比对，含旧发布标记、环境文件和停服切换时的数据库 dump，pg_restore 目录校验通过。环境和数据库迁移不修改。

回退保护：新后端尚未健康时恢复旧 JAR 并启动；新后端健康后若前端或验收失败，恢复旧前端但保留支持 6 位密码的后端，避免已新设 6 位密码无法登录。需要回退后端时须保留本版密码长度规则或安排找回，不能盲目回到至少 8 位规则。本次未触发回退。

## 线上验证结果

- 四个域名共 92 个页面入口返回新版 index，含新激活入口与各产品线入口；44 个公开静态资源字节一致。
- 84 项匿名拒绝（401 共 72，403 共 12），含到店激活码签发；4 项匿名产品目录保持空。
- QA 会话 41 项认证只读检查通过，含顾客查询、邮件配置无密码泄漏、商品、当前寄存及三个 Excel 导出、租户/门店拒绝；会话 finally 撤销。
- 正式目录 278 项前端文件和新后端哈希一致，备份旧 index/JAR 哈希复核一致。
- 153 项业务及配置指纹在切换前、切换后、最终复核保持；排除专用 QA 租户、会话、限流和审计等易变数据，不代表整库字节相等。
- Flyway 085|85|0，无迁移；环境配置和 Nginx/PostgreSQL/Redis/Docker 身份未变。
- 后端 active，PID 2305763，InvocationID c139cd76bb654f018d67fba4219c183b，NRestarts 0；最终复核无 ERROR，进程身份稳定。

没有对真实顾客签发激活码、修改密码、修改 SMTP 或发信。真实手机/微信实机及真实邮件投递不在本轮生产验收范围；中英文手机和桌面演示布局已在实现阶段核实。

GO，生产发布与最终复核完成。发布记录提交不改变线上功能 SHA。证据：target/customer-activation-six-digit-release-20261027/；服务器 /home/ubuntu/customer-activation-six-digit-release-20261027/。
