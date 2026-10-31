# 每日营业记录品牌配置收敛

## 所有权

门店名称使用租户资料的当前门店名称；Logo使用租户资料的共享租户Logo。账本不再提供名称或Logo写入入口。旧branding JSON保留用于迁移和兼容，不修改旧营业日配置或提交快照。

## API

- GET/PUT `/api/v1/stores/{storeId}/daily-ledger/settings` 保持DTO结构，返回的branding为展示投影；logo_data可为同源、受保护的当前Logo URL。PUT的branding保留兼容字段，但不再写入；config及version/CAS仍生效，权限仍为settings.manage。
- GET `/api/v1/stores/{storeId}/daily-ledger/branding/logo/{assetId}`，沿用daily_ledger.view/AppGate及认证tenant/store范围，只允许读取当前租户Logo；旧或其他assetId返回404。成功返回图片资源和private/no-cache，禁止使用租户后台管理权限代替普通员工查看权限。
- 租户资料现有上传/清空接口及权限不变。替换和清空后账本下一次读取立即采用统一结果，不复活旧图片。

## 兼容迁移

显式ledger-branding-import模式按tenant-code迁移，正常服务启动不迁移。仅当租户没有共享Logo且旧账本只有一个非空Logo时采用旧图；有不同旧图则拒绝自动选择并回滚，防止集团门店品牌被错误覆盖。有共享Logo时保留共享图。记录platform_branding_managed标记，支持重复执行且清空后不恢复旧图。无数据库结构迁移。

## 验证清单

统一来源优先；旧图迁移后保留；共享Logo清空不回退；其他租户资源404；普通员工可按AppGate查看当前图但不能编辑租户资料；旧客户端PUT不能改Logo；迁移幂等和歧义回滚；金额、冻结配置、提交快照与既有业务不变；界面只有一个上传入口。
