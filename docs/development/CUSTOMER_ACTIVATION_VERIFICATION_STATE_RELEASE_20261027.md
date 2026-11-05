# 首次核实状态生产发布

2026-10-27（Asia/Singapore），用户明确授权提交部署。

## Production Readiness

GO。功能提交 11778be10b5be1ae9617fc09c6d16bf7c5e81ae2 已推送，仅含21项本轮文件。原12项无关工作区文件SHA256保持。干净Git archive逐项核对2,889个blob，npm ci、104项前端回归、vue-tsc/Vite及Maven package成功；同一业务代码的47项后端测试已通过（31项PostgreSQL认证集成、5项顾客隐私API、7项密码服务、4项安全邮件服务）。本地运行只使用工作区指针61644端口和随机隔离schema。

核实状态由customer-auth批量只读查询提供，tenant-admin在原顾客列表/详情组合activationState。前端共用按钮组件：已核实置灰，已核实待设密者保留重新生成，不重复勾选，已激活不可再发码。状态未知禁止操作；首次成功发码立即标记，刷新失败不退回未核实。原角色、门店、租户、签发资格、密码保护、码有效期/限流/失败次数及重发规则保持。没有数据库迁移、新权限或环境变化。

新JAR与原线上e1792292相比，仅9项本轮认证状态查询/响应/Controller类及相关嵌套类变化，无无关业务代码差异。前端归档278项文件与构建目录逐字节一致。新增字段向后兼容，旧核实记录直接读取，无数据回填。

## 制品与回退保护

前后端功能SHA均为 11778be10b5be1ae9617fc09c6d16bf7c5e81ae2。
JAR SHA256：9b634537442e438f48c5068431733970d5c403aaaed868ca90b3c51d9ff27889。
前端归档SHA256：27f7321041be25fb0cebb9b30383c63c0015351708096ff4bcdcd68336eb198f。
index SHA256：4ab7d5e6d1e4a2a596b8ad6bd2424dba19ca62cb98978caba646801d62f49030。
不可变目录：/opt/rpb/releases/20261027-customer-activation-state-11778be1。
备份目录：/opt/rpb/backups/20261027-customer-activation-state-11778be1-before。

服务器可用空间约300MiB，采用同盘硬链接备份完整前端及旧JAR，切换均原子替换路径，资源只追加。保留旧前端0c432116、旧后端e1792292、环境及发布标记，停服切换时pg_dump备份并通过pg_restore目录检查。不删除既有备份。独占发布锁；启动失败恢复旧JAR，健康启动后若前端/验证失败则恢复旧前端并保留兼容的后端；不回写数据库。本次未触发回退。

## 线上验收

- 92个公开页面入口返回新版index，48项公开静态资源字节一致；84项匿名权限拒绝（72项401、12项403），4项匿名产品目录为空。
- 42项QA认证只读检查通过；2条QA顾客列表状态及详情真实状态确认，不包含密码、哈希或激活码字段。原商品、寄存清单、3种Excel导出、顾客身份与跨店拒绝保持。QA会话finally撤销。
- 278项正式前端文件与制品一致，新JAR哈希和发布标记一致，备份旧JAR/index复核一致。
- 153项业务/配置指纹在切换前、切换后及最终复核一致；排除专用QA租户、会话、限流和审计等易变数据，不代表整库字节相等。
- Flyway 085|85|0，无迁移。环境和Nginx/PostgreSQL/Redis/Docker身份不变。
- 后端active，PID2317868、InvocationID 06f7f80738fb41ffbf41d7f4fb7b808f、NRestarts0；观察与最终复核无ERROR，进程身份稳定。

没有给真实顾客发码、改密或修改SMTP及发送邮件。实现阶段只读核对用户指定顾客确有核实及设密记录，未修改该顾客。本轮生产验收不代替真实手机/微信实机和邮件投递测试。

GO，提交、部署及最终验证完成。后续发布记录提交不改变线上功能SHA。证据目录target/customer-activation-status-release-20261027/；服务器/home/ubuntu/customer-activation-status-release-20261027/。
