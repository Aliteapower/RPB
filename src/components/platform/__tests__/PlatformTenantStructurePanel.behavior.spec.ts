import { mount, type VueWrapper } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'

import type { PlatformOperatingEntity, PlatformStore } from '../../../api/platformApi'
import PlatformTenantStructurePanel from '../PlatformTenantStructurePanel.vue'

const EMPTY_ENTITY: PlatformOperatingEntity = {
  id: 'entity-empty', tenantId: 'tenant-1', entityCode: 'empty', displayName: 'Empty Entity',
  status: 'active', defaultLocale: 'en-SG', contactPhone: null, address: null,
  principalName: null, deleted: false, createdAt: '2026-07-17T00:00:00Z',
  updatedAt: '2026-07-17T00:00:00Z', deletedAt: null
}
const USED_ENTITY: PlatformOperatingEntity = {
  ...EMPTY_ENTITY, id: 'entity-used', entityCode: 'used', displayName: 'Used Entity'
}
const USED_STORE: PlatformStore = {
  id: 'store-1', tenantId: 'tenant-1', operatingEntityId: USED_ENTITY.id,
  operatingEntityCode: USED_ENTITY.entityCode, operatingEntityName: USED_ENTITY.displayName,
  storeCode: 'store-1', storeName: 'Used Store', status: 'active', timezone: 'Asia/Singapore',
  locale: 'en-SG', dateFormat: 'DD-MM-YYYY', timeFormat: 'HH:mm', currency: 'SGD',
  deleted: false, createdAt: '2026-07-17T00:00:00Z',
  updatedAt: '2026-07-17T00:00:00Z', deletedAt: null
}

function mountPanel(): VueWrapper {
  const i18n = createI18n({ legacy: false, locale: 'en-SG', messages: { 'en-SG': {} } })
  return mount(PlatformTenantStructurePanel, {
    props: {
      operatingEntities: [EMPTY_ENTITY, USED_ENTITY],
      stores: [USED_STORE],
      adminStoreOptions: [],
      adminStoreIds: [],
      defaultAdminStoreId: '',
      saving: false
    },
    global: { plugins: [i18n] }
  })
}

describe('PlatformTenantStructurePanel operating entity deletion', () => {
  it('renders and emits delete only for an entity without current stores', async () => {
    const wrapper = mountPanel()
    const rows = wrapper.findAll('.structure-entity-row')
    expect(rows[0].find('.text-button.danger').exists()).toBe(true)
    expect(rows[1].find('.text-button.danger').exists()).toBe(false)
    await rows[0].get('.text-button.danger').trigger('click')
    expect(wrapper.emitted('deleteOperatingEntity')?.[0]).toEqual([EMPTY_ENTITY])
    await wrapper.setProps({ saving: true })
    expect(wrapper.findAll('.structure-entity-row')[0].get('.text-button.danger').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('closes a removed entity edit form and retains add in the empty state', async () => {
    const wrapper = mountPanel()
    await wrapper.findAll('.structure-entity-row')[0].get('.row-actions .text-button').trigger('click')
    expect(wrapper.find('.inline-form').exists()).toBe(true)
    await wrapper.setProps({ operatingEntities: [], stores: [] })
    expect(wrapper.find('.inline-form').exists()).toBe(false)
    expect(wrapper.find('.empty-state').exists()).toBe(true)
    expect(wrapper.find('.structure-section .secondary-button').exists()).toBe(true)
    wrapper.unmount()
  })

  it('selects the next entity when the selected entity disappears', async () => {
    const wrapper = mountPanel()
    await wrapper.setProps({ operatingEntities: [USED_ENTITY] })
    expect(wrapper.get('.structure-entity-button').classes()).toContain('structure-entity-button--active')
    expect(wrapper.get('.structure-entity-button').text()).toContain('Used Entity')
    wrapper.unmount()
  })
})
