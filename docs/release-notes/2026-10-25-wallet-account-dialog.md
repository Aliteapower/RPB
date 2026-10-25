# 钱包首页账户弹窗

日期：2026-10-25；已按用户“提交部署”授权发布。

- 点击首页顶部“我的钱包 / 门店名称”打开账户设置，可修改登录密码或退出登录。
- 发行主体、充值说明及“统一资料”保留首页原位，内容未改。
- 复用平台顾客密码修改、退出登录接口。提交期间阻止重复操作和关闭弹窗；退出期间禁止修改密码。关闭后清空密码输入。
- 中文和英文文案同步，窄屏按钮纵向排列，弹窗内容支持滚动。

验证：109 项组件及工作区测试、TypeScript 检查及前端生产构建通过；首页资料区域与原版本逐字比较一致。浏览器预览工具连续超时，桌面视觉、移动端键盘与真机效果尚未确认。

无数据库迁移、API、AppGate 权限及租户范围变更。共享密码组件新增可选禁用参数与忙碌事件，其他入口沿用原有行为。

回退：回退本次账户弹窗、首页入口、中文/英文本地化及共享密码组件的对应修改，即恢复首页底部操作入口；无需回退数据库。

## 生产发布验证

- 功能提交 `3fa2acc814703dcd084166d30d803e7f9a6e464c` 已推送 `origin/codex/paynow-payment-product-line-staging`，包含本轮12个文件；同时发布“快捷收款记录”中文名称。原有8个钱包组件换行改动及未跟踪材料保留，未进入提交或制品。
- 干净Git归档核对2483个文件、1989个源码文件与验证工作区一致，独立安装锁定依赖，109项前端测试、类型检查、Vite生产构建通过。
- 前端版本标记已更新为功能提交；后端保持 `137096e1a36769398bd8a46507cf09822576faf6`，JAR SHA256保持 `f002960bc5914524438d49896886a2f49967584bfb19445217520862ebd4fa95`，没有后端部署或重启。
- 发布目录 `/opt/rpb/releases/20261025-wallet-account-3fa2acc8`；备份 `/opt/rpb/backups/20261025-wallet-account-3fa2acc8-before`，包括旧前端、前端版本标记及可读的1082926字节数据库备份。
- 前端归档SHA256 `39ec9237dd44cd83a3fdd787523a2ec440ca5e356b102c064f6565577402eff1`；index SHA256 `616ec890e53fb1630c3f1decd3afd81f35b909215874b0b341ce65462298b625`。
- 持既有发布锁，资源先复制、index原子切换，旧资源保留，脚本包含错误回退；没有触发回退。Flyway保持070/70/0。
- booking、platform.booking、20000000.booking及sx.booking四个域名的12个页面入口均匹配新index；20000000域名全部224个公网资源逐字节匹配制品。匿名auth/me和顾客wallet/me均401。
- rpb-backend保持active、PID1235333、InvocationID `400992ad55714033977735a94e811e68`、NRestarts0；nginx、PostgreSQL、Redis、Docker进程身份和重启计数均不变，发布窗口日志无新增ERROR或Exception。
- 浏览器工具超时的视觉验收限制仍保留，未修改任何真实顾客密码，也未使用生产会话办理交易。

生产回退：持 `/home/ubuntu/rpb-ledger-release.lock`，从上述备份恢复旧index并原子替换，同时恢复 `/opt/rpb/app/FRONTEND_RELEASE_SHA`；旧assets仍在，无需重启后端或恢复数据库。

证据位于 `target/wallet-account-source-proof.json`、`wallet-account-clean-tests.log`、`wallet-account-clean-build.log`、`wallet-account-artifacts.json`、`wallet-account-deploy.log`、`wallet-account-public-proof.json`、`wallet-account-api-proof.json`、`wallet-account-final.log`。
