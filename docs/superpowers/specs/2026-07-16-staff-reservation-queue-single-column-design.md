# Staff Reservation and Queue Single-Column Design

## Goal

Change the tenant employee H5 Reservation and Queue primary pages from a large-screen outer two-column layout to a single vertical content flow. The layout must remain single-column on phones, tablet portrait, tablet landscape, and wider browser viewports.

## Scope

- Reservation primary page: place the date and quick-action controls above the reservation list.
- Queue primary page: place queue management and filters above messages, empty state, and queue-ticket results.
- Keep both top and bottom regions at the full available width of the shared primary workbench surface.
- Keep the existing shared `StaffPrimaryWorkbench` shell and adaptive navigation unchanged.

## Architecture and OOD Boundaries

`StaffPrimaryWorkbench` continues to own only the shared responsive surface, navigation, and maximum content width. `ReservationTodayViewPage` and `QueueTicketListPage` continue to own their page-specific content flow.

The change is page-local CSS. It does not add layout flags to the shared shell, move business behavior between modules, or restructure page templates. This keeps each page responsible for its own visual composition without coupling the shared shell to reservation or queue concepts.

## Layout Contract

### Reservation

The existing DOM order remains:

1. Business-date switcher.
2. Reservation quick actions.
3. Today reservation list.
4. Action error, when present.

The outer `.reservation-workbench-body` remains a single-column grid at every viewport width. The 768px tablet padding remains unchanged. The 1024px two-column definition and its explicit grid row/column placement are removed.

### Queue

The existing DOM order remains:

1. Queue management and filters.
2. Queue messages.
3. Empty state or queue-ticket list.

The outer `.queue-workbench-body` remains a single-column grid at every viewport width. The 768px tablet padding and toolbar behavior remain unchanged. The 1024px two-column definition and its explicit grid row/column placement are removed.

The queue-ticket list's existing internal two-column card layout at 1200px and wider remains unchanged because it is inside the lower results region, not the outer page split identified in the request.

## Unchanged Behavior

- Phone presentation and bottom navigation.
- Tablet navigation rail and shared workbench surface.
- Home and Table primary pages.
- Page templates, Vue component boundaries, dialogs, API calls, state, events, routes, permissions, and translations.
- Backend, database, migrations, dependencies, and runtime configuration.

## Test Strategy

Use the existing `StaffPrimaryWorkbenchTabletUiValidationTest` as the TDD contract:

- First change the Reservation assertion to reject the old `minmax(280px, 0.38fr) minmax(0, 0.62fr)` outer grid.
- Change the Queue assertion to reject the old `minmax(300px, 340px) minmax(0, 1fr)` outer grid.
- Retain the assertion for Queue's internal 1200px two-column result cards.
- Run the focused test before implementation and confirm it fails for both old outer grids.
- Remove only the obsolete large-screen outer-grid rules, then rerun the focused test and frontend production build.

Browser verification covers Reservation and Queue at 390x844, 768x1024, 1024x768, and 1366x1024. Each viewport must show one full-width vertical outer flow with no horizontal overflow or locale-switcher overlap.

## Acceptance Criteria

- Reservation controls appear above the reservation list at all tested widths.
- Queue management appears above queue messages and results at all tested widths.
- Neither page creates an outer left/right content split at 1024px or wider.
- Queue result cards may still use their existing internal two-column arrangement at 1200px or wider.
- No files outside the two pages, their focused UI validation test, and delivery documentation require behavioral changes.

## Rollback

Restore the two removed 1024px outer-grid rules and their explicit grid placement declarations. No data, API, permission, or configuration rollback is required.
