# 租户常用入口与名称二维码

## 范围与交互

租户后台增加独立 `/stores/:storeId/admin/entry-links` 页面与一级导航，沿用管理员身份和授权门店切换。展示顾客钱包、在线预约、员工入口、钱包收银、PayNow 收银、每日账本及租户管理入口。公开入口直接打开目标；员工/管理员入口指定登录类别并保留本门店目标路径，不传账号密码、付款码或会话。

每项提供可复制 URL、打开入口与带租户名称的 PNG 二维码。二维码中央使用平台 tenants.display_name，外侧完整显示租户、门店及用途。名称自动换行缩放，超长名称中央保留省略显示，外侧保留全名；使用高容错、四模块静区，叠字后的 QR 及最终整张导出图都必须解码回相同 URL 才可下载。完整名称和入口用途保留在下载图的外侧标题中。长名称不能无限扩大遮挡区域。

## 只读接口

GET `/api/v1/stores/{storeId}/tenant-admin/entry-links`

权限：已登录 tenant_admin + tenant.admin.manage + 当前 storeId 授权；租户从服务端会话取得，忽略客户端租户声明。复用 TenantAdminScopeResolver。该接口属于管理资料查询，未开通任何产品也可查看其不可用原因，不赋予产品使用权。

响应：`{success:true,identity:{tenantId,tenantCode,tenantName,tenantStatus,storeId,storeCode,storeName,storeStatus},publicBookingEnabled,products:[{appKey,available,denialReason}]}`。产品包含 reservation_queue、wallet、payment、daily_ledger，复用 AppGate.evaluateCommercial 核验平台、租户、门店开通及单店到期。在线预约额外读取已有 PublicBookingAdminService 设置。租户/门店停用时不可分发新入口。

401 UNAUTHENTICATED；403 FORBIDDEN / STORE_SCOPE_MISMATCH；404 TENANT_PROFILE_NOT_FOUND；500 PERSISTENCE_ERROR。错误格式沿用租户后台 `{success:false,error:{code,messageKey,details}}`。返回 Cache-Control: private, no-store。无写操作、幂等键或新迁移；重复查询不改变开通、计费、资料或资金。

## URL 与兼容

- 顾客钱包 `/wallet/{storeId}`，预约 `/book/{storeId}`；多门店绝不生成猜测的 `/book`。
- 员工登录 `/login?entry=staff&redirect=<内部门店路由>`；租户后台登录 entry=tenant_admin。租户子域固定本租户；本地/旧主域补充合法 tenantCode 仅用于登录填充，服务端身份和权限仍须验证。
- 新登录类别 query 仅选择已允许的选项；平台域不因 query 切换租户入口。旧 URL 缺省语义不变。
- 租户域名按现有 hostContext 构造方式和受信基域生成；不接收任意域名、脚本协议或外部重定向。页面只生成固定路径目录，不接受数据库任意链接。
- 产品不可用时显示未开通/停用/到期原因，不启用打开、复制或二维码分发；原有资料、订阅和原单善后入口行为不变。

## 测试清单

- 管理员、自有授权门店、匿名、普通员工、平台管理员、未授权门店、跨租户伪造及删除资料。
- 真实 PostgreSQL 验证租户名称来自统一 tenants 而非门店名称，且查询不写表；同租户不同门店与不同租户隔离。
- AppGate 未开通/到期/停用，以及在线预约设置关闭；不因管理员可见而分发不可用产品二维码。
- 租户/主域/平台域/本地/端口、非法租户代码、不同门店、员工登录选项和旧登录缺省兼容。
- 名称排版、真实二维码解码、下载 PNG 完整名称、长名、渲染失败禁用、切店竞态、剪贴板失败、窄屏及中英文。
- 无迁移/依赖升级/生产资金操作；本轮不自动提交部署。
