# Release Notes

## Version / Date

2026-07-08 - Platform tenant structure entity-store filtering

## New

- Platform tenant structure management now lets platform administrators select an operating entity and view only that entity's branches.
- The selected operating entity is highlighted, and the branch count summary reflects the current entity context.
- Adding a branch defaults to the currently selected active operating entity.

## Changed

- The branch list no longer shows all branches from every operating entity at once.
- Editing a branch switches the operating entity selection to that branch's current owner.
- Empty branch state now distinguishes between no branches at all and no branches under the selected operating entity.

## Fixed

- Reduced confusion in group tenant setup where branches from different operating entities were visually mixed in one list.

## Migration

- No database migration.
- No API request or response change.

## Permission

- No new App Gate permission.
- Existing platform tenant management permission remains the gate for this screen.

## Risk

- Frontend-only workflow change. Existing operating entity and branch records are unchanged.
- Inactive operating entities can still be selected for review, but adding a branch remains limited to active operating entities.

## Rollback Notes

- Roll back by redeploying the previous frontend bundle under `/opt/rpb/frontend`.
- No backend or schema rollback is required.
