# Tenant Staff Reservation Note Disclosure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let tenant employees expand and read every non-blank reservation note from the Today Reservations card, regardless of whether the reservation came from public booking or a staff channel.

**Architecture:** Keep the existing persisted `reservations.note` and Today Reservations API unchanged. Add source-agnostic, card-local disclosure state to `ReservationTodayListItem.vue`; the list panel and page remain unaware of expansion state. Add only the Chinese/English card labels and a focused source-validation test.

**Tech Stack:** Vue 3 Composition API, TypeScript, Vue I18n, scoped CSS, Java 21, JUnit 5, AssertJ, Maven, Vite.

## Global Constraints

- A non-blank note shows a compact disclosure control; null, empty, and whitespace-only notes show nothing.
- Selecting the control expands the complete trimmed note inside the same card; selecting it again collapses the note.
- Public and staff-created reservation notes follow the same UI path with no source-channel branch.
- The note is read-only and must be rendered through Vue interpolation, never `v-html`.
- Existing Reservation actions, API contracts, database schema, permissions, routes, and responsive shell remain unchanged.
- Phone, tablet portrait, and tablet landscape must remain free of page-level horizontal overflow.

---

## File Structure

- `src/components/reservation-workbench/ReservationTodayListItem.vue`: owns note normalization, local disclosure state, accessible control, expanded region, and responsive card styling.
- `src/i18n/locales/zh-CN.ts`: owns Chinese note control and region labels.
- `src/i18n/locales/en-SG.ts`: owns English note control and region labels.
- `src/test/java/com/rpb/reservation/appgate/ui/ReservationNoteDisclosureUiValidationTest.java`: owns the focused frontend source contract.
- `docs/release-notes/2026-07-16-staff-reservation-note-disclosure.md`: records user-visible behavior, unchanged boundaries, verification, and rollback.

### Task 1: Add the Failing Reservation Note Disclosure Contract

**Files:**
- Create: `src/test/java/com/rpb/reservation/appgate/ui/ReservationNoteDisclosureUiValidationTest.java`
- Read: `src/components/reservation-workbench/ReservationTodayListItem.vue`
- Read: `src/i18n/locales/zh-CN.ts`
- Read: `src/i18n/locales/en-SG.ts`

**Interfaces:**
- Consumes: existing `ReservationTodayViewItem.note?: string | null`.
- Produces: a source-level contract for normalized conditional rendering, local toggle state, accessible disclosure wiring, safe text interpolation, full-width wrapping, and bilingual labels.

- [ ] **Step 1: Create the focused failing test**

```java
package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReservationNoteDisclosureUiValidationTest {

    @Test
    void todayReservationCardDisclosesOnlyNonBlankNotes() throws Exception {
        String item = FrontendSourceSupport.readString(Path.of(
            "src", "components", "reservation-workbench", "ReservationTodayListItem.vue"
        ));

        assertThat(item)
            .contains("const displayNote = computed(() => props.item.note?.trim() ?? '')")
            .contains("const hasNote = computed(() => !!displayNote.value)")
            .contains("const isNoteExpanded = ref(false)")
            .contains("function toggleNote(): void")
            .contains("v-if=\"hasNote\"")
            .contains(":aria-expanded=\"isNoteExpanded\"")
            .contains(":aria-controls=\"noteRegionId\"")
            .contains("v-if=\"hasNote && isNoteExpanded\"")
            .contains("{{ displayNote }}")
            .contains("grid-column: 1 / -1;")
            .contains("white-space: pre-wrap;")
            .contains("overflow-wrap: anywhere;")
            .doesNotContain("v-html");
    }

    @Test
    void reservationNoteDisclosureHasChineseAndEnglishLabels() throws Exception {
        String zh = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "zh-CN.ts"));
        String en = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "en-SG.ts"));

        assertThat(zh)
            .contains("hasNote: '有备注'")
            .contains("hideNote: '收起备注'")
            .contains("noteLabel: '预约备注'");
        assertThat(en)
            .contains("hasNote: 'Has note'")
            .contains("hideNote: 'Hide note'")
            .contains("noteLabel: 'Reservation note'");
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn "-Dtest=ReservationNoteDisclosureUiValidationTest" test
```

Expected: both tests compile, then fail because the card disclosure and translations do not exist yet.

- [ ] **Step 3: Commit the failing contract**

```powershell
git add -- src/test/java/com/rpb/reservation/appgate/ui/ReservationNoteDisclosureUiValidationTest.java
git commit -m "test: define staff reservation note disclosure"
```

### Task 2: Implement the Card-Local Disclosure

**Files:**
- Modify: `src/components/reservation-workbench/ReservationTodayListItem.vue`
- Modify: `src/i18n/locales/zh-CN.ts`
- Modify: `src/i18n/locales/en-SG.ts`
- Test: `src/test/java/com/rpb/reservation/appgate/ui/ReservationNoteDisclosureUiValidationTest.java`

**Interfaces:**
- Consumes: `props.item.note` from the existing `ReservationTodayViewItem` response model.
- Produces: `displayNote`, `hasNote`, `isNoteExpanded`, `noteRegionId`, and `toggleNote()` inside the card only; no emitted event or parent state.

- [ ] **Step 1: Add normalized note state and toggle behavior**

Add after `phoneDisplay`:

```ts
const displayNote = computed(() => props.item.note?.trim() ?? '')
const hasNote = computed(() => !!displayNote.value)
const isNoteExpanded = ref(false)
const noteRegionId = computed(() => `reservation-note-${props.item.reservationId}`)
```

Add after the existing watchers:

```ts
watch(
  () => props.item.note,
  () => {
    isNoteExpanded.value = false
  }
)

function toggleNote(): void {
  if (!hasNote.value) {
    isNoteExpanded.value = false
    return
  }

  isNoteExpanded.value = !isNoteExpanded.value
}
```

- [ ] **Step 2: Add the accessible control and expanded region**

Place this control in `.reservation-today-list-item__actions` after the status:

```vue
<button
  v-if="hasNote"
  class="reservation-today-list-item__action reservation-today-list-item__action--note"
  type="button"
  :aria-expanded="isNoteExpanded"
  :aria-controls="noteRegionId"
  @click="toggleNote"
>
  {{ isNoteExpanded ? $t('reservationWorkbench.item.hideNote') : $t('reservationWorkbench.item.hasNote') }}
</button>
```

Place this region after the actions container:

```vue
<section
  v-if="hasNote && isNoteExpanded"
  :id="noteRegionId"
  class="reservation-today-list-item__note"
  :aria-label="$t('reservationWorkbench.item.noteLabel')"
>
  <strong>{{ $t('reservationWorkbench.item.noteLabel') }}</strong>
  <p>{{ displayNote }}</p>
</section>
```

- [ ] **Step 3: Add note styles**

Add to the scoped style:

```css
.reservation-today-list-item__action--note {
  background: #fff7ed;
  border: 1px solid #fdba74;
  color: #c2410c;
}

.reservation-today-list-item__note {
  background: #fffaf5;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  grid-column: 1 / -1;
  min-width: 0;
  padding: 10px 12px;
}

.reservation-today-list-item__note strong {
  color: #9a3412;
  font-size: 0.76rem;
  font-weight: 950;
}

.reservation-today-list-item__note p {
  color: #334155;
  font-size: 0.82rem;
  font-weight: 700;
  line-height: 1.55;
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
```

- [ ] **Step 4: Add bilingual labels under `reservationWorkbench.item`**

Chinese:

```ts
hasNote: '有备注',
hideNote: '收起备注',
noteLabel: '预约备注',
```

English:

```ts
hasNote: 'Has note',
hideNote: 'Hide note',
noteLabel: 'Reservation note',
```

- [ ] **Step 5: Run the focused test and verify GREEN**

Run:

```powershell
mvn "-Dtest=ReservationNoteDisclosureUiValidationTest" test
```

Expected: 2 tests pass with no failures or errors.

- [ ] **Step 6: Run the frontend production build**

Run:

```powershell
npm run build
```

Expected: `vue-tsc --noEmit` and Vite production build both succeed.

- [ ] **Step 7: Commit the implementation**

```powershell
git add -- src/components/reservation-workbench/ReservationTodayListItem.vue src/i18n/locales/zh-CN.ts src/i18n/locales/en-SG.ts
git commit -m "feat: show reservation notes to staff"
```

### Task 3: Regression, UI Review, and Release Evidence

**Files:**
- Create: `docs/release-notes/2026-07-16-staff-reservation-note-disclosure.md`
- Verify: `src/components/reservation-workbench/ReservationTodayListItem.vue`
- Verify: `src/test/java/com/rpb/reservation/reservation/integration/ReservationTodayViewApiIntegrationTest.java`

**Interfaces:**
- Consumes: completed note disclosure and existing API note projection.
- Produces: regression evidence, responsive/UI review evidence, release/rollback documentation, and a clean reviewable branch.

- [ ] **Step 1: Run focused and affected regression tests**

```powershell
mvn "-Dtest=ReservationNoteDisclosureUiValidationTest,ReservationTodayViewControllerTest,ReservationTodayViewApiIntegrationTest,ReservationShareInfoUiValidationTest,StaffPrimaryWorkbenchTabletUiValidationTest" test
```

Expected: every selected test passes. The existing integration assertion continues to prove `items[].note` reaches the authorized Today Reservations response.

- [ ] **Step 2: Run static safety checks**

```powershell
rg -n "v-html|sourceChannel.*note|note.*sourceChannel" src/components/reservation-workbench/ReservationTodayListItem.vue
git diff --check
```

Expected: no unsafe HTML or source-channel note branching is found, and `git diff --check` reports no whitespace errors.

- [ ] **Step 3: Verify the responsive interaction in a browser**

Use a reservation fixture with a multi-line note and another with a null note. Check at 390x844, 768x1024, and 1024x768:

- only the noted reservation shows `有备注` / `Has note`;
- the button toggles the full text and `aria-expanded` between `false` and `true`;
- the note spans the card width, preserves line breaks, and does not create horizontal overflow;
- share, assignment, check-in, seating, no-show, and cancel controls retain their existing order and behavior.

If authenticated local validation starts the backend, first read `target/local-postgres-current.txt` and use that runtime's PostgreSQL port exactly.

- [ ] **Step 4: Apply the RPB TDD and UI review checklists**

Record that the UI test was observed failing before production changes and passing after them. Confirm the API/schema/permission path is unchanged, empty/loading/error states are unaffected, bilingual text fits, and no critical action is hidden or ambiguous.

- [ ] **Step 5: Write the release note**

Create `docs/release-notes/2026-07-16-staff-reservation-note-disclosure.md` with:

```markdown
# Staff Reservation Note Disclosure

## User-visible change

Tenant employees can now expand `有备注` / `Has note` on a Today Reservations card to read the complete reservation note. The indicator appears for any non-blank note regardless of whether the reservation came from public booking or a staff channel.

## Unchanged boundaries

- No API response, database schema, migration, permission, route, or workflow change.
- Reservations without notes keep the existing compact card.
- Existing reservation actions and responsive workbench behavior are unchanged.

## Verification

- Focused note disclosure test passed after first failing against the missing UI.
- Existing Today Reservations API note projection tests passed.
- Frontend production build passed.
- Phone and tablet portrait/landscape checks confirmed wrapping and no horizontal overflow.

## Rollback

Revert the card disclosure, locale strings, and focused UI test. No data or database rollback is required.
```

- [ ] **Step 6: Commit documentation and final verification evidence**

```powershell
git add -- docs/release-notes/2026-07-16-staff-reservation-note-disclosure.md
git commit -m "docs: record staff reservation note disclosure"
```

- [ ] **Step 7: Run final verification and push**

```powershell
mvn "-Dtest=ReservationNoteDisclosureUiValidationTest,ReservationTodayViewControllerTest,ReservationTodayViewApiIntegrationTest,ReservationShareInfoUiValidationTest,StaffPrimaryWorkbenchTabletUiValidationTest" test
npm run build
git status --short
git push origin master
```

Expected: selected tests and build pass, the worktree is clean, and `master` pushes successfully.
