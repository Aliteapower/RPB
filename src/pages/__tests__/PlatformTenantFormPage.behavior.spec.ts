import { createPinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { defineComponent } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { zhCN } from '../../i18n/locales/zh-CN'

const api = vi.hoisted(() => ({
  getTenant: vi.fn(),
  getTenantAdminStoreAccess: vi.fn(),
  listOperatingEntities: vi.fn(),
  listTenantStores: vi.fn(),
  deleteOperatingEntity: vi.fn()
}))

vi.mock('../../api/platformApi', async importOriginal => ({
  ...(await importOriginal<typeof import('../../api/platformApi')>()),
  getTenant: api.getTenant,
  getTenantAdminStoreAccess: api.getTenantAdminStoreAccess,
  listOperatingEntities: api.listOperatingEntities,
  listTenantStores: api.listTenantStores,
  deleteOperatingEntity: api.deleteOperatingEntity
}))

import { PlatformApiError, type PlatformOperatingEntity } from '../../api/platformApi'
import PlatformTenantFormPage from '../PlatformTenantFormPage.vue'

const ENTITY: PlatformOperatingEntity = {
  id: 'entity-1', tenantId: 'tenant-1', entityCode: 'entity-1', displayName: '经营主体一',
  status: 'active', defaultLocale: 'zh-CN', contactPhone: null, address: null,
  principalName: null, deleted: false, createdAt: '2026-07-17T00:00:00Z',
  updatedAt: '2026-07-17T00:00:00Z', deletedAt: null
}

const StructureStub = defineComponent({
  props: { operatingEntities: { type: Array, required: true } },
  emits: ['deleteOperatingEntity'],
  template: '<button class="delete-entity" @click="$emit(\'deleteOperatingEntity\', operatingEntities[0])">Delete</button>'
})
const EmptyStub = defineComponent({ template: '<div />' })
let wrapper: VueWrapper | undefined

async function mountPage(): Promise<VueWrapper> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/platform/tenants/:tenantId/edit', name: 'platform-tenant-edit', component: EmptyStub },
      { path: '/platform/tenants', name: 'platform-tenants', component: EmptyStub }
    ]
  })
  await router.push('/platform/tenants/tenant-1/edit')
  await router.isReady()
  const i18n = createI18n({
    legacy: false,
    locale: 'zh-CN',
    fallbackLocale: 'zh-CN',
    messages: { 'zh-CN': zhCN }
  })
  wrapper = mount(PlatformTenantFormPage, {
    global: {
      plugins: [createPinia(), router, i18n],
      stubs: {
        PlatformAdminNav: EmptyStub,
        PlatformTenantForm: EmptyStub,
        PlatformTenantStructurePanel: StructureStub
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('PlatformTenantFormPage operating entity deletion', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.getTenant.mockResolvedValue({
      success: true,
      tenant: {
        id: 'tenant-1', tenantCode: 'tenant-1', displayName: 'Tenant 1', status: 'active',
        defaultLocale: 'zh-CN', contactPhone: null, address: null, principalName: null,
        logoMediaUrl: null, deleted: false, createdAt: '2026-07-17T00:00:00Z',
        updatedAt: '2026-07-17T00:00:00Z', deletedAt: null
      }
    })
    api.getTenantAdminStoreAccess.mockResolvedValue({
      success: true, stores: [], storeIds: [], defaultStoreId: null
    })
    api.listOperatingEntities.mockResolvedValue({ success: true, operatingEntities: [ENTITY] })
    api.listTenantStores.mockResolvedValue({ success: true, stores: [] })
    api.deleteOperatingEntity.mockResolvedValue({ success: true, operatingEntity: ENTITY })
  })

  afterEach(() => wrapper?.unmount())

  it('does not call delete when confirmation is cancelled', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const page = await mountPage()
    await page.get('.delete-entity').trigger('click')
    expect(api.deleteOperatingEntity).not.toHaveBeenCalled()
  })

  it('deletes and reloads structure/access after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const page = await mountPage()
    await page.get('.delete-entity').trigger('click')
    await flushPromises()
    expect(api.deleteOperatingEntity).toHaveBeenCalledWith('tenant-1', 'entity-1')
    expect(api.listOperatingEntities).toHaveBeenCalledTimes(2)
    expect(api.listTenantStores).toHaveBeenCalledTimes(2)
    expect(api.getTenantAdminStoreAccess).toHaveBeenCalledTimes(2)
  })

  it('suppresses a duplicate delete while the first request is pending', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    api.deleteOperatingEntity.mockImplementationOnce(() => new Promise(() => undefined))
    const page = await mountPage()
    await page.get('.delete-entity').trigger('click')
    await page.get('.delete-entity').trigger('click')
    expect(api.deleteOperatingEntity).toHaveBeenCalledTimes(1)
  })

  it('shows the translated current-store conflict', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    api.deleteOperatingEntity.mockRejectedValueOnce(new PlatformApiError(409, {
      success: false,
      error: {
        code: 'OPERATING_ENTITY_HAS_STORES',
        messageKey: 'platform.tenants.operating_entity_has_stores',
        details: {}
      }
    }))
    const page = await mountPage()
    await page.get('.delete-entity').trigger('click')
    await flushPromises()
    expect(page.get('[role="alert"]').text()).toBe('该经营主体仍有门店，无法删除')
  })
})
