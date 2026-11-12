# 当前登录账号改密与钱包身份展示

POST `/api/v1/auth/password-change`，仅员工/管理员认证会话，JSON `{currentPassword,newPassword}`；只允许这两个字段。需 `X-RPB-Account: 1`，拒绝Sec-Fetch-Site为cross-site/same-site的请求。账号及租户从RPB_SESSION解析，不接收账号、租户或门店覆盖。

沿用商户登录6位字母数字、不区分大小写的既有密码规则；校验旧密码，新旧密码不得相同。按账号加锁并再次确认当前会话有效，密码哈希更新、撤销该账号其他会话及不含密码的审计在同一事务提交；保留当前会话和原到期时间。不会更改其他账号、门店授权、角色、顾客会话或顾客密码。

连续5次旧密码错误锁定改密15分钟，失败计数事务保留；成功后清零。错误：400 REQUEST_INVALID/PASSWORD_POLICY_VIOLATION，401 UNAUTHENTICATED/INVALID_CREDENTIALS，403 FORBIDDEN（浏览器来源保护），429 RATE_LIMITED。成功 `{success:true}`，响应private/no-store。不返回密码、哈希或会话令牌。

顾客弹窗通过既有GET `/api/v1/public/customer-auth/me?storeId=...`读取当前顾客姓名、手机号和已有邮箱，保留原门店/租户会话过滤，身份未读出时不能提交改密。商户弹窗打开时刷新既有auth/me，核对账号及租户与页面会话一致后显示username和displayName并启用改密；账号变化则要求重新登录。商户与顾客继续分别调用原有退出接口，不互相退出或串用密码接口。

无数据库迁移；复用auth_accounts密码、失败计数及锁定字段、auth_user_sessions和audit_logs。新增能力归现有auth模块，不依赖钱包商业开通或管理员重置他人密码权限。
