import type { AuthUser } from '../types/auth'

export type LoginEntryId = 'platform-admin' | 'tenant-admin' | 'tenant-staff'

export type PostLoginDestination =
  | { kind: 'route'; path: string }
  | { kind: 'missing-store' }
  | { kind: 'default-home' }

interface PostLoginDestinationInput {
  redirect: string | undefined
  entryId: LoginEntryId
  user: AuthUser
  entryStoreId: string | null
  isPlatformAdmin: boolean
  isTenantAdmin: boolean
  platformHomeRoute: string
}

export function resolvePostLoginDestination(input: PostLoginDestinationInput): PostLoginDestination {
  if (input.redirect) {
    return { kind: 'route', path: input.redirect }
  }

  if (input.entryId === 'platform-admin' && input.user.roles.includes('platform_admin')) {
    return { kind: 'route', path: input.platformHomeRoute }
  }

  if (!input.isPlatformAdmin && input.user.storeIds.length === 0) {
    return { kind: 'missing-store' }
  }

  const storeId = preferredLoginStoreId(input.user, input.entryStoreId)
  if (input.entryId === 'tenant-admin' && input.isTenantAdmin) {
    return { kind: 'route', path: tenantAdminStoreRoute(storeId) }
  }

  if (input.entryId === 'tenant-staff') {
    return { kind: 'route', path: storeRoute(storeId) }
  }

  return { kind: 'default-home' }
}

export function preferredLoginStoreId(user: AuthUser, entryStoreId: string | null): string {
  if (entryStoreId && user.storeIds.includes(entryStoreId)) {
    return entryStoreId
  }
  return user.defaultStoreId && user.storeIds.includes(user.defaultStoreId)
    ? user.defaultStoreId
    : (user.storeIds[0] ?? '')
}

export function storeRoute(storeId: string): string {
  return `/stores/${storeId}/staff`
}

export function tenantAdminStoreRoute(storeId: string): string {
  return `/stores/${storeId}/admin/profile`
}
