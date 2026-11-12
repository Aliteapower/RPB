# 个人钱包退出登录验收（2026-10-24）

状态：开发与本地验证完成，未提交、未推送、未部署。保留同一工作区收款科目配置及其他已有改动。

## 实现及接口

- 个人钱包首页“修改登录密码”右侧新增“退出登录”，支持中英文及移动端布局。
- 复用 `POST /api/v1/public/customer-auth/logout`，撤销当前顾客会话并清除顾客 Cookie；不调用员工退出接口。
- 成功后清空当前钱包资料并回到钱包登录状态；阻止退出前发出的延迟刷新恢复旧资料，重复点击只发起一次请求。
- 网络失败保留当前状态、展示失败提示并允许重试，不误报退出成功。
- 共用修改密码组件仅增加可选操作插槽，其他产品不自动增加钱包退出动作。

## 验证证据

- `target/wallet-logout-red.log`：新增退出行为测试先失败。
- `target/wallet-logout-focused.log`：成功、失败重试及延迟响应场景通过。
- `target/wallet-logout-regression.log`：钱包及顾客密码相关前端测试 168/168 通过；旧 workspace 测试补齐国际化插件安装。
- `target/wallet-logout-build.log`：`npm run build` 通过，包含 Vue TypeScript 检查。
- 本地 PostgreSQL 使用 `target/local-postgres-current.txt` 指定的 61644 端口；复用隔离 schema `wallet_methods_20261024`，未使用生产数据。
- 移动端浏览器实际点击退出，钱包余额及资料消失，出现顾客登录表单；截图 `target/wallet-logout-button-mobile.png`、`target/wallet-logout-signed-out.png`。
- `target/wallet-logout-api-proof.json`：退出后的原顾客令牌访问钱包返回 401；原员工会话仍返回 200。
- `git diff --check` 通过；HEAD 保持 `ef4565d2`，暂存区为空。

## 审查、影响与回退

代码审查结论：APPROVE。复用现有顾客认证边界，未增加交易、权限或数据库逻辑。新增接口调用使用凭据 Cookie；未记录令牌，不影响员工登录。

本项无服务端接口变更、无迁移、无新增权限。工作区另有收款科目功能及 V068，属于此前独立改动，不应误认为本项新增迁移。

回退仅撤销本项前端按钮、调用和状态处理，保留现有服务端退出接口及其他工作区改动。未执行生产环境或真实手机验证；本轮证据来自本地隔离数据及移动尺寸浏览器。
