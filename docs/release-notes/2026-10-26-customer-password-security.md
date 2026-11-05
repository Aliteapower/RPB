# 顾客首次改密、电邮找回与同意规则统一

日期：2026-10-26；项目 `D:\RPB`；基线 `558d7ec3`；本地实现，未提交、推送或发布生产。

## 行为

- 保留手机号后六位初始密码；初始登录只创建 15 分钟受限会话，隐藏资料字段，不能访问钱包等顾客业务。完成新密码保存后撤销旧会话并发放普通会话。刷新可继续改密，会话过期返回登录。
- 强制改密标志保存在凭据及会话中，改手机号不能使旧初始凭据变成普通密码。
- 账户设置可发送验证码验证绑定找回电邮；仅已验证的认证电邮可找回，顾客资料中的手填 email 不作为身份凭据。未绑定者不能以手机号后六位恢复自设密码。
- 匿名找回发送返回统一成功结构，避免账户枚举；验证码 10 分钟有效、最多 5 次校验失败、哈希保存、单次消费。找回成功不自动登录。普通改密及找回均撤销旧会话、绑定码和找回码。
- 发送使用现有门店 SMTP 配置，邮件标明绑定/重置用途；客户端和密码安全接口添加同源请求头约束。
- 同意正文保留已确认的“包含营销推送同意或钱包扣款授权”口径，当前版本统一为 `customer-profile-v2`。活跃 API 合约及测试一致，旧版表单拒绝，历史 v1 快照不变；不跳过具体交易的权限、余额和幂等校验。

## 迁移与权限

V082 新增凭据/会话强制改密标志及用途验证码表，包含租户、门店、顾客、认证账户复合外键。历史版本 0 凭据保守标记为待改密，原有显式改密版本可正常登录。迁移一次性撤销具备密码凭据顾客的活动会话，包含该顾客的旧邮箱/OAuth 会话；不改原密码哈希、顾客资料、余额和历史同意记录。

认证功能留在 customer-auth；新端点不以钱包 App Gate 为前提。实际业务仍走既有租户、门店、所有权及 App Gate 校验。没有增加员工权限，无依赖变更。原有工作区钱包组件改动未被本轮覆盖或回滚。

## 验证

18 个相关后端测试类汇总 **99 项通过，0 失败、0 错误、0 跳过**；包括 22 项真实 PostgreSQL 顾客密码/找回流程测试。使用 `target/local-postgres-current.txt` 指定的 `127.0.0.1:61644/postgres`，创建隔离测试 schema，未向原业务 schema 应用 V082。

覆盖初始密码与自设密码、受限会话业务拒绝和刷新识别、旧会话撤销、换手机号、跨租户/门店/顾客、认证账户复合外键、手填电邮不能找回、绑定冲突、错码限次且失败计数提交、过期、重发、重放、并发仅一次成功、普通改密撤销旧码、持久化失败原子回滚、匿名响应一致、真实 HTTP 绑定/重置接口链、迁移保留原密码及顾客、v1 同意快照不改。

命令及证据：

- `mvn -q '-Dtest=SharedCustomerPasswordPostgresTest,CustomerPasswordAuthServiceTest,CustomerSecurityEmailServiceTest,CustomerRestrictedSessionTest,CustomerPasswordSecurityMigrationTest,CustomerAuthApplicationServiceTest,CustomerRegistrationServiceTest,CustomerSelfRegistrationServiceTest,CustomerPrivacyApiTest,CustomerSelfRegistrationApiTest,CustomerPrivacyConsentPostgresTest,WalletApiIdentityTest,WalletRequestSecurityTest,WalletCustomerProofApiTest,WalletCollectionProofApiTest,QueueDisplayPrivacyTest,QueueReadLogPrivacyTest,QueueDisplayControllerTest' test`：`target/customer-security-verified-backend.log`。
- 最新补充完整 HTTP 场景后执行 `mvn -q '-Dtest=SharedCustomerPasswordPostgresTest' test`：`target/customer-security-http-final.log`，22 项通过；其余同状态结果复用。总数取各类最新 Surefire XML，不重复累加同一测试。
- `node --test --test-concurrency=1 src/test/frontend/customer-password.test.mjs src/test/frontend/wallet-marketing-points.test.mjs src/test/frontend/wallet-account-menu.test.mjs`：**117 项通过**，`target/customer-security-final-ui.log`。包含强制改密不提前放行业务、刷新、会话过期、忘记密码、电邮验证、确认不一致、重复提交及共享钱包回归。
- `npm run build` 通过：`target/customer-security-final-build.log`；之后只修改测试及文档，生产前端代码状态未变。
- `git diff --check` 通过；现有 LF/CRLF 提示不影响检查。Mockito/JVM 动态 agent 和测试生成开发密码提示为既有环境提示。

中断恢复后 PostgreSQL 未运行，首次复测曾因 connection refused 失败；按项目重启指南启动指针原数据目录后复测通过，没有改用系统 5432。原 Vite SSR 测试加载/退出停滞，本次顾客测试改用与现有钱包测试相同的 esbuild + Vue SFC 编译方式，全部断言保留并正常退出。

## 审查

已完成项目 API/数据库/TDD/代码审查，并由独立只读审查复查。两项必要意见均修复：正常改密使旧安全码失效；强制改密会话失效能返回登录。修复后仅复查受影响路径，无新增阻断项。

## 风险与未验证范围

- 真实 SMTP 投递、收件箱及真实浏览器/手机视觉验收未执行；测试邮件通过 mock 接收，不曾向顾客发送测试邮件。门店必须配置可用发信渠道，顾客需验证电邮后才能找回。
- 保留可推导初始密码，仍存在首次激活前被抢先登录并改密的风险；强制改密不证明手机号所有权。
- 同意规则一致性不等于营销和支付授权的法律有效性证明；保留期限、撤回、DPO、供应商及生产配置等审计待办仍在。
- 未提供遗失已绑定邮箱后的自动改绑流程，避免仅凭资料或初始密码替换恢复身份。
- 验证码过期/消费不等于清除数据库记录；安全码及限流数据的保留清理应纳入后续生命周期治理。

## 上线与回滚约束

此轮没有生产发布授权。后续发布须先应用 V082，再配套新后端和前端，验证门店 SMTP；已有顾客可能需重新登录。不要单独回滚前端到不支持强制改密或请求头的版本。若回退应用，保留新增列/表、历史同意快照及新密码；不恢复已撤销会话，不回填初始密码。旧后端会失去本轮受限会话保护，不能把回滚当作无安全影响操作。

详细接口见 [密码安全合约](../api/2026-10-26-customer-password-security.md)，全系统剩余问题见 [审计报告](../development/DATA_SECURITY_PDPA_AUDIT_20261026.md)。
