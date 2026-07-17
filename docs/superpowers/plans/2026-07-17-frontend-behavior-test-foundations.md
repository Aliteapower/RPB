# Frontend Behaviour-Test and Temporal Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Vue runtime-test safety net and centralize the existing Singapore date/time defaults used by the Table and Queue pages without changing routes, API contracts, rendered workflow order, or user-visible formatting.

**Architecture:** Vitest runs Vue Test Utils components in jsdom alongside the existing Node login-routing tests. A leaf `StoreTemporalContext` utility owns the current locale/timezone defaults and pure formatting functions. The current large Table, Queue, and Create Reservation surfaces receive characterization tests before their later decomposition; only duplicated temporal formatting is moved in this plan.

**Tech Stack:** Vue 3.5, TypeScript 5.7, Vite 6, Vitest 3, Vue Test Utils 2, jsdom 26, Pinia 2, Vue Router 4, Vue I18n 11.

## Global Constraints

- Do not change any API path, request shape, response shape, HTTP status, or stable error code.
- Do not add or modify database schema, migrations, indexes, constraints, or stored data.
- Do not change Reservation, QueueTicket, WalkIn, Seating, Cleaning, DiningTable, or TableGroup state-machine transitions.
- Preserve all existing frontend routes, route names, query parameters, visible workflow order, stable selectors, i18n keys, and Singapore-formatted output.
- Keep `npm run test:login-routing` unchanged and independently runnable.
- Keep the existing Java source-validation tests in place. Do not weaken or delete them in this foundation plan.
- Do not decompose the three large Vue surfaces yet. Their later plans consume the behaviour tests introduced here.
- Do not introduce a dynamic Store-timezone API. `Asia/Singapore` and `zh-CN` remain the defaults because the current Table and Queue response contracts do not expose timezone context.
- This plan is frontend-only. It must not start Spring Boot, apply migrations, or access PostgreSQL.

---

## File Structure

- Modify `package.json` and `package-lock.json` — add unit-test scripts and pinned compatible test dependencies.
- Create `vitest.config.ts` — isolated jsdom configuration for `src/**/*.spec.ts`.
- Create `src/test/setup.ts` — deterministic DOM and mock cleanup.
- Create `src/utils/storeTemporalContext.ts` — pure Store-local date/time boundary with current defaults.
- Create `src/utils/storeTemporalContext.spec.ts` — fixed-instant boundary and invalid-input tests.
- Modify `src/pages/TableResourceListPage.vue` — delegate its existing time and date-input formatting to the temporal boundary.
- Modify `src/pages/QueueTicketListPage.vue` — delegate its existing month/day/time formatting to the temporal boundary.
- Create `src/pages/__tests__/TableResourceListPage.behavior.spec.ts` — loading, success, empty, and API-error characterization.
- Create `src/pages/__tests__/QueueTicketListPage.behavior.spec.ts` — loading, success, empty, API-error, and phone-filter characterization.
- Create `src/components/reservation-workbench/__tests__/CreateReservationDialog.behavior.spec.ts` — open/load/close characterization.

### Task 1: Install and Prove the Vue Runtime-Test Harness

**Files:**
- Modify: `package.json`
- Modify: `package-lock.json`
- Create: `vitest.config.ts`
- Create: `src/test/setup.ts`
- Create: `src/test/testHarness.spec.ts`

**Interfaces:**
- Adds `npm run test:unit` and `npm run test:unit:watch` only.
- Does not rename or replace `build`, `dev`, or `test:login-routing`.

- [ ] **Step 1: Record the missing-script failure**

Run:

```powershell
npm run test:unit
```

Expected: npm exits non-zero because `test:unit` does not exist. Record this as the red test-harness baseline; do not treat dependency installation as evidence that component behaviour passes.

- [ ] **Step 2: Add the scripts and compatible development dependencies**

Run:

```powershell
npm install --save-dev vitest@3.2.4 @vue/test-utils@2.4.6 jsdom@26.1.0
```

Then make the `package.json` scripts exactly:

```json
{
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "test:login-routing": "node --experimental-strip-types --test frontend-tests/loginStoreRouting.test.mjs",
    "test:unit": "vitest run",
    "test:unit:watch": "vitest",
    "preview": "vite preview"
  }
}
```

The install command must update both `package.json` and `package-lock.json`. Do not hand-edit lockfile integrity entries.

- [ ] **Step 3: Add the Vitest configuration and cleanup hook**

Create `vitest.config.ts`:

```ts
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  test: {
    clearMocks: true,
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
    restoreMocks: true,
    setupFiles: ['./src/test/setup.ts']
  }
})
```

Create `src/test/setup.ts`:

```ts
import { afterEach, vi } from 'vitest'

afterEach(() => {
  document.body.innerHTML = ''
  vi.clearAllMocks()
})
```

- [ ] **Step 4: Add a real Vue smoke test**

Create `src/test/testHarness.spec.ts`:

```ts
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'

describe('Vue behaviour-test harness', () => {
  it('mounts and reacts to a DOM action in jsdom', async () => {
    const component = defineComponent({
      data: () => ({ count: 0 }),
      template: '<button type="button" @click="count += 1">{{ count }}</button>'
    })
    const wrapper = mount(component)

    await wrapper.get('button').trigger('click')

    expect(wrapper.get('button').text()).toBe('1')
    wrapper.unmount()
  })
})
```

- [ ] **Step 5: Run the harness test and build**

Run:

```powershell
npm run test:unit -- src/test/testHarness.spec.ts
npm run build
```

Expected: one Vitest test passes; TypeScript checking and the production Vite build pass.

- [ ] **Step 6: Commit the isolated harness**

```powershell
git add package.json package-lock.json vitest.config.ts src/test/setup.ts src/test/testHarness.spec.ts
git commit -m "test: add Vue behaviour test harness"
```

### Task 2: Introduce the Store Temporal Context with Fixed-Instant Tests

**Files:**
- Create: `src/utils/storeTemporalContext.ts`
- Test: `src/utils/storeTemporalContext.spec.ts`

**Interfaces:**
- Produces: immutable `DEFAULT_STORE_TEMPORAL_CONTEXT` with `timeZone: 'Asia/Singapore'` and `locale: 'zh-CN'`.
- Produces: `storeDateInput`, `formatStoreTime`, and `formatStoreMonthDayTime` pure functions.
- Null timestamps format as `''`; malformed non-empty timestamps round-trip unchanged so page-level fallback behaviour remains possible.

- [ ] **Step 1: Write the failing fixed-instant tests**

Create `src/utils/storeTemporalContext.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'

import {
  DEFAULT_STORE_TEMPORAL_CONTEXT,
  formatStoreMonthDayTime,
  formatStoreTime,
  storeDateInput
} from './storeTemporalContext'

describe('StoreTemporalContext', () => {
  const midnightBoundary = new Date('2026-07-16T16:30:00.000Z')

  it('derives the Singapore business date across the UTC midnight boundary', () => {
    expect(DEFAULT_STORE_TEMPORAL_CONTEXT).toEqual({
      timeZone: 'Asia/Singapore',
      locale: 'zh-CN'
    })
    expect(storeDateInput(midnightBoundary)).toBe('2026-07-17')
  })

  it('formats the same instant with the current Table and Queue output shapes', () => {
    const value = midnightBoundary.toISOString()

    expect(formatStoreTime(value)).toBe('00:30')
    expect(formatStoreMonthDayTime(value)).toBe('07-17 00:30')
  })

  it('keeps null and malformed input behaviour explicit', () => {
    expect(formatStoreTime(null)).toBe('')
    expect(formatStoreMonthDayTime(undefined)).toBe('')
    expect(formatStoreTime('not-a-date')).toBe('not-a-date')
    expect(formatStoreMonthDayTime('not-a-date')).toBe('not-a-date')
  })
})
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
npm run test:unit -- src/utils/storeTemporalContext.spec.ts
```

Expected: the suite fails because `storeTemporalContext.ts` does not exist.

- [ ] **Step 3: Implement the temporal boundary**

Create `src/utils/storeTemporalContext.ts`:

```ts
export interface StoreTemporalContext {
  readonly timeZone: string
  readonly locale: string
}

export const DEFAULT_STORE_TEMPORAL_CONTEXT: StoreTemporalContext = Object.freeze({
  timeZone: 'Asia/Singapore',
  locale: 'zh-CN'
})

export function storeDateInput(
  value: Date = new Date(),
  context: StoreTemporalContext = DEFAULT_STORE_TEMPORAL_CONTEXT
): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: context.timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(value)

  return `${part(parts, 'year')}-${part(parts, 'month')}-${part(parts, 'day')}`
}

export function formatStoreTime(
  value: string | null | undefined,
  context: StoreTemporalContext = DEFAULT_STORE_TEMPORAL_CONTEXT
): string {
  return formatTimestamp(value, context, false)
}

export function formatStoreMonthDayTime(
  value: string | null | undefined,
  context: StoreTemporalContext = DEFAULT_STORE_TEMPORAL_CONTEXT
): string {
  return formatTimestamp(value, context, true)
}

function formatTimestamp(
  value: string | null | undefined,
  context: StoreTemporalContext,
  includeMonthDay: boolean
): string {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  const parts = new Intl.DateTimeFormat(context.locale, {
    timeZone: context.timeZone,
    ...(includeMonthDay ? { month: '2-digit' as const, day: '2-digit' as const } : {}),
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(date)
  const time = `${part(parts, 'hour')}:${part(parts, 'minute')}`

  return includeMonthDay
    ? `${part(parts, 'month')}-${part(parts, 'day')} ${time}`
    : time
}

function part(parts: Intl.DateTimeFormatPart[], type: Intl.DateTimeFormatPartTypes): string {
  return parts.find(item => item.type === type)?.value ?? ''
}
```

- [ ] **Step 4: Run the focused temporal tests**

Run:

```powershell
npm run test:unit -- src/utils/storeTemporalContext.spec.ts
```

Expected: 3/3 tests pass, including the UTC-to-Singapore next-day boundary.

- [ ] **Step 5: Commit the leaf capability**

```powershell
git add src/utils/storeTemporalContext.ts src/utils/storeTemporalContext.spec.ts
git commit -m "feat: add Store temporal formatting boundary"
```

### Task 3: Characterize and Rewire Table Temporal Behaviour

**Files:**
- Modify: `src/pages/TableResourceListPage.vue`
- Test: `src/pages/__tests__/TableResourceListPage.behavior.spec.ts`

**Interfaces:**
- Mocks only `fetchTableResources` and `getReservationCalendarSummary` at their module boundaries.
- Keeps the route `/stores/:storeId/tables`, route name `table-resource-list`, DOM classes, API queries, and generated-text keys unchanged.
- Keeps the literal identifier `todayDateInput` in `TableResourceListPage.vue` through an import alias so `StaffUiV12TableSelectionValidationTest` remains valid.

- [ ] **Step 1: Add failing loading, success, empty, and API-error tests against the current page**

Create `src/pages/__tests__/TableResourceListPage.behavior.spec.ts`:

```ts
import { createPinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  fetchTableResources: vi.fn(),
  getReservationCalendarSummary: vi.fn()
}))

vi.mock('../../api/tableResourceApi', async importOriginal => ({
  ...(await importOriginal<typeof import('../../api/tableResourceApi')>()),
  fetchTableResources: api.fetchTableResources
}))
vi.mock('../../api/reservationCalendarSummaryApi', async importOriginal => ({
  ...(await importOriginal<typeof import('../../api/reservationCalendarSummaryApi')>()),
  getReservationCalendarSummary: api.getReservationCalendarSummary
}))

import { TableResourceApiError } from '../../api/tableResourceApi'
import TableResourceListPage from '../TableResourceListPage.vue'

const EmptyShell = defineComponent({ template: '<div />' })
let wrapper: VueWrapper | undefined

async function mountPage(): Promise<VueWrapper> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/stores/:storeId/tables', name: 'table-resource-list', component: EmptyShell },
      { path: '/:pathMatch(.*)*', name: 'test-fallback', component: EmptyShell }
    ]
  })
  await router.push('/stores/store-1/tables')
  await router.isReady()

  wrapper = mount(TableResourceListPage, {
    global: {
      plugins: [createPinia(), router],
      stubs: {
        StaffPrimaryWorkbench: { template: '<main><slot /></main>' },
        StaffHomeTopBar: { template: '<header><slot name="action" /></header>' },
        StaffBusinessDateSwitcher: true,
        ReservationTableSwitchDialog: true
      }
    }
  })
  return wrapper
}

describe('TableResourceListPage behaviour', () => {
  beforeEach(() => {
    api.getReservationCalendarSummary.mockResolvedValue({
      success: true,
      storeId: 'store-1',
      month: '2026-07',
      storeTimezone: 'Asia/Singapore',
      days: []
    })
  })

  afterEach(() => wrapper?.unmount())

  it('shows loading and then the configured-resource success state', async () => {
    let resolveResources!: (value: unknown) => void
    api.fetchTableResources.mockImplementationOnce(
      () => new Promise(resolve => { resolveResources = resolve })
    )

    const page = await mountPage()
    await nextTick()
    expect(page.find('.state-panel').exists()).toBe(true)

    resolveResources({
      success: true,
      resources: [{
        resourceType: 'dining_table',
        resourceId: 'table-1',
        code: 'T01',
        displayName: 'Table 01',
        areaName: 'Main',
        capacityMin: 1,
        capacityMax: 4,
        status: 'available',
        selectable: true,
        memberTableCodes: []
      }]
    })
    await flushPromises()

    expect(page.find('.table-page__area-list').exists()).toBe(true)
    expect(page.text()).toContain('T01')
  })

  it('shows the stable empty state after an empty response', async () => {
    api.fetchTableResources.mockResolvedValueOnce({ success: true, resources: [] })

    const page = await mountPage()
    await flushPromises()

    expect(page.find('.error-panel').exists()).toBe(false)
    expect(page.find('.state-panel').exists()).toBe(true)
  })

  it('shows the API error state without rendering resources', async () => {
    api.fetchTableResources.mockRejectedValueOnce(new TableResourceApiError(500, {
      success: false,
      error: {
        code: 'PERSISTENCE_ERROR',
        messageKey: 'table.resources.request_failed',
        details: {}
      }
    }))

    const page = await mountPage()
    await flushPromises()

    expect(page.find('.error-panel').exists()).toBe(true)
    expect(page.find('.table-page__area-list').exists()).toBe(false)
  })
})
```

- [ ] **Step 2: Run the Table behaviour tests before rewiring**

Run:

```powershell
npm run test:unit -- src/pages/__tests__/TableResourceListPage.behavior.spec.ts
```

Expected: 3/3 characterization tests pass against the current page. If mounting exposes a missing browser primitive, add only the smallest faithful jsdom polyfill to `src/test/setup.ts`, then rerun; do not stub page logic or relax assertions.

- [ ] **Step 3: Replace only the two local temporal helpers**

Add this import to `TableResourceListPage.vue`:

```ts
import {
  formatStoreTime,
  storeDateInput as todayDateInput
} from '../utils/storeTemporalContext'
```

Delete the local `formatStoreTime` and `todayDateInput` function bodies currently near the end of `<script setup>`. Do not rename any call sites. Do not move filters, watchers, API calls, commands, template, or styles.

- [ ] **Step 4: Prove temporal equivalence and source-test compatibility**

Run:

```powershell
npm run test:unit -- src/utils/storeTemporalContext.spec.ts src/pages/__tests__/TableResourceListPage.behavior.spec.ts
mvn -q "-Dtest=StaffUiV12TableSelectionValidationTest" test
npm run build
```

Expected: 6/6 Vitest tests pass; the Java source-validation class passes because `todayDateInput` remains present as an import alias; the production build passes.

- [ ] **Step 5: Commit the Table characterization and rewire**

```powershell
git add src/pages/TableResourceListPage.vue src/pages/__tests__/TableResourceListPage.behavior.spec.ts
git commit -m "test: characterize Table resource page behaviour"
```

### Task 4: Characterize and Rewire Queue Temporal and Filter Behaviour

**Files:**
- Modify: `src/pages/QueueTicketListPage.vue`
- Test: `src/pages/__tests__/QueueTicketListPage.behavior.spec.ts`

**Interfaces:**
- Mocks the Queue list, Table resource, and Me Apps read boundaries; action APIs remain real but are not invoked.
- Keeps `/stores/:storeId/queue-tickets`, `queue-ticket-list`, request pagination, phone normalization, empty/error selectors, and page-level missing-timestamp fallback unchanged.

- [ ] **Step 1: Add failing loading, success, empty, API-error, and phone-filter tests**

Create `src/pages/__tests__/QueueTicketListPage.behavior.spec.ts` using the same router/Pinia mount pattern as Task 3, with these exact mocks and expectations:

```ts
const api = vi.hoisted(() => ({
  fetchMeApps: vi.fn(),
  fetchTableResources: vi.fn(),
  listQueueTickets: vi.fn()
}))

vi.mock('../../api/meAppsApi', async importOriginal => ({
  ...(await importOriginal<typeof import('../../api/meAppsApi')>()),
  fetchMeApps: api.fetchMeApps
}))
vi.mock('../../api/tableResourceApi', async importOriginal => ({
  ...(await importOriginal<typeof import('../../api/tableResourceApi')>()),
  fetchTableResources: api.fetchTableResources
}))
vi.mock('../../api/queueTicketListApi', async importOriginal => ({
  ...(await importOriginal<typeof import('../../api/queueTicketListApi')>()),
  listQueueTickets: api.listQueueTickets
}))
```

Use route `{ path: '/stores/:storeId/queue-tickets', name: 'queue-ticket-list', component: EmptyShell }`, mount `QueueTicketListPage`, install `createPinia()` and the memory router, and stub:

```ts
{
  StaffPrimaryWorkbench: { template: '<main><slot /></main>' },
  StaffHomeTopBar: true
}
```

In `beforeEach`, use:

```ts
api.fetchMeApps.mockResolvedValue({ success: true, apps: [] })
api.fetchTableResources.mockResolvedValue({ success: true, resources: [] })
```

The five tests must assert:

```ts
// initial request contract
api.listQueueTickets.mockResolvedValueOnce({
  success: true,
  items: [],
  page: { limit: 100, offset: 0, total: 0 }
})
await mountPage()
await flushPromises()
expect(api.listQueueTickets).toHaveBeenCalledWith('store-1', {
  limit: 100,
  offset: 0
})

// loading -> success
let resolveQueue!: (value: unknown) => void
api.listQueueTickets.mockImplementationOnce(
  () => new Promise(resolve => { resolveQueue = resolve })
)
const loadingPage = await mountPage()
await nextTick()
expect(loadingPage.find('.state-panel').exists()).toBe(true)
resolveQueue({
  success: true,
  items: [{
    queueTicketId: 'queue-1',
    queueTicketNumber: 7,
    queueTicketDisplayNumber: 'Q007',
    queueTicketStatus: 'waiting',
    partySize: 2,
    partySizeGroup: 'small',
    customerName: 'Guest',
    createdAt: '2026-07-16T16:30:00.000Z'
  }],
  page: { limit: 100, offset: 0, total: 1 }
})
await flushPromises()
expect(loadingPage.find('.queue-list').exists()).toBe(true)
expect(loadingPage.text()).toContain('Q007')

// empty
api.listQueueTickets.mockResolvedValueOnce({
  success: true,
  items: [],
  page: { limit: 100, offset: 0, total: 0 }
})
const emptyPage = await mountPage()
await flushPromises()
expect(emptyPage.find('.empty-queue-panel').exists()).toBe(true)

// API error
api.listQueueTickets.mockRejectedValueOnce(new QueueTicketListApiError(403, {
  success: false,
  error: {
    code: 'APP_GATE_DISABLED',
    messageKey: 'app.gate.disabled',
    details: {}
  }
}))
const errorPage = await mountPage()
await flushPromises()
expect(errorPage.find('.error-panel').exists()).toBe(true)

// phone normalization; start this test with an empty successful response
const filterPage = await mountPage()
await flushPromises()
await filterPage.get('input[name="queuePhoneFilter"]').setValue('9123-4567')
await flushPromises()
expect(api.listQueueTickets).toHaveBeenLastCalledWith('store-1', {
  limit: 100,
  offset: 0,
  phone: '91234567'
})
```

Keep each of these five scenarios in its own `it` block. In the loading, empty, API-error, and phone-filter cases, additionally assert that at least one call contains `{ limit: 100, offset: 0 }`. Reset mock implementations in `beforeEach` with `vi.resetAllMocks()` before applying defaults, and unmount the wrapper in `afterEach`. Import `QueueTicketListApiError` from the partially mocked real module.

- [ ] **Step 2: Run the Queue characterization tests before rewiring**

Run:

```powershell
npm run test:unit -- src/pages/__tests__/QueueTicketListPage.behavior.spec.ts
```

Expected: 5/5 tests pass against current runtime behaviour.

- [ ] **Step 3: Delegate only Queue timestamp formatting**

Add:

```ts
import { formatStoreMonthDayTime } from '../utils/storeTemporalContext'
```

Delete `const storeTimezone = 'Asia/Singapore'`. Replace the body of the existing page-level wrapper with:

```ts
function formatStoreDateTime(value: string | null | undefined): string {
  if (!value) {
    return gt('generated.queue-ticket-list.098')
  }

  return formatStoreMonthDayTime(value)
}
```

Keeping `formatStoreDateTime` preserves the page-specific generated-text fallback; the shared utility stays independent of i18n.

- [ ] **Step 4: Prove Queue behaviour and formatting equivalence**

Run:

```powershell
npm run test:unit -- src/utils/storeTemporalContext.spec.ts src/pages/__tests__/QueueTicketListPage.behavior.spec.ts
mvn -q "-Dtest=StaffReceptionClosedLoopUiValidationTest,StaffUiV12TableSelectionValidationTest" test
npm run build
```

Expected: 8/8 Vitest tests pass, both Java UI validation classes pass, and the production build passes.

- [ ] **Step 5: Commit the Queue characterization and rewire**

```powershell
git add src/pages/QueueTicketListPage.vue src/pages/__tests__/QueueTicketListPage.behavior.spec.ts
git commit -m "test: characterize Queue workbench behaviour"
```

### Task 5: Characterize Create Reservation Dialog Open, Load, and Close

**Files:**
- Test: `src/components/reservation-workbench/__tests__/CreateReservationDialog.behavior.spec.ts`

**Interfaces:**
- Production dialog code remains unchanged.
- Mocks only the time-slot and Table resource reads triggered by opening the dialog.
- Uses the real global `i18n` plugin and real Teleport-to-body behaviour.

- [ ] **Step 1: Write the dialog behaviour test**

Create `src/components/reservation-workbench/__tests__/CreateReservationDialog.behavior.spec.ts`:

```ts
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  fetchReservationTimeSlots: vi.fn(),
  fetchTableResources: vi.fn()
}))

vi.mock('../../../api/reservationMealPeriodApi', async importOriginal => ({
  ...(await importOriginal<typeof import('../../../api/reservationMealPeriodApi')>()),
  fetchReservationTimeSlots: api.fetchReservationTimeSlots
}))
vi.mock('../../../api/tableResourceApi', async importOriginal => ({
  ...(await importOriginal<typeof import('../../../api/tableResourceApi')>()),
  fetchTableResources: api.fetchTableResources
}))

import CreateReservationDialog from '../CreateReservationDialog.vue'
import { i18n } from '../../../i18n'

let wrapper: VueWrapper | undefined

describe('CreateReservationDialog behaviour', () => {
  beforeEach(() => {
    api.fetchReservationTimeSlots.mockResolvedValue({
      success: true,
      storeId: 'store-1',
      businessDate: '2027-01-15',
      timezone: 'Asia/Singapore',
      slots: []
    })
    api.fetchTableResources.mockResolvedValue({ success: true, resources: [] })
  })

  afterEach(() => wrapper?.unmount())

  it('opens, loads options for the selected date, and emits close from cancel', async () => {
    wrapper = mount(CreateReservationDialog, {
      attachTo: document.body,
      props: {
        open: true,
        storeId: 'store-1',
        selectedDate: '2027-01-15',
        minDate: '2026-07-17'
      },
      global: {
        plugins: [i18n],
        stubs: {
          StaffGuestContactLookup: true,
          TableResourcePicker: true,
          ReservationShareCopyPanel: true
        }
      }
    })
    await flushPromises()

    expect(api.fetchReservationTimeSlots).toHaveBeenCalledWith('store-1', '2027-01-15')
    expect(api.fetchTableResources).toHaveBeenCalledWith('store-1', {
      includeGroups: true,
      businessDate: '2027-01-15'
    })
    expect(document.body.querySelector('[role="dialog"]')).not.toBeNull()

    const cancel = document.body.querySelector<HTMLButtonElement>('.reservation-create-dialog__cancel')
    expect(cancel).not.toBeNull()
    cancel?.click()
    await flushPromises()

    expect(wrapper.emitted('update:open')).toContainEqual([false])
  })
})
```

- [ ] **Step 2: Run the focused dialog test**

Run:

```powershell
npm run test:unit -- src/components/reservation-workbench/__tests__/CreateReservationDialog.behavior.spec.ts
```

Expected: 1/1 test passes. If Teleport cleanup emits a warning, fix wrapper unmount/DOM cleanup rather than replacing Teleport with a stub.

- [ ] **Step 3: Run the existing Create Reservation source validation**

Run:

```powershell
mvn -q "-Dtest=ReservationCreateDialogUiValidationTest" test
```

Expected: the existing Java validation class passes; no production dialog change occurred.

- [ ] **Step 4: Commit the dialog safety net**

```powershell
git add src/components/reservation-workbench/__tests__/CreateReservationDialog.behavior.spec.ts
git commit -m "test: characterize Create Reservation dialog behaviour"
```

### Task 6: Phase-One Frontend Regression Gate

**Files:**
- Verify only; do not make opportunistic production changes.

- [ ] **Step 1: Run the complete new Vitest suite**

Run:

```powershell
npm run test:unit
```

Expected: 13/13 tests pass: harness 1, temporal 3, Table 3, Queue 5, Create Reservation 1.

- [ ] **Step 2: Preserve the existing login-routing contract**

Run:

```powershell
npm run test:login-routing
```

Expected: 7/7 tests pass.

- [ ] **Step 3: Run the focused Java UI validation groups**

Run:

```powershell
mvn -q "-Dtest=StaffUiV12TableSelectionValidationTest,StaffReceptionClosedLoopUiValidationTest,ReservationCreateDialogUiValidationTest" test
```

Expected: all tests in the three classes pass. Do not replace these source validations in this plan.

- [ ] **Step 4: Run production compilation and diff checks**

Run:

```powershell
npm run build
git diff --check
git status --short
```

Expected: production build passes; no whitespace errors; only the intended frontend harness, temporal utility, two page rewires, and three behaviour suites are changed relative to the plan's starting point.

- [ ] **Step 5: Inspect the final diff for scope and compatibility**

Run:

```powershell
git diff --stat HEAD~5..HEAD
git diff HEAD~5..HEAD -- package.json vitest.config.ts src/test src/utils/storeTemporalContext.ts src/pages/TableResourceListPage.vue src/pages/QueueTicketListPage.vue src/pages/__tests__ src/components/reservation-workbench/__tests__
```

Confirm all of the following before starting any later frontend decomposition:

- `test:login-routing` is unchanged;
- no API or type contract changed;
- no route, route name, selector, generated-text key, template workflow, or style changed;
- `todayDateInput` remains visible to the existing Table source-validation test;
- Queue null timestamps still use `generated.queue-ticket-list.098`;
- temporal tests cover the Singapore day boundary with a fixed instant;
- large Vue files have not yet been decomposed.

## Completion Boundary

This plan is complete only when the runtime harness, temporal utility, three characterization suites, focused Java UI validations, login-routing suite, and production build all pass. The next frontend plans may extract Table, Queue, and Create Reservation workflow modules one surface at a time; they must treat these runtime tests as required regression gates and expand them before moving additional behaviour.
