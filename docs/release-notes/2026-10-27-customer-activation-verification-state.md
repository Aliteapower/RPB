# 首次核实状态与灰色按钮

2026-10-27，用户已明确授权提交部署，生产结果另记发布验收记录。

## 行为

- 独立到店激活页、租户顾客管理及钱包顾客列表统一读取后台 activationState；已有核实发码记录或已完成设密的顾客显示灰色“已核实”，不再重复首次核实。
- 按用户明确选择，已核实但未设密者保留独立“重新生成激活码”，无需再次勾选核实；已设密者无重发入口，已有已验证邮箱者显示“邮箱已验证”。首次未核实仍须勾选当面核实。
- 旧码过期/被消费后仍保留核实事实。成功发码立即更新当前列表并刷新服务器状态；刷新失败也不重新显示首次核实按钮。页面刷新、切换产品入口均读取真实记录。
- 保持原签发 API、identityConfirmed:true、6 位数字、10 分钟、5 次错误及限流、重发作废旧码、同租户顾客锁和资格校验，不能覆盖已设密码。状态未知时置灰，不猜测为未核实。

## 模块与 API 审查

customer-auth 拥有状态枚举、批量只读查询及持久化适配；tenant-admin 在原有列表/单顾客响应组合 activationState，不向 customer 目录或产品线写入凭据规则。共享前端按钮组件复用状态逻辑，签发组件复用首次核实记录。Controller 不直接访问数据库。

沿用 tenant_admin + tenant.admin.manage + storeIds 校验。查询按 tenant_id 与当前页 customerIds 过滤，不返回密码、哈希、激活码或其他租户状态；没有新端点、新权限、迁移或业务数据修正。新增响应字段向后兼容，详情/列表有真实状态；创建/更新响应在列表刷新前为 unknown。首次核实历史来自既有记录，无需回填。

## 验证与审查

- 47 项后端测试通过：SharedCustomerPasswordPostgresTest 31、CustomerPrivacyApiTest 5、CustomerPasswordAuthServiceTest 7、CustomerSecurityEmailServiceTest 4。
- 新增测试覆盖 required→verified_pending→activated、旧码过期仍已核实、详情状态、邮箱已验证、停用账号、未知顾客、跨租户和跨门店拒绝；原重发、设密/登录/找回、事务与安全回归通过。
- 104 项前端回归通过；覆盖各状态按钮、待设密独立重发、无重复核实、激活码过期后仍可重发、首次签发后列表刷新失败仍立即置灰；原重复提交和晚到响应隔离保留。
- 首次后端测试因指针端口 61644 无监听而失败；按 LOCAL_RUNTIME_QUICK_RESTART_GUIDE 恢复 D:/RPB/target/local-postgres-current.txt 指向的原 dataDir 后，在随机隔离 schema 运行通过，没有切换到系统数据库。
- vue-tsc/Vite、git diff --check 通过。390px 中文与320px英文真实组件夹具确认灰色状态、重发入口和无横向溢出；重发面板没有重复确认框。只用演示数据，没有真实签发或改密。
- 只读核对用户指定的真实顾客，已存在核实发码及自设密码记录；未改动该顾客。12 个原有无关文件 SHA256 保持。
- API/TDD/UI/code-review 结论：通过，无未处理阻断项。查询仍只读、租户边界清晰，服务器始终保留最终签发资格校验。

## 发布与回退

已获得本轮生产部署授权。上线需同时发布后端状态字段与前端；新前端连接旧后端会因 unknown 状态禁用发码。没有数据库迁移；回退前端可恢复原界面，新后端附加字段不影响旧客户端。凭据、核实记录和资产历史保持。

证据：target/customer-activation-status-20261027/backend-final.log、frontend-final.log、build-final.log、mobile-zh.png、reissue-zh.png。
