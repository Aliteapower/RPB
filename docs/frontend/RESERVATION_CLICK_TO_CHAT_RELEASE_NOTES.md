# Release Notes

## Version / Date

Reservation Click-to-Chat Phase 1 / 2026-06-29

## New

- Added staff WhatsApp click-to-chat for reservation share messages using backend-generated `wa.me` links.
- Added staff WeChat share support by copying the full rendered share message plus H5 link, then opening the local WeChat app where supported.
- Added tenant admin `whatsappBusinessPhoneE164` share-profile field for the store's fixed WhatsApp sender label.
- Added `POST /api/v1/stores/{storeId}/reservations/{reservationId}/share-info/intent` to audit staff sharing intent.
- Added staff share buttons for `WhatsApp发送`, `微信发送`, `系统转发`, and `复制链接`.
- Added API contract `docs/api/RESERVATION_STAFF_SHARE_INFO_API_CONTRACT.md`.

## Changed

- Staff share-info now returns channel-specific fields: `senderLabel`, `canOpenWhatsAppLink`, `whatsappLink`, `canOpenWechatLink`, `wechatLink`, and `wechatShareText`.
- Reservation share messages now combine the rendered template text with the public H5 reservation share link for external chat channels.
- Tenant admin share profile reads and writes `whatsappBusinessPhoneE164` with E.164 validation.

## Fixed

- The existing `whatsappLink` behavior is now active instead of being a dead response field.
- Staff sharing no longer depends only on generic system share/copy fallback when a valid customer phone exists.

## Migration

- Added `V020__store_whatsapp_share_profile.sql`.
- New nullable column: `stores.whatsapp_business_phone_e164`.
- New check constraint: `ck_stores_whatsapp_business_phone_e164`.
- No reservation, customer, queue, seating, cleaning, business event, or state transition schema is changed.

## Permission

- No new App Gate permission is required.
- Share info and share intent both use the existing `reservation.today_view` permission under `reservation_queue`.
- Local runtime allowlist now includes `POST /api/v1/stores/{storeId}/reservations/{reservationId}/share-info/intent`.

## Risk

- `whatsappLink` necessarily contains customer phone digits as part of the `wa.me` URL; frontend must treat it as an action target and must not render or log it as display text.
- The actual WhatsApp sender remains the account logged in on the staff device. The tenant fixed WhatsApp number is informational unless the device is logged into that account.
- WeChat does not provide the same phone-addressed prefilled chat link as WhatsApp. Staff must choose the WeChat conversation manually after the message is copied.
- The intent endpoint writes audit rows for each click and is intentionally not idempotent.
- Vite still reports the existing large chunk warning during frontend build.

## Rollback Notes

- Roll back frontend by removing the four share actions from `ReservationShareCopyPanel.vue` and restoring the prior single share/copy event wiring in reservation workbench containers.
- Roll back APIs by removing `ReservationShareIntentApplicationService`, the intent DTOs, controller method, frontend API client method, and new share-info response fields.
- Roll back tenant admin UI/API by removing `whatsappBusinessPhoneE164` from share-profile DTOs, service validation, repository SQL, frontend types, and `TenantAdminProfilePage.vue`.
- Database rollback requires dropping `stores.whatsapp_business_phone_e164` and `ck_stores_whatsapp_business_phone_e164` only after confirming no tenant depends on the configured sender label.
