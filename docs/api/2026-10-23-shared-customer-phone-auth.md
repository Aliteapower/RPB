# 租户共享顾客与手机号密码登录

## 身份规则
- customer 模块是各产品线唯一顾客目录。租户管理、订位、钱包及后续产品线复用 customer_id，唯一识别范围为 tenant_id + phone_e164（E.164）。
- 后台新增／修改正式顾客须提供手机号，重复返回既有 409 CUSTOMER_PHONE_CONFLICT。历史无号档案及引用保留；临时到店流程兼容，不按姓名／邮箱自动合并或转移资金。
- 钱包员工查询共享目录，不要求顾客先登录；新开钱包须有手机号，历史无号账户保留。资金仍按原门店和经营主体隔离。
- 登录只访问租户已有有效顾客，不自动注册。初始密码为手机号后六位，首次成功登录持久化 BCrypt 哈希；改密后不回退初始密码，修改手机号也不重置密码。
- 旧邮箱／OAuth API 兼容保留，新界面统一手机号密码登录。本轮不接短信。

## API
POST /api/v1/public/customer-auth/password-login：{storeId, phoneE164, password}，门店决定租户。响应沿用既有登录响应及 HttpOnly cookie。未知号码／错误密码统一 401 invalid_credentials。

POST /api/v1/public/customer-auth/password-change：{currentPassword, newPassword}，要求有效顾客会话及旧密码。新密码至少八字符、UTF-8 最多 72 字节，不等于旧密码或手机号后六位。成功撤销该顾客全部旧会话，签发新会话并替换 cookie。不能修改手机号／顾客 ID，不自动重试。

按租户内手机号每十五分钟十次、IP 每十五分钟五十次持久化限流，含失败；同顾客登录／初始化／改密在事务中串行。密码不返回、不记录。登录不创建顾客，只为共享档案创建或复用认证账号。

初始化／修改密码与 customer.password.set 审计同事务，审计仅含租户、顾客、动作，不含密码或密码哈希。

错误：400 request_invalid；401 invalid_credentials、account_disabled、unauthenticated；429 rate_limited；钱包新开户缺号 409 CUSTOMER_PHONE_REQUIRED。格式沿用 {success:false,error}。新 API 复用 public customer-auth 入口，改密验证顾客会话；原租户管理权限、钱包门店权限、App Gate 和资金幂等保持。

登录和 me 响应的 principal 增加可空 phoneE164，email 允许为空。预约使用登录档案的手机号，不能通过预约自动改掉已有手机号；改号统一由顾客管理操作。显示姓名读取共享顾客资料。

GET /api/v1/public/customer-auth/me 支持可选 storeId，仅返回该门店所属租户的有效会话；不匹配为 401。预约页面携带该参数，防止在其他租户入口显示前一会话的资料；旧调用不传参数保持兼容。

## 数据与回退
V058 允许认证账号邮箱为空，增加具租户顾客复合外键的密码凭据表与租户限流表；保留邮箱唯一性及原数据，不重写顾客和钱包引用。初始密码仅在成功校验时初始化哈希，无明文默认密码批量迁移。回退代码时保留迁移和密码数据。

## 验证清单
共享目录未登录可查、跨租户隔离、归档不可见、重复手机号冲突、缺号建档失败、历史保留；已有顾客登录 ID／钱包一致、不自动注册、同号异租户独立、错误／限流／禁用、默认密码不复活、改密撤销旧会话、修改手机号保留已设密码；pointer PostgreSQL 独立 schema 的迁移及并发；钱包与预约共用登录／改密组件、中英文本、构建及资金权限回归。
