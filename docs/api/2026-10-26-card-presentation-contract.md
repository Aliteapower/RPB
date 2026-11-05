# 顾客卡码

- `POST /api/v1/public/stores/{storeId}/wallet/cards/holdings/{id}/presentation`
  请求 `{quantity, businessDay, idempotencyKey}`，返回 `{requestId, code, expiresAt}`。
- 顾客登录、Wallet App Gate、门店卡功能开启、持有人及 tenant/store 校验；数量 1..1000000，集次卡仅 1。业务日期使用卡片冻结时区。有效卡及今日额度在出码和执行时均检查。
- 创建绑定持有人、卡片、操作和数量的 120 秒单次 CA 授权，不扣额度。同一幂等键恢复同一操作码；过期恢复不延长期限。
- 商户沿用 `POST /authorized-redeem`；先定位操作，再按 stamp/redeem 校验对应权限。集次增加一次，其他类型扣指定数量。事务锁、原有幂等与业务日期保护不变；重复执行不重复记账。
- 顾客沿用 `GET /requests/{id}` 查询完成状态。码只供本店商户扫描，关闭展示不撤销已发出的短时授权。
- 错误沿用 CARD_INVALID/400、CARD_NOT_FOUND/404、CARD_FORBIDDEN/403、CARD_CODE_EXPIRED/409、CARD_DAY_CHANGED/409、CARD_QUOTA_INSUFFICIENT/409 及既有 App Gate 错误。
- 无迁移，无新增权限，兼容原商户出码和顾客确认流程。

测试清单：各卡类型；仅出码不扣次；集次权限；跨持有人/门店/租户；过期、跨日、停用、额度不足；同键重放和重复扫码；前端展示、过期刷新及成功关闭。
