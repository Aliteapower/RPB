# Wine storage reports API

GET /api/v1/stores/{storeId}/wine-storage/reports

Authenticated staff only: tenant + assigned store + wine_storage.view; active or authorized history App Gate access required. Does not permit new business. No public/customer endpoint.

Filters: kind=deposit|pickup (default deposit), query literal case-insensitive substring max120 across customer name/phone, wines, location and label; status=all or a status for that kind. Deposit: pending/stored/cancelled/rejected; pickup: awaiting_authorization/authorized/completed/cancelled/expired. from/to optional YYYY-MM-DD inclusive local dates interpreted using store timezone, invalid/reversed dates rejected. page=0..100000,size=1..100 (default20).

Returns Page {items:Row[],summary:{recordCount,completedCount,bottleCount},page,size,hasMore}. Row {id,kind,customerId,customerName,customerPhone,status,occurredAt,quantity,winesZh,winesEn,locations}. One application/intent per row, deterministic occurredAt DESC,id DESC. Summary computed on the entire filtered result: bottleCount counts actual stored/completed quantities only; pending/cancelled/expired do not count as completed stock movements. Report period uses receipt time for received deposits (created time fallback), handover time for completed pickups and creation time otherwise. This is historical movement, not current stock. Returned summaries and rows share a REPEATABLE_READ snapshot.

GET /reports/{kind}/{id}: same access; returns Detail {application|null,intent|null,items,events}; only the specified bill and linked holdings/events; original evidence is retained. No stock mutations or QR secrets. Missing/cross-tenant/store id =>404. Codes STORAGE_INVALID 400, STORAGE_FORBIDDEN403, STORAGE_UNAUTHENTICATED401, STORAGE_NOT_FOUND404 per existing error advice. Read queries are retry-safe. Existing APIs remain compatible.

GET /home?includeRecords=false: same entry access, existing Home structure with empty applications/items/intents/events, used for report shell; default true retains legacy behavior. This avoids downloading all customer history merely to open a paginated report. UI report has no customer creation, receipt/withdrawal commands, correction/upload or customer confirmation step.
