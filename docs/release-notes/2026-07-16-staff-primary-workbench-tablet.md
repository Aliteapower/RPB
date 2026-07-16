# Release Notes

## Version / Date

2026-07-16 - Staff primary workbench tablet adaptation

## New

- Added the shared `StaffPrimaryWorkbench` responsive shell for the four primary tenant employee H5 pages: Home, Reservation, Queue, and Table.
- Added tablet portrait and landscape presentation from 768px through 1366px, with an 88px left workbench navigation rail and a centered 1200px large-screen content boundary.

## Changed

- Home, Reservation, Queue, and Table now use page-specific tablet grids while continuing to share one responsive shell and navigation policy.
- At 1024px and wider, Home, Reservation, and Queue use operational two-column layouts; Table expands its resource presentation independently inside the same shell.
- The shared shell reserves tablet topbar space for the locale switcher and clips mobile full-bleed painting without changing the existing bottom navigation.

## Fixed

- Tablet users no longer receive the narrow phone-width workbench with a bottom navigation bar centered in a wide viewport.
- The locale switcher no longer overlaps employee topbar actions on tablet widths.
- The existing mobile full-bleed topbar no longer creates horizontal document scrolling.

## Migration

- No database migration, data rewrite, API contract change, dependency change, or runtime configuration change is required.

## Permission

- No App Gate, role, route guard, authentication, or authorization behavior changes are included.

## Risk

- Low: the implementation changes Vue templates and responsive CSS only; page API calls, state transitions, dialogs, and business actions remain unchanged.
- Breakpoint behavior is centralized in `StaffPrimaryWorkbench` and `StaffBottomNav`; page-specific grids remain owned by their page modules.
- The new bundle has not been deployed to production. The authenticated `lsc106` site was inspected read-only as the pre-deployment baseline and still serves the previous narrow layout.

## Verification

- Contract and regression suite: 7 focused test classes, 38 tests, 0 failures, 0 errors, 0 skipped.
- Frontend production build: `vue-tsc --noEmit && vite build` completed successfully with 357 transformed modules.
- Browser geometry matrix: Home, Reservation, Queue, and Table at 390x844, 768x1024, 1024x768, and 1366x1024; all 16 combinations had no horizontal overflow or locale-switcher overlap.
- Visual checks confirmed the existing mobile bottom navigation, tablet portrait navigation rail, tablet landscape two-column work areas, and the 1200px centered large-landscape boundary.
- Local visual validation used the pointer-backed runtime with an empty application dataset, so error and empty states were rendered; a post-deployment authenticated production smoke test with real data remains required.

## Deployment

- Publish the frontend bundle through the normal production deployment process; no backend restart or Flyway action is required for this UI-only change.
- After publication, authenticate at the tenant employee H5 site and smoke-test all four primary pages in mobile, tablet portrait, and tablet landscape viewports without performing destructive business actions.

## Rollback Notes

- Restore the previous frontend bundle. No database, API, permission, configuration, or data rollback is required.
- Rolling back restores the previous phone-width tablet presentation and bottom navigation behavior.
