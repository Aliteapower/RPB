# Card business report

GET `/api/v1/stores/{storeId}/wallet/cards/reports`

Staff `wallet.cards.view` through existing CardAccess/App Gate and tenant/store scope. Historic access remains available when new transactions are disabled. No new permission or migration. Existing `/entries` and `/summary` remain compatible.

Parameters: required ISO `from`, `to`; optional `section=business|expiry` (default business), `query` (literal customer name/phone, max120), `planId` UUID, `groupBy=plan|customer` (default plan), `kind=all|redeem|stamp|grant|refund|reversal` (default all), `page` zero-based (0..100000), `size` (1..100, default25). Inclusive date range max366 days. Invalid input: existing CARD_INVALID/400. Authentication/denial: existing 401/403. Expiry section accepts kind=all only.

Business dates filter actual createdAt using store timezone; expiry filters businessDay. Both fields returned separately. Report returns timezone, page/size/total/items and aggregates across ALL matching rows, grouped by card plan or customer and quantity unit. Counts, original quantities and reversals are separate; net amounts are original minus reversals. Initial card grants and card refunds count cards, stamps count stamps, benefits count product units; different units never sum together.

Rows: entry identity, customer contact (active directory only), card/plan/product snapshot names, kind, unit, quantity, balances, businessDay, createdAt, source/reason/originalId, global reversed flag and basic reversal eligibility. Derived daily expiry rows for unlimited-total period cards have synthetic IDs, no createdAt or reversal action. Ledger history is retained. Technical identifiers/source appear only in details. Final reversal commands retain authoritative business validation and idempotency.

Existing expiry settlement runs within authorized report transaction before reading, as with the previous reports. Queries use parameter binding, literal keyword escaping, tenant/store constraints on joins, stable ordering and database pagination. Totals and page share one repeatable-read transaction. No customer ownership verification, card issuance, redemption or payment occurs from page selection.
