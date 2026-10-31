# 门店产品线使用开关 API

## 契约

租户管理员按门店管理已开通的 wallet、daily_ledger。复用 tenant_admin 角色、tenant.admin.manage 权限及账号门店范围；不能关闭或开启其他租户／无权门店。管理入口不受具体产品运行开关控制。

- GET `/api/v1/stores/{storeId}/tenant-admin/product-lines`：`{success:true,products:[{appKey,enabled,canEnable,unavailableReason}]}`。
- PUT `/api/v1/stores/{storeId}/tenant-admin/product-lines/{appKey}`：请求 `{enabled:true|false}`；返回同结构，products含目标产品一项。必须携带 `X-RPB-Product-Settings: 1`，拒绝 Sec-Fetch-Site 为 cross-site/same-site 的写入；不开放CORS。
- enabled是门店使用开关；canEnable表示当前平台／租户／门店商业授权是否允许开启。已开关开启但订阅失效时可能 enabled=true/canEnable=false，显示失效原因，实际访问仍被AppGate阻止。
- unavailableReason为AppGate拒绝原因：APP_DISABLED、TENANT_APP_NOT_ENABLED、TENANT_APP_EXPIRED、STORE_APP_NOT_ENABLED、STORE_APP_EXPIRED；可用时null。关闭项仍列出，以便重新开启；尚未开通项只能查看，不能凭开关购买或授予权限。
- 响应使用 Cache-Control: private, no-store；不返回数据库实体、产品私有配置或其他门店资料。

## 错误与重试

沿用 `{success:false,error:{code,messageKey,details}}`。匿名401 UNAUTHENTICATED；非管理员403 FORBIDDEN；无门店范围403 STORE_SCOPE_MISMATCH；未知产品／缺失或null的enabled为400；非活动或不匹配的实际租户／门店404 TENANT_PROFILE_NOT_FOUND；无有效开通资格的开启请求409 PRODUCT_LINE_UNAVAILABLE。数据库故障不返回成功，事务整体回滚。

PUT是显式目标状态，相同门店写操作按门店行串行化。重复目标不重复改变数据或追加审计；重新开启每次仍校验商业资格。关闭可在订阅已到期时执行；业务数据、订阅状态和计费不变。

## 持久化与兼容

无需迁移，使用store_app_settings既有范围唯一键、is_enabled、disabled_at及AppGate审计。状态变更与审计同事务。重新开启清除disabled_at，关闭写入disabled_at；订阅同步对wallet/daily_ledger保留is_enabled=false且disabled_at非空的明确关闭状态，首次购买仍自动创建开启状态。其他产品同步语义不变。

菜单、员工API和顾客钱包业务API继续使用现有AppGate运行判定，无法通过直接链接绕过关闭状态。切店后前端丢弃旧门店未完成响应；保存成功后刷新当前管理员菜单可见产品。
