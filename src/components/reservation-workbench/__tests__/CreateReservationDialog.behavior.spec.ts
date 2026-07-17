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
