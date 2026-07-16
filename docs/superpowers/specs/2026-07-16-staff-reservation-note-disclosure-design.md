# Tenant Staff Reservation Note Disclosure Design

**Date:** 2026-07-16
**Status:** Accepted for implementation planning
**Scope:** Tenant employee Reservation workbench UI

## Goal

Make a reservation's existing note visible to tenant employees from the Today Reservations card. The behavior applies to every reservation with a note, including public bookings and reservations created through staff or other authorized channels.

## Confirmed Product Decisions

- A card with a non-blank note shows a compact `有备注` / `Has note` control.
- Selecting the control expands the complete note inside the same reservation card.
- Selecting it again collapses the note.
- A card with a null, empty, or whitespace-only note shows no note control or empty note region.
- The note is read-only in this change.
- Existing phone, tablet portrait, and tablet landscape layouts remain supported.

## Existing Data Path

The required data already crosses every application boundary:

1. Public booking and reservation creation normalize and persist customer/staff input in `reservations.note`.
2. `ReservationJpaRepository.findTodayView` selects `r.note`.
3. `ReservationPersistenceAdapter` maps it into `ReservationTodayViewRow`.
4. `ReservationTodayViewApplicationService` maps it into `ReservationTodayViewItem`.
5. The Today Reservations API serializes the field as `items[].note`.
6. The frontend `ReservationTodayViewItem` type already declares `note?: string | null`.

The missing boundary is presentation only: `ReservationTodayListItem.vue` does not render `item.note`.

## Approaches Considered

### A. Expand Within the Card — Selected

Show a compact note indicator only when a note exists. Expand the note in a full-width region within the same card.

Benefits:

- keeps the normal reservation list compact;
- keeps the note visibly associated with the correct guest and reservation;
- supports long and multi-line content without a modal overlay;
- works consistently on phone and tablet layouts.

### B. Always Show Note Text

Render every non-empty note directly in the card body. This is the simplest interaction but makes the list harder to scan and allows long notes to dominate the workbench.

### C. Open a Modal

Show a note indicator that opens a dialog. This preserves list density but adds another overlay and more focus-management complexity for read-only content.

## Component and OOD Boundaries

`ReservationTodayListItem.vue` remains the sole owner of one reservation card's display and local disclosure state. It will:

- derive a trimmed display note from `props.item.note`;
- derive whether the note control should be rendered;
- own one local expanded/collapsed boolean;
- render the disclosure control and note region;
- expose no new event because expanding a note is presentation-only and does not affect page state.

`ReservationTodayListPanel.vue` continues to own filtering and card collection behavior. `ReservationTodayViewPage.vue` continues to own API loading and reservation actions. Neither receives note-specific state or events.

No new shared component is required for this single, bounded disclosure. No source-channel branch is introduced: all notes use the same UI contract.

## Interaction and Layout Contract

- The disclosure control is rendered in the existing card action region so it remains close to the status and other reservation actions.
- The collapsed label is `有备注` in Chinese and `Has note` in English.
- The expanded label is `收起备注` in Chinese and `Hide note` in English.
- The expanded note region spans the full card width below the main information and action columns.
- The region has a clear `预约备注` / `Reservation note` label and visually distinct, low-emphasis background.
- Note content is trimmed once for display, uses normal text rendering, preserves embedded line breaks, and wraps long uninterrupted text without horizontal overflow.
- Expansion is independent per card; expanding one reservation does not collapse another.
- List refreshes or item replacement may reset disclosure state because it is ephemeral UI state and is not persisted.
- Existing card actions, status, share controls, and layout order remain unchanged.

## Accessibility

- The control is a native button.
- `aria-expanded` reflects the disclosure state.
- `aria-controls` points to a stable note-region id derived from the reservation id.
- The expanded region has a translated accessible label.
- Existing focus-visible styling applies to the control.
- The note is available in the DOM only while expanded, avoiding duplicate hidden reading content.

## Localization

Add Chinese and English messages under `reservationWorkbench.item` for:

- note present;
- hide note;
- reservation note label.

No generated catalog key or backend translation is required because this component already uses the Vue i18n locale files for its card copy.

## API, Database, and Security Impact

- No API request or response shape changes.
- No database schema or migration changes.
- No authorization changes: the employee already needs access to the store-scoped Today Reservations endpoint, which already returns the note.
- No additional customer data is exposed beyond the authorized response already delivered to the page.
- Note text is rendered with Vue interpolation, not `v-html`, so user-provided markup remains escaped.

## Test Strategy

Use TDD against the reservation workbench UI contract:

1. Add a focused source-validation test that initially fails because the card has no note disclosure.
2. Require normalized note handling, conditional rendering, a native toggle button, `aria-expanded`, `aria-controls`, and a full-width note region.
3. Require Chinese and English note strings.
4. Implement the minimum card and locale changes to pass.
5. Run the focused UI validation tests, the existing Reservation Today API/integration contract tests, and the frontend production build.

Browser verification covers:

- a reservation with a short note;
- a reservation with a multi-line/long note;
- a reservation with no note;
- expand and collapse behavior;
- no horizontal overflow at phone, tablet portrait, and tablet landscape widths;
- unchanged reservation status and action controls.

If local authenticated browser validation starts the backend, it must use the PostgreSQL runtime in `target/local-postgres-current.txt`.

## Expected Files

- Modify `src/components/reservation-workbench/ReservationTodayListItem.vue`.
- Modify `src/i18n/locales/zh-CN.ts`.
- Modify `src/i18n/locales/en-SG.ts`.
- Add or modify one focused UI validation test under `src/test/java/com/rpb/reservation/appgate/ui`.
- Add a release note after implementation.

Backend production code, API contracts, migrations, dependencies, and runtime configuration are outside scope.

## Acceptance Criteria

- Every Today Reservations card with a non-blank note shows the note disclosure control, regardless of reservation source channel.
- The control expands the complete trimmed note within that card and collapses it on the next selection.
- Null, empty, and whitespace-only notes produce no control or empty content.
- Long and multi-line notes remain readable without page-level horizontal overflow.
- Existing reservation data, filters, status transitions, dialogs, sharing, assignment, check-in, seating, no-show, and cancellation behavior are unchanged.
- Chinese and English labels are available.
- Focused tests, affected reservation tests, and `npm run build` pass.

## Rollback

Remove the note disclosure markup, local state/computed values, card styles, i18n messages, and focused UI assertions. No data, API, permission, or database rollback is required.
