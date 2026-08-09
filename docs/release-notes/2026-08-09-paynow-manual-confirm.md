# PayNow Manual Confirm

## Version / Date

2026-08-09

## New

- Quick Payment 的 Recent Display Numbers pending 卡片右侧新增手工“确认”按钮。
- 新增 `POST /api/v1/stores/{storeId}/payments/intents/sessions/{sessionNo}/manual-confirm`，用于员工确认已收到 PayNow 转账并将 quick pay intent/session 关单为 `paid`。
- 手工确认写入 `payment_events` 的 `source_confirmed` 事件，保留 actor、idempotency key 和 terminal code。

## Changed

- 手工确认成功后，当前终端本地展示缓存会将对应 recent item 标记为 `paid`，并从顾客展示屏 active payload 中移除。

## Fixed

- 当扫码/拍照回单无法校验时，员工可在 pending/awaiting verification 卡片上完成手工关单。

## Migration

- 无数据库迁移。复用既有 `payment_events.event_type = source_confirmed`。

## Permission

- 无新增权限。手工确认 endpoint 复用 `payment.intent.create` App Gate 权限。

## Risk

- 手工确认依赖员工线下核验实际到账。后端仅允许 quick pay 且状态为 `pending` 或 `awaiting_verification` 的 session 变为 `paid`，已取消或失败的记录不会被确认。
- Tenant/store scope 由当前登录 actor 和 path storeId 双重限制。

## Rollback Notes

- 回滚本次应用版本即可移除前端按钮和后端 endpoint。
- 已手工确认产生的 `paid` 状态和 `source_confirmed` 事件属于真实收款操作记录，回滚代码不会自动反向修改数据。
