import { createPinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

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

import { QueueTicketListApiError } from '../../api/queueTicketListApi'
import QueueTicketListPage from '../QueueTicketListPage.vue'

const EmptyShell = defineComponent({ template: '<div />' })
let wrapper: VueWrapper | undefined

async function mountPage(): Promise<VueWrapper> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/stores/:storeId/queue-tickets',
        name: 'queue-ticket-list',
        component: EmptyShell
      }
    ]
  })
  await router.push('/stores/store-1/queue-tickets')
  await router.isReady()

  wrapper = mount(QueueTicketListPage, {
    global: {
      plugins: [createPinia(), router],
      stubs: {
        StaffPrimaryWorkbench: { template: '<main><slot /></main>' },
        StaffHomeTopBar: true
      }
    }
  })
  return wrapper
}

describe('QueueTicketListPage behaviour', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.fetchMeApps.mockResolvedValue({ success: true, apps: [] })
    api.fetchTableResources.mockResolvedValue({ success: true, resources: [] })
  })

  afterEach(() => wrapper?.unmount())

  it('requests the first Queue page with the stable pagination contract', async () => {
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
  })

  it('shows loading and then the Queue success state', async () => {
    let resolveQueue!: (value: unknown) => void
    api.listQueueTickets.mockImplementationOnce(
      () => new Promise(resolve => { resolveQueue = resolve })
    )

    const loadingPage = await mountPage()
    await nextTick()

    expect(loadingPage.find('.state-panel').exists()).toBe(true)
    expect(api.listQueueTickets).toHaveBeenCalledWith('store-1', {
      limit: 100,
      offset: 0
    })

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
  })

  it('shows the stable empty Queue state after an empty response', async () => {
    api.listQueueTickets.mockResolvedValueOnce({
      success: true,
      items: [],
      page: { limit: 100, offset: 0, total: 0 }
    })

    const emptyPage = await mountPage()
    await flushPromises()

    expect(api.listQueueTickets).toHaveBeenCalledWith('store-1', {
      limit: 100,
      offset: 0
    })
    expect(emptyPage.find('.empty-queue-panel').exists()).toBe(true)
  })

  it('shows the Queue API error state', async () => {
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

    expect(api.listQueueTickets).toHaveBeenCalledWith('store-1', {
      limit: 100,
      offset: 0
    })
    expect(errorPage.find('.error-panel').exists()).toBe(true)
  })

  it('normalizes the phone filter before requesting Queue tickets', async () => {
    api.listQueueTickets.mockResolvedValueOnce({
      success: true,
      items: [],
      page: { limit: 100, offset: 0, total: 0 }
    })

    const filterPage = await mountPage()
    await flushPromises()

    expect(api.listQueueTickets).toHaveBeenCalledWith('store-1', {
      limit: 100,
      offset: 0
    })

    await filterPage.get('input[name="queuePhoneFilter"]').setValue('9123-4567')
    await flushPromises()

    expect(api.listQueueTickets).toHaveBeenLastCalledWith('store-1', {
      limit: 100,
      offset: 0,
      phone: '91234567'
    })
  })
})
