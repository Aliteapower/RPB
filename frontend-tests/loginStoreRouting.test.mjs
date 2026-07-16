import assert from 'node:assert/strict'
import test from 'node:test'

import { resolvePostLoginDestination } from '../src/pages/loginStoreRouting.ts'

function authUser(overrides = {}) {
  return {
    accountId: 'account-1',
    tenantId: 'tenant-1',
    username: 'staff',
    displayName: 'Staff',
    actorType: 'store_staff',
    defaultStoreId: 'store-a',
    storeIds: ['store-a', 'store-b'],
    roles: ['store_staff'],
    permissions: [],
    ...overrides
  }
}

test('staff enters the authorised current-domain store', () => {
  assert.deepEqual(resolvePostLoginDestination({
    redirect: undefined,
    entryId: 'tenant-staff',
    user: authUser(),
    entryStoreId: 'store-b',
    isPlatformAdmin: false,
    isTenantAdmin: false,
    platformHomeRoute: '/platform/tenants'
  }), {
    kind: 'route',
    path: '/stores/store-b/staff'
  })
})

test('unauthorised entry store falls back to an authorised account default', () => {
  assert.deepEqual(resolvePostLoginDestination({
    redirect: undefined,
    entryId: 'tenant-staff',
    user: authUser(),
    entryStoreId: 'store-x',
    isPlatformAdmin: false,
    isTenantAdmin: false,
    platformHomeRoute: '/platform/tenants'
  }), {
    kind: 'route',
    path: '/stores/store-a/staff'
  })
})

test('invalid entry and account defaults fall back to the first authorised store', () => {
  assert.deepEqual(resolvePostLoginDestination({
    redirect: undefined,
    entryId: 'tenant-staff',
    user: authUser({ defaultStoreId: 'store-x', storeIds: ['store-b', 'store-c'] }),
    entryStoreId: 'store-y',
    isPlatformAdmin: false,
    isTenantAdmin: false,
    platformHomeRoute: '/platform/tenants'
  }), {
    kind: 'route',
    path: '/stores/store-b/staff'
  })
})

test('tenant administrator enters the resolved store admin workbench', () => {
  assert.deepEqual(resolvePostLoginDestination({
    redirect: undefined,
    entryId: 'tenant-admin',
    user: authUser({ actorType: 'tenant_admin', roles: ['tenant_admin'] }),
    entryStoreId: 'store-b',
    isPlatformAdmin: false,
    isTenantAdmin: true,
    platformHomeRoute: '/platform/tenants'
  }), {
    kind: 'route',
    path: '/stores/store-b/admin/profile'
  })
})

test('explicit redirect keeps precedence over store entry', () => {
  assert.deepEqual(resolvePostLoginDestination({
    redirect: '/stores/store-a/staff/reservations',
    entryId: 'tenant-staff',
    user: authUser(),
    entryStoreId: 'store-b',
    isPlatformAdmin: false,
    isTenantAdmin: false,
    platformHomeRoute: '/platform/tenants'
  }), {
    kind: 'route',
    path: '/stores/store-a/staff/reservations'
  })
})

test('platform administrator keeps platform routing precedence', () => {
  assert.deepEqual(resolvePostLoginDestination({
    redirect: undefined,
    entryId: 'platform-admin',
    user: authUser({ tenantId: null, defaultStoreId: null, storeIds: [], roles: ['platform_admin'] }),
    entryStoreId: null,
    isPlatformAdmin: true,
    isTenantAdmin: false,
    platformHomeRoute: '/platform/tenants'
  }), {
    kind: 'route',
    path: '/platform/tenants'
  })
})

test('tenant account without an authorised store requires missing-store handling', () => {
  assert.deepEqual(resolvePostLoginDestination({
    redirect: undefined,
    entryId: 'tenant-staff',
    user: authUser({ defaultStoreId: null, storeIds: [] }),
    entryStoreId: null,
    isPlatformAdmin: false,
    isTenantAdmin: false,
    platformHomeRoute: '/platform/tenants'
  }), {
    kind: 'missing-store'
  })
})
