# 顾客到店激活码与紧凑操作页

日期：2026-10-27。用户已授权提交部署，发布结果另记生产验收记录。

## 变更

- 新激活码为 SecureRandom 生成的 6 位数字字符串，保留前导零。旧 12 位字母数字码一律拒绝，未过期也需重发。
- 顾客激活、登录、改密及已验证邮箱找回统一支持至少 6 字符，包括 6 位纯数字；已有长密码保持。历史初始凭据仍受 password_change_required 标记限制，不恢复手机号后六位自动登录。
- 所有产品线共用独立的紧凑激活页面：顾客查询、分页、顾客信息卡、身份核实与签发；不再显示完整后台导航和宽表格。原激活查询链接重定向到新页面，返回入口限定为原有枚举。
- 核实说明折叠、顾客身份突出、激活码加大、操作按钮适配窄屏；原顾客管理和钱包内的签发复用同一组件。后台账号入口提供紧凑导航样式。
- 不新增 API 或数据库迁移；继续使用原查询、签发、设密接口。原 tenant_admin / tenant.admin.manage / storeIds 权限、门店与租户隔离、10 分钟到期、5 次错误、限流、哈希存储和事务规则保持。

## 验证与审查

- 新前端测试首先捕获 minlength 仍为 8 的失败，再同步实现前后端规则。
- 后端 CustomerPasswordAuthServiceTest 7 项、CustomerSecurityEmailServiceTest 4 项、SharedCustomerPasswordPostgresTest 29 项通过；随后将邮箱找回正向测试也改为 6 位密码并单独复跑通过。
- PostgreSQL 测试只读取 D:/RPB/target/local-postgres-current.txt 指向的 61644 端口，在随机隔离 schema 执行，结束清除测试 schema。
- 前端 7 文件共 100 项通过，包含激活码前导零显示、设密/找回/改密 6 位表单、重复提交、门店切换及晚到响应清除、无权限不查询、空态、错误重试、中英文和产品线入口回归。
- npm run build、git diff --check 通过。演示数据的中英文窄屏与桌面浏览器验收通过；最终预览包含 App 全局样式和语言切换。原 App 根元素最小宽度为 320px；319px 视口对应 320px 布局，业务区域没有额外溢出。标准 320px 及以上不受影响。
- 测试仅使用演示/隔离数据，未对真实顾客签发、改密或发送邮件。原 12 个无关文件 SHA256 与任务前一致。
- API / TDD / UI / code-review 自查：没有新增越权、租户混用或事务问题。查询复用 customer 模块，签发仍由 customer-auth 拥有；UI 不新增凭据持久化。

## 发布与回退注意

本轮授权上线，需同时更新前后端，旧码需重新生成；无需修改历史数据。若回退到至少 8 字符的旧后端，本版已选择的 6 位密码将无法登录，需要保留本版密码长度规则或安排已验证找回，不能直接回退旧规则。原有迁移、密码哈希、顾客与资产历史均保留。

## 本地证据

- target/customer-activation-six-digit-frontend-final.log
- target/customer-activation-six-digit-backend.log
- target/customer-activation-six-digit-recovery.log
- target/customer-activation-six-digit-build-final.log
- target/customer-activation-six-digit/mobile-zh-list.png
- target/customer-activation-six-digit/mobile-zh-code.png
- target/customer-activation-six-digit/mobile-en-list.png
- target/customer-activation-six-digit/mobile-en-code.png
- target/customer-activation-six-digit/desktop-en-code.png
