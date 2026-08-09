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

## Deployment

- 功能提交：`6cc06fbd fix: add paynow manual confirm`
- 验证：
  - `mvn "-Dtest=PaymentIntentServiceTest,PaymentIntentControllerTest,PayNowPaymentUiAcceptanceValidationTest" test`：25 tests，0 failures，0 errors
  - `npm run build`：通过
  - `mvn -q -DskipTests package`：通过
- 生产备份：`/opt/rpb/backups/20260809-0904-6cc06fbd-paynow-manual-confirm`
- 线上 smoke：
  - `/login`：200
  - `/stores/20000000-0000-0000-0000-000000000001/payments`：200
  - `/stores/20000000-0000-0000-0000-000000000001/payments/proof-review`：200
  - `PaymentQuickPayPage-C2vFzTOL.js`、`paymentPresentBridge-DYcbGADv.js`、`api-DakOu-cN.js`：200
  - unauthenticated manual-confirm endpoint：403 `PERMISSION_DENIED`，确认路由已上线且受 App Gate 保护

## Deployment Update: Recent Ref Display

- 功能提交：`1b14d087 fix: show quickpay recent ref`
- 变更：Quick Payment 的 Recent Display Numbers 卡片显示该单 Ref，便于员工核对回单。
- 验证：
  - `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`：3 tests，0 failures，0 errors
  - `npm run build`：通过
- 前端生产备份：`/opt/rpb/backups/20260809-0918-1b14d087-quickpay-recent-ref-frontend`
- 线上 smoke：
  - `/login`：200
  - `/stores/20000000-0000-0000-0000-000000000001/payments`：200
  - `PaymentQuickPayPage-BN4bqWSe.js`、`PaymentQuickPayPage-cC4WPumi.css`：200

## Deployment Update: Confirmed Pending Display

- 功能提交：`f5b45e4a fix: stop confirmed paynow pending display`
- 变更：
  - Quick Payment 的 Recent Display Numbers 在手动确认后不再显示倒计时，只保留已更新的状态。
  - 回单校验弹窗重新获得焦点时刷新待验证收款列表，避免其他窗口手动确认后仍显示为 pending。
- 验证：
  - `mvn "-Dtest=PayNowPaymentUiAcceptanceValidationTest" test`：3 tests，0 failures，0 errors
  - `npm run build`：通过
- 前端生产备份：`/opt/rpb/backups/20260809-0930-f5b45e4a-paynow-confirmed-pending-display-frontend`
- 线上 smoke：
  - `/login`：200
  - `/stores/20000000-0000-0000-0000-000000000001/payments`：200
  - `/stores/20000000-0000-0000-0000-000000000001/payments/proof-review`：200
  - `PaymentPresentPage-Cg6VkvBA.js`、`PaymentProofReviewPage-Ri-leWIw.js`、`PaymentQuickPayPage-B5qBbMyK.js`：200
