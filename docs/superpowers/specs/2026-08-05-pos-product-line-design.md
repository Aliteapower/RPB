# POS Product Line Design

## Status

Design specification only. This document does not create executable schema, Java code, Vue code, runtime configuration, dependency changes, production data changes, seed data, or migrations.

The approved direction is:

```text
Create a native RPB POS product line using SimsPOS as a business reference, not as code to migrate.
```

## Source Context

### SimsPOS Evidence

`D:\SIMS` contains a packaged legacy POS stack, not a normal source repository:

- `sims_web.war`: Spring 4.0.3, Hibernate 4.3.5, MySQL Connector 5.1.30, JSON/JSONP web application.
- `SimsPOS.zip`: Sencha Touch mobile POS application.
- `test_20141027.full.sql`: MySQL 5.6-era schema and seed/sample data.
- Runtime installers: JDK 6, Tomcat 7, MySQL 5.6.

SimsPOS covers these useful POS concepts:

| SimsPOS Area | Evidence | Product Meaning |
|---|---|---|
| Product catalog | `GD_GOODS`, `GD_GOODS_PRICE`, `GD_CLZ`, `GD_TAX_FEE` | Sellable items, category tree, unit price, tax and service fee rules. |
| Front POS order | `PS_ORDER`, `PS_ORDER_ITEM` | Open order, item lines, discount, tax, fee, paid amount, close state. |
| Payment and cash drawer | `PS_MONEY_BOX` | Cash/card payment entries, return change, open cash box style operations. |
| Dining table | `TB_TABLE_INFO` | Table code, area, seats, minimum charge. |
| Cash point and printing | `TB_CASH_POINT`, `PT_PRINTER`, `PT_PRINTER_TPL` | POS terminal/cash point and receipt template concepts. |
| Inventory | `ST_STORE`, `ST_STORE_ITEM`, `ST_IN_ORDER`, `ST_OUT_ORDER`, `ST_CHECK_ORDER` | Store stock, inbound, outbound, stock check. |
| Organization and users | `AH_USER`, `AH_ROLE`, `OG_CUSTOMER`, `OG_EMPLOYEE` | Account, role, customer, employee concepts. |

The mobile app calls endpoints such as:

```text
pos/Order/create.jsonp
pos/Order/createItems.jsonp
pos/Order/updPriceAmount.jsonp
pos/Order/closeOrder.jsonp
pos/Order/printBill.jsonp
pos/Order/openBox.jsonp
pos/MoneyBox/receiveCash.jsonp
pos/MoneyBox/receiveCard.jsonp
pos/MoneyBox/returnChange.jsonp
goods/Goods/query4Customer.json
goods/Goods/queryByTreeNode.json
goods/Goods/queryByNameCode.json
```

The valuable part is the POS workflow and domain vocabulary. The legacy runtime itself is not suitable for direct reuse in RPB because it is old, JSONP-based, MySQL-specific, and not aligned with RPB tenant/store isolation.

### RPB Evidence

RPB already has:

- Product line catalog through `platform_apps`.
- Tenant entitlement through `tenant_app_entitlements`.
- Store-level entry and enablement through `store_app_settings`.
- Product-line billing and store billing state through `platformbilling`.
- Tenant, operating entity, store, staff, customer, table, reservation, queue, walk-in, seating, and cleaning modules.
- App Gate runtime checks over platform app status, tenant entitlement, store setting, store access, and endpoint permission.

Current gap:

```text
AppGateService.visibleApps() currently exposes entry permissions only for reservation_queue.
```

The POS product line must address that entry-permission hardcoding before the POS app can appear correctly in `/me/apps`.

## Goals

1. Add a new RPB-native POS product line with `app_key = pos`.
2. Reuse RPB tenant, store, staff, customer, and table boundaries instead of copying SimsPOS platform/user/store tables.
3. Implement a Phase 1 POS sales loop: catalog, open order, add items, adjust item quantity/price with permission, take payment, close order, and print or render receipt.
4. Keep every POS operational table scoped by `tenant_id` and `store_id`.
5. Use App Gate for runtime product-line entitlement and POS endpoint permissions.
6. Use existing platformbilling product-line and store-billing models for commercial activation.
7. Keep POS order payment separate from platform product-line billing.
8. Reserve explicit extension points for later inventory, KDS, member wallet, offline mode, and hardware integration without implementing them in Phase 1.

## Non-Goals

- No direct migration of SimsPOS Java, Sencha Touch, MySQL, or JSONP code.
- No compatibility runtime for `*.jsonp` endpoints.
- No MySQL support in RPB POS.
- No inventory inbound, outbound, stock check, recipe costing, or stock deduction in Phase 1.
- No KDS, kitchen ticket routing, or serving management in Phase 1.
- No member stored value, prepaid card, or loyalty wallet in Phase 1.
- No payment gateway settlement, card terminal integration, Stripe, PayNow QR automation, or bank reconciliation in Phase 1.
- No offline-first POS in Phase 1.
- No InvoiceNow, GST filing, accounting export, or tax document automation in Phase 1.
- No changes to Reservation, Queue, Walk-in, Seating, or Cleaning behavior except read-only table/customer reuse where documented.

## Product Line Boundary

The POS product line is one App Gate app:

| Field | Value |
|---|---|
| `app_key` | `pos` |
| Platform display name | `POS 收银系统` |
| Default entry route | `/stores/:storeId/pos` |
| Product-line billing | Existing platformbilling subscription and store item model |
| Runtime authorization | App Gate plus POS endpoint permission |

POS is independent from `reservation_queue`. A store may subscribe to both, only one, or neither.

The POS product line owns restaurant sales operations. It must not own platform subscription billing. Platform subscription billing continues to live in `platformbilling`.

## Recommended Approach

Use this Phase 1 approach:

```text
RPB-native POS core, SimsPOS-informed model, no legacy runtime bridge.
```

Alternatives considered:

| Option | Description | Trade-Off | Decision |
|---|---|---|---|
| Legacy embed | Run SimsPOS war/zip next to RPB and link to it. | Fast demo but unsafe stack, no tenant isolation, no unified billing or permissions. | Reject. |
| Data adapter first | Build importers from SimsPOS MySQL into RPB POS before POS UI. | Useful for migration projects but delays a sellable POS product. | Defer. |
| Native POS core | Rebuild POS Phase 1 in RPB and map SimsPOS fields conceptually. | More design work now, but clean SaaS product line. | Recommend. |

## Module And OOD Design

Create a focused POS module:

```text
com.rpb.reservation.pos
  api
  application
  domain
  persistence
```

Frontend structure:

```text
src/api/posCatalogApi.ts
src/api/posOrderApi.ts
src/api/posPaymentApi.ts
src/api/posReceiptApi.ts
src/types/posCatalog.ts
src/types/posOrder.ts
src/types/posPayment.ts
src/pages/PosWorkbenchPage.vue
src/pages/TenantAdminPosCatalogPage.vue
src/components/pos-workbench/*
src/components/tenant-admin-pos/*
```

### Domain Objects

| Object | Responsibility | Must Not Do |
|---|---|---|
| `PosProductCategory` | Store-scoped product category tree and display order. | Own platform product-line category. |
| `PosProduct` | Store-scoped sellable item identity, availability, tax category, and display fields. | Store inventory quantity. |
| `PosProductPrice` | Active unit price by product, unit, and sales channel. | Calculate platform billing price. |
| `PosTaxRule` | Store-scoped sales tax and service fee calculation rule. | Replace jurisdictional tax filing. |
| `PosTerminal` | Store-scoped POS terminal or cash point. | Control physical hardware directly in Phase 1. |
| `PosOrder` | Aggregate root for one sales order lifecycle. | Mutate platform subscriptions or inventory. |
| `PosOrderLine` | Item line under an order, including quantity, price snapshot, discount, tax, and status. | Query live product price after line creation except through explicit price update command. |
| `PosPayment` | Payment entry under an order. | Store raw card number, bank credential, or payment gateway secret. |
| `PosReceiptSnapshot` | Immutable printable receipt payload snapshot. | Act as the order source of truth. |
| `PosNumberSequence` | Store-scoped business-date order number sequence. | Generate global IDs or platform billing numbers. |

### Application Services

| Service | Responsibility |
|---|---|
| `PosCatalogService` | Query and mutate POS categories, products, prices, and tax rules for a store. |
| `PosOrderService` | Open orders, add/update/void lines, apply order-level discount, close or void orders. |
| `PosPaymentService` | Record cash/card/paynow/manual payments and return change calculations. |
| `PosReceiptService` | Build receipt snapshots and render printer-ready payloads or print-view responses. |
| `PosNumberSequenceService` | Allocate store/business-date order numbers transactionally. |
| `PosPermissionPolicy` | Centralize POS command permission requirements for application services and UI action state. |

### Dependency Rules

1. `pos` may read `stores`, `store_areas`, `dining_tables`, `customers`, and staff actor context through explicit ports or repository projections.
2. `pos` may not update Reservation, Queue, Walk-in, Seating, Cleaning, platform billing, App Gate, tenant lifecycle, or customer auth tables.
3. `platformbilling` must not depend on `pos`. It only sees `app_key = pos`.
4. `appgate` must not depend on POS domain services. It may know POS entry permissions through an App Gate permission registry or explicit POS permission set.
5. Controllers call POS application services, not repositories.
6. Domain objects use stable value objects and status transitions. Persistence entities do not leak into API responses.
7. Payment commands are idempotent because cashiers can retry after network or tablet issues.

## App Gate Design

### Product-Line Seed

Implementation should seed:

```sql
insert into platform_apps (
    app_key,
    app_name,
    status,
    default_entry_route,
    description,
    sort_order,
    config_json
) values (
    'pos',
    'POS 收银系统',
    'active',
    '/stores/:storeId/pos',
    'Store POS sales, cashier payment, receipt, and basic sales reporting.',
    20,
    '{"entryPermissions":["pos.order.view","pos.order.create","pos.payment.take"]}'::jsonb
);
```

Phase 1 may also seed zero or default platform product-line prices for `pos` using existing `platform_product_line_prices`.

### Entry Permission Gap

Current `AppGateService.visibleApps()` returns entry permissions only when `appKey = reservation_queue`. POS requires one of these implementation decisions:

| Option | Description | Recommendation |
|---|---|---|
| Explicit POS set | Add POS constants and a POS entry-permission set beside `RESERVATION_QUEUE_ENTRY_PERMISSIONS`. | Acceptable for Phase 1. |
| Config-driven registry | Read `platform_apps.config_json.entryPermissions` and filter actor permissions dynamically. | Preferred long-term. |

Recommended Phase 1:

```text
Use explicit POS permission constants now, but keep platform_apps.config_json.entryPermissions aligned so Phase 2 can move to a config-driven registry.
```

### Runtime Enforcement

Every POS operational API must use:

```text
@RequireAppGate(appKey = "pos", permission = "<pos permission>")
```

App Gate remains responsible for:

1. `platform_apps.status = active`.
2. Tenant has enabled/trial/non-expired `tenant_app_entitlements` for `pos`.
3. Store has enabled `store_app_settings` for `pos`.
4. Actor can access the store.
5. Actor has the endpoint permission.

POS services still validate order ownership and store scope after App Gate passes.

## Permissions

### Platform Permissions

Existing permissions remain:

| Permission | Purpose |
|---|---|
| `platform.product_line.manage` | Create and update the `pos` product-line catalog entry. |
| `platform.billing.manage` | Open, renew, suspend, or cancel store billing for `pos`. |

### Tenant Admin Permissions

| Permission | Purpose |
|---|---|
| `pos.catalog.manage` | Manage categories, products, prices, tax rules, and availability. |
| `pos.terminal.manage` | Manage POS terminals, receipt preferences, and print-view settings. |
| `pos.report.view` | View POS sales summaries and order history. |

### Staff Permissions

| Permission | Purpose |
|---|---|
| `pos.order.view` | View current POS orders and order history for accessible stores. |
| `pos.order.create` | Open a new POS order. |
| `pos.order.update` | Add items, update quantity, add notes, and attach customer/table. |
| `pos.order.discount` | Apply line or order discounts. |
| `pos.order.void` | Void order lines or entire open orders. |
| `pos.price.override` | Override unit price on a line. |
| `pos.payment.take` | Record payment entries. |
| `pos.payment.void` | Void a payment before order close. |
| `pos.order.close` | Close a fully paid order. |
| `pos.receipt.print` | Generate receipt snapshots and print-view payloads. |
| `pos.cash_drawer.open` | Record a cash drawer open event. |

Entry visibility should require at least one of:

```text
pos.order.view
pos.order.create
pos.payment.take
```

## Phase 1 Data Model

All POS operational tables are PostgreSQL tables in RPB. All tenant-scoped tables include `tenant_id`. All store-operational tables include `store_id` and use scoped foreign keys where the referenced table supports them.

### Product Catalog

#### `pos_product_categories`

Purpose: store-scoped category tree for POS browsing.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `parent_id` | uuid | Nullable, same tenant/store. |
| `code` | text | Required store-scoped code. |
| `name` | text | Required display name. |
| `status` | text | `active`, `hidden`, `archived`. |
| `sort_order` | integer | Required, default 0. |
| `created_at` | timestamptz | Required. |
| `updated_at` | timestamptz | Required. |
| `version` | integer | Optimistic version. |

Required constraints and indexes:

- Unique `(tenant_id, store_id, code)`.
- FK `(store_id, tenant_id)` to `stores(id, tenant_id)`.
- Add a unique key `(id, tenant_id, store_id)` and use FK `(parent_id, tenant_id, store_id)` to enforce same-store parent category scope.
- Check `status in ('active','hidden','archived')`.
- Index `(tenant_id, store_id, status, sort_order, code)`.

#### `pos_products`

Purpose: store-scoped sellable item.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `category_id` | uuid | Nullable category. |
| `code` | text | Required store-scoped item code. |
| `name` | text | Required item name. |
| `short_name` | text | Optional compact POS display. |
| `description` | text | Optional. |
| `product_type` | text | `standard`, `service`, `set`, `open_price`. |
| `tax_rule_id` | uuid | Nullable. |
| `status` | text | `active`, `hidden`, `sold_out`, `archived`. |
| `image_url` | text | Nullable. |
| `created_at` | timestamptz | Required. |
| `updated_at` | timestamptz | Required. |
| `version` | integer | Optimistic version. |

Required constraints and indexes:

- Unique `(tenant_id, store_id, code)`.
- FK `(store_id, tenant_id)` to `stores(id, tenant_id)`.
- Category and tax rule must belong to the same tenant/store.
- Check `product_type in ('standard','service','set','open_price')`.
- Check `status in ('active','hidden','sold_out','archived')`.
- Index `(tenant_id, store_id, status, category_id, code)`.
- Trigram or lower-name search can be deferred unless existing RPB search policy already enables it.

#### `pos_product_prices`

Purpose: price rows for product units and sales channels.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `product_id` | uuid | Required. |
| `unit_code` | text | Required, such as `each`, `plate`, `bowl`. |
| `channel` | text | `dine_in`, `takeaway`, `delivery`, `all`. |
| `price` | numeric(12,2) | Required, non-negative. |
| `currency` | text | Required uppercase 3-letter currency. |
| `status` | text | `active`, `disabled`. |
| `created_at` | timestamptz | Required. |
| `updated_at` | timestamptz | Required. |
| `version` | integer | Optimistic version. |

Required constraints and indexes:

- Unique `(tenant_id, store_id, product_id, unit_code, channel)`.
- Product FK must be same tenant/store.
- Check `price >= 0`.
- Check `currency = upper(currency) and length(currency) = 3`.
- Check `channel in ('dine_in','takeaway','delivery','all')`.
- Check `status in ('active','disabled')`.

#### `pos_tax_rules`

Purpose: GST/service-charge style calculation rules for POS order totals.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `code` | text | Required store-scoped code. |
| `name` | text | Required. |
| `tax_rate` | numeric(7,4) | Required, non-negative. |
| `service_charge_rate` | numeric(7,4) | Required, default 0. |
| `price_includes_tax` | boolean | Required. |
| `status` | text | `active`, `disabled`. |
| `created_at` | timestamptz | Required. |
| `updated_at` | timestamptz | Required. |
| `version` | integer | Optimistic version. |

Required constraints:

- Unique `(tenant_id, store_id, code)`.
- Check rates are `>= 0` and `<= 1`.
- Check `status in ('active','disabled')`.

### POS Terminal And Receipt

#### `pos_terminals`

Purpose: logical POS terminal or cash point.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `code` | text | Required store-scoped code. |
| `name` | text | Required. |
| `terminal_type` | text | `tablet`, `counter`, `browser`, `kiosk`. |
| `receipt_mode` | text | `browser_print`, `network_print_reserved`, `none`. |
| `config_json` | jsonb | Required default `{}`. |
| `status` | text | `active`, `disabled`. |
| `created_at` | timestamptz | Required. |
| `updated_at` | timestamptz | Required. |
| `version` | integer | Optimistic version. |

Phase 1 stores printer configuration only as reserved metadata. It does not connect directly to ESC/POS printers or cash drawers.

#### `pos_receipt_snapshots`

Purpose: immutable printable receipt payload for a closed or printed order.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `order_id` | uuid | Required. |
| `receipt_no` | text | Required store/business-date scoped receipt number. |
| `snapshot_json` | jsonb | Required printable payload. |
| `created_by` | uuid | Actor account when available. |
| `created_at` | timestamptz | Required. |

Required constraints:

- Unique `(tenant_id, store_id, receipt_no)`.
- Order FK must be same tenant/store.

### POS Order

#### `pos_orders`

Purpose: aggregate root for one sales transaction.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `business_date` | date | Store business date. |
| `order_no` | text | Store/business-date order number. |
| `terminal_id` | uuid | Nullable. |
| `table_id` | uuid | Nullable existing dining table. |
| `customer_id` | uuid | Nullable existing customer. |
| `sales_channel` | text | `dine_in`, `takeaway`, `delivery`. |
| `status` | text | `open`, `held`, `closed`, `voided`. |
| `currency` | text | Required uppercase 3-letter currency. |
| `subtotal_amount` | numeric(12,2) | Required default 0. |
| `discount_amount` | numeric(12,2) | Required default 0. |
| `service_charge_amount` | numeric(12,2) | Required default 0. |
| `tax_amount` | numeric(12,2) | Required default 0. |
| `rounding_amount` | numeric(12,2) | Required default 0. |
| `total_amount` | numeric(12,2) | Required default 0. |
| `paid_amount` | numeric(12,2) | Required default 0. |
| `change_amount` | numeric(12,2) | Required default 0. |
| `note` | text | Nullable. |
| `opened_by` | uuid | Actor account when available. |
| `closed_by` | uuid | Nullable. |
| `voided_by` | uuid | Nullable. |
| `opened_at` | timestamptz | Required. |
| `closed_at` | timestamptz | Nullable. |
| `voided_at` | timestamptz | Nullable. |
| `created_at` | timestamptz | Required. |
| `updated_at` | timestamptz | Required. |
| `version` | integer | Optimistic version. |

Required constraints and indexes:

- Unique `(tenant_id, store_id, business_date, order_no)`.
- FK `(store_id, tenant_id)` to `stores(id, tenant_id)`.
- `table_id` must belong to the same tenant/store when provided.
- `customer_id` must belong to the same tenant when provided.
- `terminal_id` must belong to the same tenant/store when provided.
- Check `sales_channel in ('dine_in','takeaway','delivery')`.
- Check `status in ('open','held','closed','voided')`.
- Amount checks: subtotal, discount, service charge, tax, total, paid, and change amounts are `>= 0`; rounding may be negative or positive.
- Index `(tenant_id, store_id, business_date, status, opened_at)`.
- Index `(tenant_id, store_id, customer_id, opened_at)` where `customer_id is not null`.

State rules:

| Current | Command | Next | Notes |
|---|---|---|---|
| none | open | `open` | Allocates order number. |
| `open` | hold | `held` | Optional Phase 1 if UI supports parking orders. |
| `held` | resume | `open` | Optional Phase 1. |
| `open` or `held` | close | `closed` | Requires non-void lines and full payment. |
| `open` or `held` | void | `voided` | Requires `pos.order.void`. |
| `closed` | void | rejected | Post-close refund is Phase 2. |

#### `pos_order_lines`

Purpose: order item snapshot and line status.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `order_id` | uuid | Required. |
| `line_no` | integer | Required within order. |
| `product_id` | uuid | Nullable for open-price lines. |
| `product_code` | text | Required snapshot. |
| `product_name` | text | Required snapshot. |
| `unit_code` | text | Required. |
| `quantity` | numeric(12,3) | Required positive. |
| `unit_price` | numeric(12,2) | Required non-negative snapshot. |
| `gross_amount` | numeric(12,2) | Required. |
| `discount_amount` | numeric(12,2) | Required default 0. |
| `service_charge_amount` | numeric(12,2) | Required default 0. |
| `tax_amount` | numeric(12,2) | Required default 0. |
| `net_amount` | numeric(12,2) | Required. |
| `tax_rule_id` | uuid | Nullable snapshot link. |
| `status` | text | `active`, `voided`. |
| `note` | text | Nullable. |
| `void_reason` | text | Nullable. |
| `created_by` | uuid | Actor account when available. |
| `voided_by` | uuid | Nullable. |
| `created_at` | timestamptz | Required. |
| `updated_at` | timestamptz | Required. |
| `voided_at` | timestamptz | Nullable. |
| `version` | integer | Optimistic version. |

Required constraints:

- Unique `(tenant_id, store_id, order_id, line_no)`.
- Order FK must be same tenant/store.
- Product and tax rule must belong to same tenant/store when present.
- Check `quantity > 0`.
- Check amount fields except none are `>= 0`.
- Check `status in ('active','voided')`.
- Voided lines require `void_reason` and `voided_at`.

#### `pos_order_events`

Purpose: append-only audit trail and idempotency anchor for order commands.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `order_id` | uuid | Nullable for failed open commands; required otherwise. |
| `event_type` | text | `open`, `add_line`, `update_line`, `void_line`, `discount`, `hold`, `resume`, `close`, `void_order`, `receipt_printed`, `cash_drawer_opened`. |
| `idempotency_key` | text | Required for commands. |
| `actor_user_id` | uuid | Nullable. |
| `before_json` | jsonb | Nullable. |
| `after_json` | jsonb | Nullable. |
| `created_at` | timestamptz | Required. |

Required constraints:

- Unique `(tenant_id, store_id, event_type, idempotency_key)`.
- Check allowed `event_type` values.
- Index `(tenant_id, store_id, order_id, created_at)`.

### POS Payments

#### `pos_payments`

Purpose: payment entry under an order.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `order_id` | uuid | Required. |
| `payment_no` | text | Required order-scoped payment number. |
| `method` | text | `cash`, `card`, `paynow`, `voucher`, `manual`. |
| `status` | text | `recorded`, `voided`. |
| `amount` | numeric(12,2) | Required positive. |
| `currency` | text | Required uppercase 3-letter currency. |
| `received_amount` | numeric(12,2) | Nullable, useful for cash tendered. |
| `change_amount` | numeric(12,2) | Required default 0. |
| `reference` | text | Nullable non-sensitive reference. |
| `note` | text | Nullable. |
| `recorded_by` | uuid | Actor account when available. |
| `voided_by` | uuid | Nullable. |
| `recorded_at` | timestamptz | Required. |
| `voided_at` | timestamptz | Nullable. |
| `version` | integer | Optimistic version. |

Required constraints:

- Unique `(tenant_id, store_id, order_id, payment_no)`.
- Order FK must be same tenant/store.
- Check `method in ('cash','card','paynow','voucher','manual')`.
- Check `status in ('recorded','voided')`.
- Check `amount > 0`.
- Check `received_amount is null or received_amount >= amount` for `cash` when change is recorded.
- Check `currency = upper(currency) and length(currency) = 3`.

#### `pos_payment_events`

Purpose: append-only payment command event table and idempotency anchor.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `id` | uuid | Primary key. |
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `order_id` | uuid | Required. |
| `payment_id` | uuid | Nullable for failed commands. |
| `event_type` | text | `record_payment`, `void_payment`, `return_change`. |
| `idempotency_key` | text | Required. |
| `actor_user_id` | uuid | Nullable. |
| `payload_json` | jsonb | Required default `{}`. |
| `created_at` | timestamptz | Required. |

Required constraints:

- Unique `(tenant_id, store_id, event_type, idempotency_key)`.
- Payment/order FK must be same tenant/store when present.

### Sequences

#### `pos_number_sequences`

Purpose: transaction-safe store/business-date numbers.

Design-level columns:

| Column | Type | Notes |
|---|---|---|
| `tenant_id` | uuid | Required. |
| `store_id` | uuid | Required. |
| `sequence_type` | text | `order`, `payment`, `receipt`. |
| `business_date` | date | Required. |
| `next_value` | integer | Required, starts at 1. |
| `updated_at` | timestamptz | Required. |

Required constraints:

- Primary key `(tenant_id, store_id, sequence_type, business_date)`.
- Check `sequence_type in ('order','payment','receipt')`.
- Check `next_value > 0`.

The sequence allocation must run inside the same transaction as the command that consumes the number.

## API Design

All endpoints use `/api/v1`. Store-scoped operational endpoints include `stores/{storeId}`. Request and response DTOs are explicit and never expose JPA entities or raw persistence rows.

### Staff POS Catalog Query

```http
GET /api/v1/stores/{storeId}/pos/catalog
```

Permission:

```text
@RequireAppGate(appKey = "pos", permission = "pos.order.view")
```

Response shape:

```json
{
  "success": true,
  "businessDate": "2026-08-05",
  "currency": "SGD",
  "categories": [
    {
      "id": "71000000-0000-0000-0000-000000000001",
      "parentId": null,
      "code": "MAIN",
      "name": "Main",
      "sortOrder": 10
    }
  ],
  "products": [
    {
      "id": "72000000-0000-0000-0000-000000000001",
      "categoryId": "71000000-0000-0000-0000-000000000001",
      "code": "BEEF-NOODLE",
      "name": "Beef Noodle",
      "shortName": "Beef Noodle",
      "status": "active",
      "prices": [
        {
          "id": "73000000-0000-0000-0000-000000000001",
          "unitCode": "bowl",
          "channel": "dine_in",
          "price": "8.80",
          "currency": "SGD"
        }
      ]
    }
  ]
}
```

### Tenant Admin Catalog Management

```http
GET    /api/v1/stores/{storeId}/tenant-admin/pos/categories
POST   /api/v1/stores/{storeId}/tenant-admin/pos/categories
PATCH  /api/v1/stores/{storeId}/tenant-admin/pos/categories/{categoryId}
GET    /api/v1/stores/{storeId}/tenant-admin/pos/products
POST   /api/v1/stores/{storeId}/tenant-admin/pos/products
PATCH  /api/v1/stores/{storeId}/tenant-admin/pos/products/{productId}
POST   /api/v1/stores/{storeId}/tenant-admin/pos/products/{productId}/prices
PATCH  /api/v1/stores/{storeId}/tenant-admin/pos/products/{productId}/prices/{priceId}
GET    /api/v1/stores/{storeId}/tenant-admin/pos/tax-rules
POST   /api/v1/stores/{storeId}/tenant-admin/pos/tax-rules
PATCH  /api/v1/stores/{storeId}/tenant-admin/pos/tax-rules/{taxRuleId}
```

Permission:

```text
@RequireAppGate(appKey = "pos", permission = "pos.catalog.manage")
```

Catalog mutation requests require `version` for updates. Create requests require an `idempotencyKey` only for bulk import or repeated client submissions; simple create may rely on unique code conflict if the UI cannot retry automatically.

### Orders

```http
GET  /api/v1/stores/{storeId}/pos/orders
POST /api/v1/stores/{storeId}/pos/orders
GET  /api/v1/stores/{storeId}/pos/orders/{orderId}
POST /api/v1/stores/{storeId}/pos/orders/{orderId}/lines
PATCH /api/v1/stores/{storeId}/pos/orders/{orderId}/lines/{lineId}
POST /api/v1/stores/{storeId}/pos/orders/{orderId}/lines/{lineId}/void
POST /api/v1/stores/{storeId}/pos/orders/{orderId}/discount
POST /api/v1/stores/{storeId}/pos/orders/{orderId}/close
POST /api/v1/stores/{storeId}/pos/orders/{orderId}/void
```

Permission mapping:

| Endpoint | Permission |
|---|---|
| List/get | `pos.order.view` |
| Create | `pos.order.create` |
| Add/update line | `pos.order.update` |
| Price override | `pos.price.override` plus `pos.order.update` |
| Discount | `pos.order.discount` |
| Void line/order | `pos.order.void` |
| Close | `pos.order.close` |

Create order request:

```json
{
  "idempotencyKey": "pos-open-20260805-store-20000000-terminal-a-001",
  "terminalId": "74000000-0000-0000-0000-000000000001",
  "tableId": "30000000-0000-0000-0000-000000000001",
  "customerId": null,
  "salesChannel": "dine_in",
  "note": "Window seat"
}
```

Create order response:

```json
{
  "success": true,
  "replayed": false,
  "order": {
    "id": "75000000-0000-0000-0000-000000000001",
    "tenantId": "10000000-0000-0000-0000-000000000983",
    "storeId": "20000000-0000-0000-0000-000000000983",
    "businessDate": "2026-08-05",
    "orderNo": "0001",
    "salesChannel": "dine_in",
    "status": "open",
    "currency": "SGD",
    "subtotalAmount": "0.00",
    "discountAmount": "0.00",
    "serviceChargeAmount": "0.00",
    "taxAmount": "0.00",
    "roundingAmount": "0.00",
    "totalAmount": "0.00",
    "paidAmount": "0.00",
    "changeAmount": "0.00",
    "lines": [],
    "payments": [],
    "version": 0
  }
}
```

Add line request:

```json
{
  "idempotencyKey": "pos-add-line-20260805-order-0001-line-001",
  "productId": "72000000-0000-0000-0000-000000000001",
  "priceId": "73000000-0000-0000-0000-000000000001",
  "quantity": "2.000",
  "note": "Less spicy",
  "orderVersion": 0
}
```

Close order request:

```json
{
  "idempotencyKey": "pos-close-20260805-order-0001",
  "version": 4
}
```

Close behavior:

- Reject if the order is not `open` or `held`.
- Reject if no active lines exist.
- Reject if paid amount is less than total amount.
- Set `closed_at`, `closed_by`, and `status = closed`.
- Create a receipt snapshot if no snapshot exists for this close command.

### Payments

```http
POST /api/v1/stores/{storeId}/pos/orders/{orderId}/payments
POST /api/v1/stores/{storeId}/pos/orders/{orderId}/payments/{paymentId}/void
```

Permission mapping:

| Endpoint | Permission |
|---|---|
| Record payment | `pos.payment.take` |
| Void payment | `pos.payment.void` |

Record cash payment request:

```json
{
  "idempotencyKey": "pos-pay-cash-20260805-order-0001-001",
  "method": "cash",
  "amount": "20.00",
  "currency": "SGD",
  "receivedAmount": "20.00",
  "reference": null,
  "note": null,
  "orderVersion": 3
}
```

The backend calculates `changeAmount` from order remaining amount and received amount. It must not trust a client-provided change value.

### Receipt And Cash Drawer

```http
GET  /api/v1/stores/{storeId}/pos/orders/{orderId}/receipt
POST /api/v1/stores/{storeId}/pos/orders/{orderId}/receipt/print
POST /api/v1/stores/{storeId}/pos/cash-drawer/open
```

Permission mapping:

| Endpoint | Permission |
|---|---|
| Receipt get/print | `pos.receipt.print` |
| Cash drawer open event | `pos.cash_drawer.open` |

Phase 1 receipt printing returns a browser-print payload or HTML print view. Direct network printer and cash drawer hardware control are reserved.

### Sales Report

```http
GET /api/v1/stores/{storeId}/tenant-admin/pos/reports/sales-summary
GET /api/v1/stores/{storeId}/tenant-admin/pos/reports/orders
```

Permission:

```text
@RequireAppGate(appKey = "pos", permission = "pos.report.view")
```

Reports are read-only projections over closed and voided POS orders. They do not replace accounting exports.

### Error Codes

Use stable POS error codes:

| HTTP | Code | Meaning |
|---:|---|---|
| 401 | `UNAUTHENTICATED` | No current actor. |
| 403 | `FORBIDDEN` | Actor lacks role, store access, App Gate entitlement, or permission. |
| 400 | `REQUEST_INVALID` | Invalid body, amount, quantity, status, currency, or idempotency key. |
| 404 | `STORE_NOT_FOUND` | Store does not exist or actor cannot access it. |
| 404 | `POS_PRODUCT_NOT_FOUND` | Product not found in the same tenant/store. |
| 404 | `POS_ORDER_NOT_FOUND` | Order not found in the same tenant/store. |
| 404 | `POS_ORDER_LINE_NOT_FOUND` | Line not found in the same order. |
| 404 | `POS_PAYMENT_NOT_FOUND` | Payment not found in the same order. |
| 409 | `POS_ORDER_STATE_CONFLICT` | Command invalid for current order status. |
| 409 | `POS_PAYMENT_CONFLICT` | Payment would overpay invalidly, void closed payment, or mismatch currency. |
| 409 | `VERSION_CONFLICT` | Optimistic version mismatch. |
| 409 | `IDEMPOTENCY_CONFLICT` | Same key used with a different command payload. |
| 500 | `PERSISTENCE_ERROR` | Database operation failed. |

## Page Flow

### Store Staff POS Workbench

Route:

```text
/stores/:storeId/pos
```

Primary layout:

1. Store top bar with store switcher, business date, terminal selector, and cashier identity.
2. Left or primary area: active order ticket with lines, quantities, discounts, totals, payments, and close action.
3. Right or secondary area: category tabs, product grid, product search, and common modifiers.
4. Payment panel: cash quick amounts, card/manual/PayNow entry, remaining amount, and change.
5. Receipt panel: visible only after close or when viewing a closed order.

Required states:

- Loading App Gate and catalog.
- Product line not enabled for tenant.
- Store POS disabled.
- Permission denied.
- Empty catalog.
- Open order.
- Held order list if hold/resume is included in Phase 1.
- Payment in progress.
- Close order success.
- Close order rejected with stable error message.
- Receipt print payload ready.

Mobile/tablet behavior:

- Ticket and product grid can switch as tabs or split panels.
- Payment action is fixed but does not obscure totals or line controls.
- Buttons use concise labels and stable dimensions.

### Tenant Admin POS Catalog

Routes:

```text
/stores/:storeId/admin/pos/catalog
/stores/:storeId/admin/pos/tax-rules
/stores/:storeId/admin/pos/terminals
/stores/:storeId/admin/pos/reports
```

Required workflows:

1. Manage categories.
2. Manage products.
3. Manage prices by unit and channel.
4. Mark product sold out or hidden.
5. Manage tax/service charge rule.
6. Manage terminal display/receipt settings.
7. View sales summary and order history.

The tenant admin page must not include platform subscription controls. Those remain in platform billing pages.

### Platform Product Line And Billing

Existing platform product-line pages should show `pos` beside `reservation_queue`.

Platform admins can:

- Create or update `pos` product-line metadata.
- Set monthly/yearly store unit prices for `pos`.
- Open, renew, suspend, or cancel POS billing by tenant/store through existing store-level billing workflows.

The platform billing page should not inspect POS order revenue in Phase 1. Store revenue reports are POS tenant-admin reports.

## SimsPOS Field Mapping

### Product Catalog

| SimsPOS | RPB POS | Notes |
|---|---|---|
| `GD_GOODS.ID` | `pos_products.legacy_source_id` only in import tooling, not Phase 1 table by default | Keep import metadata outside core table unless migration is approved. |
| `GD_GOODS.CODE` | `pos_products.code` | Scope changes from global code to tenant/store code. |
| `GD_GOODS.NAME` from model/API | `pos_products.name` | Name was not visible in the DDL excerpt but exists in JS model. |
| `GD_GOODS.ENABLE` | `pos_products.status` | `true -> active`, `false -> hidden` or `archived` by import rule. |
| `GD_GOODS.CLZ0/CLZ1/CLZ2` | `pos_product_categories` | Convert flat multi-level class codes into category rows. |
| `GD_GOODS.TIME_PRICE` | Phase 2 price schedule | Not in Phase 1. |
| `GD_GOODS.ATTACH_CODES` | Phase 2 modifiers | Not in Phase 1 core unless essential. |
| `GD_GOODS_PRICE.TYPE` | `pos_product_prices.channel` or price type | Map known values during import. |
| `GD_GOODS_PRICE.UNIT` | `pos_product_prices.unit_code` | Normalize text. |
| `GD_GOODS_PRICE.PRICE` | `pos_product_prices.price` | Required non-negative numeric. |
| `GD_TAX_FEE.VALUE` | `pos_tax_rules.tax_rate` or `service_charge_rate` | Import rule must classify SimsPOS tax/fee type. |

### Order

| SimsPOS | RPB POS | Notes |
|---|---|---|
| `PS_ORDER.ID` | `pos_orders.legacy_source_id` in import tooling | Core table uses new UUID. |
| `PS_ORDER.CODE` | `pos_orders.order_no` | RPB order number is store/business-date scoped. |
| `PS_ORDER.POS_CODE` | `pos_terminals.code` or reserved terminal external code | Create terminal if importing. |
| `PS_ORDER.STATUS` | `pos_orders.status` | `new -> open`, `close -> closed`; unknown statuses require import review. |
| `PS_ORDER.CASHIER_CODE` | `opened_by` or cashier snapshot in event payload | RPB uses authenticated account id where possible. |
| `PS_ORDER.CASHIER_NAME` | cashier display snapshot in events or receipt snapshot | Do not trust as identity source. |
| `PS_ORDER.CST_CODE` | `customer_id` through lookup or customer snapshot | RPB customer mapping must be tenant-scoped. |
| `PS_ORDER.TOTAL_MONEY` | `pos_orders.total_amount` | RPB recalculates from lines/payments. |
| `PS_ORDER.DISCOUNT` | `pos_orders.discount_amount` | Negative SimsPOS discount values normalize to positive discount amount. |
| `PS_ORDER.TAX` | `pos_orders.tax_amount` | RPB tax rule controls recalculation. |
| `PS_ORDER.FEE` | `pos_orders.service_charge_amount` | If type is service fee. |
| `PS_ORDER.PAYIN_MONEY` | `pos_orders.paid_amount` | Derived from `pos_payments`. |
| `PS_ORDER.WIPE_ZERO` | `pos_orders.rounding_amount` | May be negative or positive. |

### Order Line

| SimsPOS | RPB POS | Notes |
|---|---|---|
| `PS_ORDER_ITEM.ID` | `pos_order_lines.legacy_source_id` in import tooling | Not needed for fresh Phase 1. |
| `PS_ORDER_ITEM.CODE` | `pos_orders.order_no` lookup during import | SimsPOS line code appears to reference order code. |
| `PS_ORDER_ITEM.PRD_CODE` | `pos_order_lines.product_code` | Snapshot. |
| `PS_ORDER_ITEM.PRD_NAME` | `pos_order_lines.product_name` | Snapshot. |
| `PS_ORDER_ITEM.PRD_AMOUNT` | `pos_order_lines.quantity` | Decimal precision retained. |
| `PS_ORDER_ITEM.PRD_UNIT` | `pos_order_lines.unit_code` | Normalize text. |
| `PS_ORDER_ITEM.UNIT_PRICE` | `pos_order_lines.unit_price` | Snapshot. |
| `PS_ORDER_ITEM.TOTAL_MONEY` | `pos_order_lines.gross_amount` or `net_amount` by import rule | Need sample-specific validation. |
| `PS_ORDER_ITEM.DISCOUNT` | `pos_order_lines.discount_amount` | Normalize negative discounts to positive amount. |
| `PS_ORDER_ITEM.TAX` | `pos_order_lines.tax_amount` | Snapshot. |
| `PS_ORDER_ITEM.FEE` | `pos_order_lines.service_charge_amount` | Snapshot. |
| `PS_ORDER_ITEM.OWN_ID/OWN_TYPE` | Phase 2 set/modifier relationship | Not in Phase 1 core unless set meals are approved. |

### Payment And Cash Box

| SimsPOS | RPB POS | Notes |
|---|---|---|
| `PS_MONEY_BOX.ID` | `pos_payments.legacy_source_id` or `pos_payment_events.legacy_source_id` in import tooling | Not in core Phase 1. |
| `PS_MONEY_BOX.ORD_CODE` | `pos_orders.order_no` lookup | Must be tenant/store scoped during import. |
| `PS_MONEY_BOX.ACTION` | `pos_payment_events.event_type` | `RcvCash -> record_payment`, `RtnChange -> return_change`. |
| `PS_MONEY_BOX.TYPE` | `pos_payments.method` | Map known values to `cash`, `card`, `paynow`, `voucher`, or `manual`; reject or quarantine unknown values during import. |
| `PS_MONEY_BOX.MONEY` | `pos_payments.amount` or event payload | Negative change entries map to change amount/event. |
| `PS_MONEY_BOX.CURRENCY` | `pos_payments.currency` | Normalize to store currency if historical values differ. |
| `PS_MONEY_BOX.CARD_NO` | Do not store raw | Store only masked or non-sensitive reference if approved. |
| `PS_MONEY_BOX.CASHIER_CODE/NAME` | actor/payment snapshot | RPB account id is authoritative. |

### Table And Terminal

| SimsPOS | RPB POS | Notes |
|---|---|---|
| `TB_TABLE_INFO.CODE` | existing RPB dining table code | Prefer mapping into existing table module, not POS-owned table table. |
| `TB_TABLE_INFO.AREA` | existing RPB store area | Reuse store/table module. |
| `TB_TABLE_INFO.SEAT` | dining table capacity | Existing RPB table capacity owns this. |
| `TB_TABLE_INFO.MIN_CHARGE` | Phase 2 table minimum charge | Not Phase 1 unless required. |
| `TB_CASH_POINT.CODE` | `pos_terminals.code` | Store-scoped terminal. |
| `TB_CASH_POINT.PRINTER_CODE` | `pos_terminals.config_json.printerCode` reserved | No direct hardware in Phase 1. |
| `PT_PRINTER` / `PT_PRINTER_TPL` | `pos_terminals.config_json` and receipt snapshot template reservation | Browser print first. |

### Inventory

| SimsPOS | RPB POS | Notes |
|---|---|---|
| `ST_STORE` | existing RPB store / future inventory warehouse | SimsPOS store may mean stock warehouse, not RPB tenant store. |
| `ST_STORE_ITEM` | Phase 2 inventory stock | Not Phase 1. |
| `ST_IN_ORDER` | Phase 2 inventory inbound | Not Phase 1. |
| `ST_OUT_ORDER` | Phase 2 inventory outbound | Not Phase 1. |
| `ST_CHECK_ORDER` | Phase 2 stock check | Not Phase 1. |

## Data Flow

### Open And Sell

```text
Actor opens /stores/:storeId/pos
-> App Gate checks app_key=pos and entry permission
-> POS catalog loads active categories/products/prices
-> Cashier opens order with idempotency key
-> Backend allocates order_no for store/business_date
-> Cashier adds lines
-> Backend snapshots product name, unit, price, tax rule, and recalculates totals
-> Cashier records payments
-> Backend records payment events and recalculates paid/change amounts
-> Cashier closes order
-> Backend validates full payment, closes order, and creates receipt snapshot
```

### Tenant Admin Catalog

```text
Tenant admin enters POS catalog page
-> App Gate checks app_key=pos and pos.catalog.manage
-> Admin creates categories/products/prices/tax rules
-> Backend validates tenant/store ownership and optimistic version
-> Staff POS catalog reflects active rows after reload
```

### Platform Activation

```text
Platform admin creates or enables pos platform app
-> Platform billing opens pos subscription for tenant/store
-> platformbilling syncs tenant_app_entitlements and store_app_settings
-> App Gate allows POS endpoints for enabled stores and permitted actors
```

## Calculation Rules

Phase 1 uses server-side calculation as authoritative:

1. Line gross amount = `quantity * unit_price`.
2. Line discount amount is either explicit amount or derived from discount rate, always stored as positive amount.
3. Service charge and tax use the line's tax rule snapshot at command time.
4. Order totals are derived from non-void lines.
5. Payment totals are derived from non-void payments.
6. Close is allowed only when `paid_amount >= total_amount`.
7. Change is calculated by server and represented separately from paid amount.
8. Currency must match the order currency for all Phase 1 payments.

Open question deliberately closed for Phase 1:

```text
Multi-currency payment is not supported in Phase 1.
```

## Tenant And Store Isolation

Every POS query and mutation must validate:

1. The path `storeId` belongs to the authenticated actor's tenant and accessible store list.
2. Every referenced product, price, tax rule, terminal, order, line, payment, table, and customer belongs to the same tenant and required store.
3. Request bodies cannot override `tenant_id` or `store_id`.
4. Business date is derived from store time policy, not from client text.
5. Unique keys include tenant/store scope.
6. Reports filter by tenant/store and never aggregate cross-tenant data.

## Audit And Idempotency

Required idempotent commands:

- Open order.
- Add line.
- Update line quantity or price.
- Void line.
- Apply discount.
- Record payment.
- Void payment.
- Close order.
- Void order.
- Receipt print.
- Cash drawer open.

Repeated command behavior:

| Case | Behavior |
|---|---|
| Same idempotency key and same payload after success | Return original result with `replayed = true`. |
| Same idempotency key and different payload | Return `IDEMPOTENCY_CONFLICT`. |
| Same idempotency key after command failed before mutation | Allow retry if no event was committed. |
| Stale version with new idempotency key | Return `VERSION_CONFLICT`. |

Audit data lives in POS event tables. Critical denial events remain App Gate denial audit logs.

## Testing Requirements

### Database Review Matrix

| Scenario | Required Coverage |
|---|---|
| Migration creates POS tables | Tables, checks, scoped FKs, unique keys, indexes. |
| Tenant/store isolation | Cross-tenant product/order/payment references are rejected by FK or service validation. |
| Order number sequence | Concurrent opens allocate unique store/business-date order numbers. |
| Amount constraints | Negative quantity, negative price, invalid currency, and invalid status are rejected. |
| Idempotency uniqueness | Duplicate key cannot create duplicate order/payment/event. |

### API Review Matrix

| Scenario | Required Coverage |
|---|---|
| App Gate enabled | POS endpoints pass when tenant/store/app/permission are valid. |
| App Gate disabled | Product disabled, tenant not entitled, expired entitlement, or store app disabled returns 403. |
| Permission denied | Each command rejects actors missing the required POS permission. |
| Stable errors | Documented POS error codes map to HTTP statuses. |
| DTO boundary | No JPA entity or persistence row leaks through response. |
| Replay | Idempotent commands return replay responses. |

### TDD Review Matrix

| Scenario | Required Coverage |
|---|---|
| Open order | Creates order with business date, order number, totals, and event. |
| Add product line | Snapshots product and recalculates totals. |
| Price override | Requires `pos.price.override`. |
| Discount | Requires `pos.order.discount` and recalculates totals. |
| Payment | Records cash/card/manual payment and server-calculates change. |
| Close | Rejects underpaid order, closes fully paid order, creates receipt snapshot. |
| Void | Rejects post-close void in Phase 1. |
| Cross-store access | Store A actor cannot mutate Store B POS order. |
| Frontend states | Loading, empty catalog, permission denied, order open, payment error, close success. |

### Manual Smoke

1. Platform admin enables `pos` for a tenant and one store.
2. Tenant admin creates one category, one product, one price, and one tax rule.
3. Staff opens `/stores/{storeId}/pos`.
4. Staff opens an order, adds two items, records cash payment, closes order, and opens receipt print view.
5. Disable store app setting for `pos` and confirm POS endpoints and entry are denied.
6. Enable sibling store without catalog and confirm it shows empty catalog, not sibling store products.

## Rollout Plan

### Phase 1

- Product-line seed for `pos`.
- App Gate entry permission support for POS.
- POS catalog tables and tenant-admin catalog UI.
- POS order, line, payment, event, sequence, terminal, and receipt snapshot tables.
- Store staff POS workbench.
- Basic sales report.
- Browser-print receipt payload.
- Tests from the required matrices.

### Phase 2

- SimsPOS import tooling for catalog, orders, and historical payment snapshots.
- Set meals, modifiers, attach items, and time-based pricing.
- Inventory stock, inbound, outbound, stock check, and stock deduction.
- KDS and kitchen print routing.
- Hardware printer and cash drawer adapters.
- Member points, vouchers, stored value, and loyalty integration.
- Refunds, post-close voids, settlement reports, and accounting export.
- Offline-first tablet mode with sync conflict policy.

## Implementation Notes For Later

- Before implementation, repair or verify the local PostgreSQL runtime pointer if runtime or migration validation is needed, following `target/local-postgres-current.txt` and the local runtime guide.
- Do not create executable migrations from this document without running database review again.
- Do not add POS APIs without a separate API contract or implementation plan.
- Do not broaden local runtime allowlists except for explicitly tested POS routes.
- Add release notes only after implementation is complete.

## Review Notes

### API Review

- Paths use `/api/v1`.
- Store-scoped POS endpoints include `stores/{storeId}`.
- Platform product-line and billing APIs remain in existing platform modules.
- App Gate permission is specified for each endpoint group.
- Command endpoints that can be retried require idempotency.
- Error codes and status mapping are stable.
- DTOs are explicit and do not expose persistence entities.

### Database Review

- Tenant/store operational data includes `tenant_id` and `store_id`.
- Store-scoped FKs are required for stores and same-store POS references.
- Unique constraints include tenant/store scope.
- Enum-like values have check constraints.
- Monetary fields use `numeric(12,2)` and explicit non-negative checks.
- Sequence allocation is store/business-date scoped.
- No executable migration is created by this document.

### TDD Review

- The test matrix covers happy path, permission failure, App Gate denial, duplicate command replay, cross-tenant/store access, stale version, and invalid state transitions.
- Frontend tests must cover empty, loading, permission denied, API error, and successful close states.

### Code Review

- The design keeps controllers out of repositories.
- POS does not mutate platformbilling or App Gate runtime state.
- POS order payment is separate from platform subscription billing.
- Hardware, inventory, loyalty, and migration import are explicitly deferred.
