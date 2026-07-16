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
- The frontend bundle was deployed to the `lsc106` production site on 2026-07-16. No backend, database, API, permission, environment, or runtime configuration change was included.

## Verification

- Contract and regression suite: 7 focused test classes, 38 tests, 0 failures, 0 errors, 0 skipped.
- Frontend production build: `vue-tsc --noEmit && vite build` completed successfully with 357 transformed modules.
- Browser geometry matrix: Home, Reservation, Queue, and Table at 390x844, 768x1024, 1024x768, and 1366x1024; all 16 combinations had no horizontal overflow or locale-switcher overlap.
- Visual checks confirmed the existing mobile bottom navigation, tablet portrait navigation rail, tablet landscape two-column work areas, and the 1200px centered large-landscape boundary.
- Authenticated production smoke matrix: Home, Reservation, Queue, and Table at 390x844, 768x1024, and 1024x768; all 12 combinations rendered real tenant data without horizontal overflow, locale-switcher overlap, or console errors.
- Production health checks returned `200` for the login page and new frontend asset, `401` for the unauthenticated `/api/auth/me` probe, and confirmed the backend service remained active.

## Deployment

- Deployed frontend source commit: `07f17545379882706e2b24ee487a16d11d56f87a`.
- Production entry asset changed from `/assets/index-DDcJTY8m.js` to `/assets/index-CEyPae27.js`.
- Deployment backup: `/opt/rpb/backups/20260716-110042-07f17545-frontend`.
- The deployment was frontend-only: no backend restart, Flyway action, database mutation, or destructive business action was performed.
- Browser-cache diagnostics briefly reproduced the previous hashed entry asset; a cache-busted navigation loaded the new entry asset and completed the full authenticated production matrix.

## Rollback Notes

- Restore `/opt/rpb/backups/20260716-110042-07f17545-frontend`. No database, API, permission, configuration, or data rollback is required.
- Rolling back restores the previous phone-width tablet presentation and bottom navigation behavior.
