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
      { path: '/stores/:storeId/walk-ins/direct-seating', name: 'walk-in-direct-seating', component: EmptyShell },
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
    expect(page.text()).toContain('Table 01')
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
