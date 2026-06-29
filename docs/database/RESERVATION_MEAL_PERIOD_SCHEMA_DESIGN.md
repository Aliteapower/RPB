# Reservation Meal Period Schema Design

## Scope

This schema adds platform default meal-period seed configuration and store-scoped effective reservation meal-period settings.

## Tables

### platform_reservation_meal_period_seeds

Platform data. No tenant or store scope.

Columns:

- `id uuid primary key`
- `period_key text not null`
- `display_name text not null`
- `start_local_time time not null`
- `end_local_time time not null`
- `crosses_next_day boolean not null default false`
- `slot_interval_minutes integer not null default 30`
- `status text not null default 'active'`
- `sort_order integer not null default 0`
- `created_at`, `updated_at`, `deleted_at`, `version`

### store_reservation_meal_period_settings

Store-scoped setting controlling inheritance.

Columns:

- `id uuid primary key`
- `tenant_id uuid not null`
- `store_id uuid not null`
- `use_platform_seed boolean not null default true`
- audit/version columns

### store_reservation_meal_periods

Store-owned editable periods used when `use_platform_seed=false`.

Columns mirror platform seed and add `tenant_id`, `store_id`, and nullable `source_seed_id`.

## Constraints

- Platform `period_key` is unique for active seed rows.
- Store `period_key` is unique inside `tenant_id + store_id`.
- Store tables have `(store_id, tenant_id)` FK to `stores`.
- `slot_interval_minutes` must be between 5 and 240.
- `status` values are `active` or `disabled`.
- `sort_order` supports deterministic display.

## Seed Data

Default platform rows:

- `lunch`: `11:00-15:00`, not cross-day, 30-minute interval, active, sort 10.
- `dinner`: `17:00-00:30`, cross-day, 30-minute interval, active, sort 20.

## Permission

Add `platform.reservation_meal_period.manage` to platform admin accounts.

## Rollback

Rollback is additive: disable the new UI routes and drop V019 tables only after confirming no stores depend on custom periods. Reservation records remain compatible because they already store `business_date` and timestamps.
