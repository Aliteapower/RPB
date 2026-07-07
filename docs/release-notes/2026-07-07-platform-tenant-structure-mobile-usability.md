# Platform Tenant Structure Mobile Usability

## Version / Date

2026-07-07 frontend usability release.

## New

- Platform tenant management now has a mobile card layout for group / tenant rows.
- The operating entity and store structure panel now shows summary counts and mobile tabs before edit forms.

## Changed

- The tenant list action copy changed from “Stores” / “门店” to “Store structure” / “门店结构”.
- Operating entity and store forms now open only after add/edit actions. Secondary details are grouped under collapsible sections.

## Fixed

- Mobile tenant row actions no longer rely on a wide desktop table that can clip or overlap on phones.
- New group setup empty states now guide the platform admin to add branches under the default operating entity.

## Migration

- No database migration.
- No backend API or Flyway change.

## Permission

- No App Gate, role, or permission change.

## Risk

- Low frontend-only risk. Existing API payloads, tenant isolation, store access rules, and billing behavior are unchanged.

## Rollback Notes

- Roll back by redeploying the previous frontend bundle under `/opt/rpb/frontend`.
- No database rollback is required.
