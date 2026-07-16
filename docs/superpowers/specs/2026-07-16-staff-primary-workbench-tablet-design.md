# Tenant Staff Primary Workbench Tablet Design

**Date:** 2026-07-16
**Status:** Accepted for implementation planning
**Scope:** Frontend UI only

## Objective

Adapt the four tenant staff H5 primary workbench pages for tablet portrait and landscape use while preserving the existing mobile H5 experience:

- Home: `StoreStaffHomePage.vue`
- Reservation: `ReservationTodayViewPage.vue`
- Queue: `QueueTicketListPage.vue`
- Table: `TableResourceListPage.vue`

The tablet layout must use the available horizontal space, remain touch friendly, and preserve every existing route, permission, API call, state transition, action, message, and i18n key.

## Confirmed Product Decisions

- The login page is outside this change.
- Staff secondary-operation pages are outside this change.
- Tablet coverage includes portrait and landscape from 768px through 1366px.
- Below 768px, the current mobile H5 layout and fixed bottom navigation remain unchanged.
- At 768px and above, the four primary pages use a left navigation rail.
- Tablet density is medium and touch first: primary actions retain at least 40px height with 44px as the preferred touch-target size, while grids use additional horizontal space.
- The implementation follows the shared primary workbench shell approach rather than widening every staff page or duplicating tablet-specific pages.

## Scope Boundaries

### In Scope

- A reusable primary staff workbench shell.
- An adaptive mode for the existing shared staff navigation.
- Tablet-only layout rules for the four primary pages.
- Responsive validation and visual regression checks at representative mobile and tablet viewports.
- A UI-only release note.

### Out of Scope

- Login page changes.
- Staff secondary-operation page redesigns.
- API, router, Pinia, authentication, authorization, or business workflow changes.
- Reservation, QueueTicket, Table, Seating, Cleaning, or TableGroup state changes.
- Database schema, Flyway migration, dependency, or runtime configuration changes.
- A separate desktop application or duplicated tablet page implementation.

## OOD and Module Design

### `StaffPrimaryWorkbench`

Add a shared visual composition component for the four primary pages. Its responsibilities are limited to:

- rendering the existing `staff-workbench-shell` root;
- creating the tablet navigation/content grid;
- exposing a default slot for each page's existing top bar and body;
- rendering `StaffBottomNav` with the current `storeId` and `activeTab`.

Its public inputs are:

- `storeId`
- `activeTab`

It must not:

- fetch or transform business data;
- own Reservation, QueueTicket, Table, Seating, Cleaning, or TableGroup state;
- handle page actions or business events;
- infer authorization;
- duplicate store, locale, or timezone resolution.

The four pages retain ownership of their existing data loading, computed state, event handlers, dialogs, and business actions. Vue class inheritance will expose page-specific root classes where grid placement needs them, but responsive shell behavior belongs to this shared component.

### `StaffBottomNav`

Extend the existing navigation with an explicit adaptive-primary presentation mode. The component continues to own navigation item rendering, translated labels, active state, focus behavior, and route construction.

- Mobile/default mode remains the current fixed bottom navigation.
- Adaptive-primary mode becomes a sticky left rail at 768px and above.
- Existing secondary pages that instantiate `StaffBottomNav` directly retain the current default behavior.
- The primary shell is the only module that opts into adaptive-primary mode.

This explicit opt-in prevents a global shell change from unintentionally widening or restructuring secondary staff flows.

### Page Modules

Each page remains responsible only for its semantic content layout:

- Home owns KPI, operation, queue overview, and table overview grids.
- Reservation owns date selection, quick actions, list placement, and dialogs.
- Queue owns filter, message, ticket list, and ticket action placement.
- Table owns summary, area, resource, temporary group, and table action placement.

No page reimplements shell or navigation breakpoint rules.

## Responsive System

### Breakpoints

| Range | Layout |
|---|---|
| `<768px` | Existing mobile H5 presentation, including the fixed bottom navigation. |
| `768-1023px` | Tablet portrait or compact tablet: left rail plus a wider single-column content flow with optimized grids. |
| `>=1024px` | Tablet landscape or large tablet: left rail plus split work areas where the page benefits from them. |

The primary workbench has a maximum overall width of 1200px. At 768px, the 88px rail leaves 680px for the content surface before its internal page padding. At wider tablet sizes, the content column expands until the shell cap is reached.

### Shared Tablet Shell

At 768px and above:

- use a two-column grid with an 88px navigation rail and a `minmax(0, 1fr)` content surface;
- keep the navigation rail sticky for the viewport height;
- keep the top bar sticky inside the content surface;
- remove mobile bottom-navigation spacing from the four primary page bodies;
- prevent page-level horizontal overflow with `min-width: 0` on grid children;
- keep local horizontal scrolling only for chip and filter rows that already support it.

The existing `staffWorkbench.css` mobile shell remains the fallback. Tablet widening is scoped to `StaffPrimaryWorkbench`, not applied to every `.staff-workbench-shell` consumer.

## Page Layouts

### Home

Tablet portrait:

- the date strip, errors, operation toolbar, KPI row, queue overview, and table overview remain in reading order;
- the four primary KPI cards render in one row;
- operation entries remain three large touch targets;
- queue and table overview sections remain stacked.

Tablet landscape at 1024px and above:

- date, error, operation, and KPI regions span the full content width;
- queue overview and table overview render side by side;
- their internal party-size and status grids retain current labels and values.

### Reservation

Tablet portrait:

- retain the order date switcher, quick actions, reservation list, and action error;
- allow the wider content surface to improve list readability without changing list behavior.

Tablet landscape at 1024px and above:

- place the date switcher and quick-action panel in the left work column;
- place the reservation list and action feedback in the right work column;
- keep create, seating, and table-assignment dialogs as overlays with their existing state and events.

Page-level classes will be attached to the existing child component roots where necessary to position them in the grid. No child component receives business state solely for layout purposes.

### Queue

Tablet portrait:

- keep the management/filter panel above messages and tickets;
- keep ticket cards full width;
- preserve local horizontal scrolling for status and filter chips.

Tablet landscape at 1024px and above:

- use a left management column for heading, display link, status, area, party-size, phone, and reset filters;
- use the right work column for loading/error/success messages, empty state, and tickets;
- keep ticket cards in one column from 1024px through 1199px;
- switch the ticket list to two columns at 1200px and above, where each card retains touch-friendly action sizing.

All call, recall, skip, rejoin, seat, and cancellation handlers remain unchanged.

### Table

Tablet portrait:

- keep the six summary items in one readable row;
- render normal table resource cards in three columns;
- keep area filters, temporary group controls, bulk actions, and group resources in their existing order.

Tablet landscape at 1024px and above:

- render normal table resource cards in four columns;
- keep temporary group resources in a lower-density grid appropriate to their additional content;
- allow temporary group heading, name, and actions to use horizontal space without changing their actions;
- keep table switch and other dialogs unchanged.

All status labels, preassignment details, table actions, grouping rules, seating actions, and cleaning actions remain bound to the existing page logic.

## Data Flow and Business Safety

The responsive change is presentation-only:

```text
Existing page state and API calls
  -> existing page/components and event handlers
  -> StaffPrimaryWorkbench visual slot
  -> mobile or tablet CSS presentation
```

There is no viewport-dependent business branch. CSS media queries select presentation; JavaScript does not inspect device type or orientation. No resize listener, duplicated page state, or alternate tablet route is introduced.

Reservation, QueueTicket, Table, TableGroup, CheckIn, Seating, and Cleaning remain separate business concepts. The shared shell has no knowledge of those state machines and cannot trigger a state transition.

## Loading, Empty, Error, and Success States

- Existing conditional rendering and state precedence remain unchanged.
- Tablet grids reposition whole state panels; they do not split or duplicate them.
- Error and success messages remain adjacent to the work region they currently describe.
- Dialog error handling remains within the existing dialogs.
- A failed API request must render the same content and offer the same recovery action at every viewport width.

## Accessibility and Interaction

- Preserve translated navigation labels and the navigation `aria-label`.
- Mark the current navigation entry semantically and visually.
- Preserve keyboard focus-visible treatment.
- Keep primary actions at least 40px high and prefer a 44px touch target where current controls are intended for direct tablet operation.
- Preserve safe-area padding on mobile.
- Ensure sticky regions do not cover focus targets or page content.
- Do not rely on hover for access to any action.
- Keep text wrapping and local scrolling from causing page-level horizontal overflow.

## Testing Strategy

### TDD Static Validation

Add a focused frontend source validation test before implementation. It must require:

- a reusable `StaffPrimaryWorkbench` component;
- use of that component by exactly the four primary pages in scope;
- an explicit adaptive-primary navigation mode;
- tablet rules beginning at 768px;
- landscape work-area rules beginning at 1024px;
- a primary shell maximum width of 1200px;
- continued use of the existing mobile shell and bottom navigation below 768px;
- no adoption by `LoginPage.vue` or named secondary staff pages.

Update an existing assertion only if it conflicts with the accepted scoped tablet design. The base `.staff-workbench-shell` should remain 520px, so existing mobile-shell validation should remain meaningful.

### Automated Verification

- Run the focused responsive UI validation test.
- Run existing staff workbench, reservation, queue, table, i18n, and route validation tests affected by the changed Vue files.
- Run `npm run build` for Vue template, TypeScript, and Vite validation.

### Browser Verification

Validate the four pages at:

- 390x844: existing mobile layout and fixed bottom navigation;
- 768x1024: tablet portrait with left navigation and no horizontal overflow;
- 1024x768: tablet landscape with split work areas;
- 1366x1024: large tablet with capped, centered primary workbench.

If authenticated local browser validation requires the backend, use the PostgreSQL runtime referenced by `target/local-postgres-current.txt` and the repository local runtime restart guide. Do not fall back to a hard-coded database port.

Browser acceptance checks include:

- the top bar remains within the content surface;
- the left rail is visible and usable only for the four primary pages;
- the active navigation item is correct;
- no page-level horizontal scrollbar appears;
- chips may scroll locally where designed;
- dialogs open, fit, and close normally;
- mobile visual structure is unchanged;
- all existing actions remain reachable.

## Expected Files

Implementation is expected to stay within frontend UI, frontend source-validation tests, and documentation:

- new shared primary workbench component;
- `StaffBottomNav.vue`;
- the four primary page Vue files;
- responsive UI validation test files;
- this design, the later implementation plan, and a release note.

No backend production file, migration, API contract, dependency file, or runtime configuration belongs in this change.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Global widening changes secondary pages. | Scope tablet width to `StaffPrimaryWorkbench`; leave base shell and default navigation behavior unchanged. |
| New wrapper changes mobile sticky behavior. | Keep mobile layout rules unchanged and verify at 390x844 before tablet acceptance. |
| Tablet cards become visually dense. | Use the confirmed medium touch-first density and explicit three/four-column limits. |
| Split grids reorder state or actions. | Position existing whole components with CSS classes; do not duplicate conditional blocks or handlers. |
| Navigation rail covers content. | Use one grid-owned rail rather than independent viewport coordinate calculations. |
| Scoped styles cannot position child roots predictably. | Use explicit inherited layout classes on child component roots and verify generated layout through the build and browser checks. |

## Acceptance Criteria

- All four primary pages use one shared primary workbench shell.
- Mobile widths below 768px retain the current H5 presentation and bottom navigation.
- Tablet portrait and landscape use a left rail and the confirmed page layouts.
- The primary workbench is centered and capped at 1200px on large tablets.
- Login and staff secondary pages are unchanged.
- No API, permission, routing, state-machine, database, dependency, or runtime behavior changes.
- The four specified browser sizes have no page-level horizontal overflow.
- Existing actions, loading states, empty states, errors, success feedback, dialogs, and i18n remain functional.
- Focus, active navigation state, and touch targets remain accessible.
- Focused UI tests, affected existing tests, and `npm run build` pass.
