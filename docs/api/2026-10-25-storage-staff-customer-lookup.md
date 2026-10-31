# 商户寄存顾客识别查询

用于“帮顾客存酒/取酒/查记录”的顾客选择；顾客入口不使用此 API。

- `GET /api/v1/stores/{storeId}/wine-storage/customers?query=`：手机号或姓名检索；空词返回空列表，最多 120 字符，复用统一顾客目录的租户范围及最多 20 项限制。
- `GET /api/v1/stores/{storeId}/wine-storage/customers/{customerId}`：专用领取码识别后读取该租户的有效顾客资料。
- 响应只包含 `id, displayName, phone`，不返回邮箱、登录信息或完整顾客实体。
- 要求员工身份、当前租户及门店访问、`wine_storage.view`，且具有 `wine_storage.receive`、`wine_storage.deliver` 或 `customer.lookup` 之一；平台管理员不能通过此路径查询。
- 未认证沿现有安全层返回 401；无权限/跨租户/跨门店为 `STORAGE_FORBIDDEN`/403；无效搜索词为 `STORAGE_INVALID`/400；非本租户或失效顾客为 `STORAGE_NOT_FOUND`/404。
- 历史及受控交付查询继续使用原历史读取门禁，不因新业务关闭而阻止查找原顾客；新收存仍由原 AppGate 和收存服务校验。
- 只读、无幂等键、无新增数据表或权限授予。商户命令中的顾客编号来自明确选择或后端验证的领取码，仍由后端再次校验归属。
