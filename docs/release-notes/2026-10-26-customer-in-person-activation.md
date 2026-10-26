# 到店核实后一次性激活（本地待发布）

2026-10-26，`D:\RPB`，基线 `558d7ec3`。用户选择暂不接短信、没有已验证电邮时由授权员工当面核实并交付短时一次性激活码。本轮未提交、推送或部署生产。

## 改动与使用方式

1. 员工进入现有租户顾客管理或钱包共享顾客管理，选择顾客，点击“核实并生成激活码”。权限沿用 tenant_admin + tenant.admin.manage + 授权门店，不向普通店员开放，也不依赖某一钱包产品的开通。
2. “已当面核实顾客本人”默认不勾选，确认后才能发码；只适用于活跃、有手机号、尚未设密且没有已验证认证电邮的顾客。已设密/已有已验证电邮不能通过此入口覆盖凭据。
3. 随机 12 位去歧义激活码，10 分钟有效、5 次错误锁定，只在此次响应显示。数据库只存盐化 SHA-256 哈希；审计存员工、租户、门店、顾客和事件，不记录码。重发废止该租户顾客全部旧码。离开或切换顾客/门店清除显示；晚到响应不显示在新门店/顾客下，页面到期清除明文，不进入 URL 或本地存储。
4. 顾客在共享登录入口选择“首次激活”，输入手机号、当面取得的激活码，自行设置并确认密码。成功后回登录页，不自动登录。员工不能查看顾客的新密码。
5. 手机号后六位登录和改密 fallback 均关闭，不再生成初始密码受限会话。旧受限会话不能调用改密。既有自设密码、普通改密、已验证电邮找回保留。

激活持有顾客行锁，密码保存、设密状态、旧会话和安全码撤销、所有激活码消费及审计在同一事务完成；失败次数独立于业务回滚保存。发码也持同一顾客锁，重复发码会换码，不是重放读取明文；网络不确定时只允许员工主动重发，不自动重试。

## API / DB 审查

- 私有 POST `/api/v1/stores/{storeId}/tenant-admin/customers/{customerId}/activation-codes`；公开 POST `/api/v1/public/customer-auth/activate`。请求/响应和错误见 [合约](../api/2026-10-26-customer-in-person-activation.md)。均要求专用请求头并拒绝跨站写入，响应 private,no-store / no-referrer，无查询明文接口。
- V083 增加带 tenant/store/customer/issuer 复合 FK、限次及到期约束和索引的激活码表，并撤销旧初始凭据相关会话。V082 和更早迁移不重写，不删除旧密码哈希，不改顾客资料、资产、余额或历史同意。
- 认证与激活留在 customer-auth，新服务、端口、JDBC 适配器、控制器各有职责；顾客管理共用同一个发码组件。控制器不访问业务 Repository。沿用现有顾客管理角色/权限，本轮不新增员工权限或跨产品授权。
- 独立只读审查结论 APPROVE，未发现新的阻断问题；已核对权限、scope、已有凭据保护、盐化代码、事务/锁/重放及 UI 明文隔离。此结论不代替测试执行证据。

## 验证

**后端 15 类共 88 项通过，0 失败、0 错误、0 跳过；其中 27 项为真实 PostgreSQL 顾客认证/激活/找回集成测试。前端 118 项通过；构建及 diff 检查通过。**

测试使用 workspace 的 `target/local-postgres-current.txt` 指定 `127.0.0.1:61644/postgres`、postgres/空密码；迁移和业务操作仅在生成的测试 schema 内执行，没有向原业务 schema 应用 V083。

- 修复前先验证两个关闭初始登录的测试失败：`target/customer-activation-red.log`；修复后通过。
- `mvn -q '-Dtest=SharedCustomerPasswordPostgresTest,CustomerPasswordAuthServiceTest,CustomerPasswordSecurityMigrationTest,CustomerAuthApplicationServiceTest,CustomerSecurityEmailServiceTest,CustomerRestrictedSessionTest,CustomerPrivacyApiTest,CustomerRegistrationServiceTest,CustomerSelfRegistrationServiceTest,CustomerSelfRegistrationApiTest,CustomerPrivacyConsentPostgresTest,WalletApiIdentityTest,WalletCustomerProofApiTest,WalletCollectionProofApiTest,WalletRequestSecurityTest' test`：`target/customer-activation-final-backend.log`。
- 增加码长度保护和对应错误计数断言后，只复跑受影响类：`mvn -q '-Dtest=SharedCustomerPasswordPostgresTest' test`，`target/customer-activation-final-http.log`，27 项通过；其余同状态结果复用，总数按各类最新 XML 汇总，不重复累加。
- `node --test --test-concurrency=1 src/test/frontend/customer-password.test.mjs src/test/frontend/wallet-marketing-points.test.mjs src/test/frontend/wallet-account-menu.test.mjs`：`target/customer-activation-ui.log`，118 项通过。
- `npm run build`：`target/customer-activation-final-build.log`，通过；`git diff --check` 通过，保留既有 LF/CRLF 提示。

覆盖：原初始/已保存初始密码拒绝、换手机号仍拒绝、自设密码保留、角色/权限/匿名/门店/租户拒绝、确认缺失、已验证电邮拒绝、只回显本次码、HTTP 发码/设密不自动登录、重发/错码/超长码/过期/重放/并发一次成功、跨门店/顾客/租户、审计失败完整回滚、既有余额不变、邮箱绑定/找回及撤销、V082→V083 兼容和自设密码会话保留。前端覆盖确认默认关闭、重复提交、密码确认、切换门店/顾客和晚到响应、共享钱包回归。

原有 Mockito/JVM 动态 agent 与测试开发密码提示保持，负例 GET 发码产生预期 405 警告。无真实邮件、短信发送；真实手机/浏览器视觉验收及员工现场核实动作未执行，不能声称已经实际核实过顾客。

## 风险、发布与回滚

- 仍需员工真实当面核实和交付；系统保存的是员工确认及发码证据，不能自动证明当面核实发生。禁止仅凭电话报手机号发码、将码留在备注、截图或转发。
- 已设密但没有可用电邮的顾客不能被当作“首次激活”绕过恢复身份；遗失已验证邮箱后的改绑、已设密无邮箱的人工恢复是独立流程，本轮不开放覆盖密码。
- 发行码历史及限流记录的保留清理仍需后续生命周期治理。其他 PDPA 审计问题不因激活功能全部解决。
- 后续发布需按 V082→V083 配套后端/前端；旧初始密码登录方式停用，用户需刷新页面并到店激活。没有生产授权，本轮未执行。
- 回滚不得恢复可推导初始密码登录。保留新增数据、凭据、已消费/撤销状态和审计，不恢复旧会话或删除原业务资料；回退旧应用会重新引入此前风险，不能作为无影响回滚。

此前的“初始密码登录后强制改密”发布说明反映前一轮实现，本合约和本说明是最新规则。
