import { defineStore } from 'pinia'

import { AuthApiError, fetchCurrentStores, fetchCurrentUser, login, logout } from '../api/authApi'
import type { AuthStoreAccess, AuthUser, LoginRequest } from '../types/auth'
import { permissionsForStore } from '../modules/staff-permissions/permissionSelection.mjs'

const missingStoreScopeRoute = '/login?storeScope=missing'

export const useAuthSessionStore = defineStore('authSession', {
  state: () => ({
    user: null as AuthUser | null,
    basePermissions: [] as string[],
    permissionStoreId: '',
    userLoadRevision: 0,
    authorizedStores: [] as AuthStoreAccess[],
    storesLoaded: false,
    storesLoading: false,
    loaded: false,
    loading: false
  }),
  getters: {
    isAuthenticated: state => state.user !== null,
    platformHomeRoute: () => '/platform/tenants',
    isPlatformAdmin: state => state.user?.roles.includes('platform_admin') === true,
    isTenantAdmin: state =>
      state.user?.roles.includes('tenant_admin') === true &&
      state.user?.permissions.includes('tenant.admin.manage') === true,
    tenantAdminHomeRoute: state => {
      const storeId = state.user?.defaultStoreId || state.user?.storeIds[0]
      return storeId ? `/stores/${storeId}/admin/profile` : missingStoreScopeRoute
    },
    defaultStoreRoute: state => {
      const storeId = state.user?.defaultStoreId || state.user?.storeIds[0]
      return storeId ? `/stores/${storeId}/staff` : missingStoreScopeRoute
    },
    defaultHomeRoute(): string {
      if (this.isPlatformAdmin) {
        return this.platformHomeRoute
      }
      return this.isTenantAdmin ? this.tenantAdminHomeRoute : this.defaultStoreRoute
    }
  },
  actions: {
    async ensureCurrentUser(force = false): Promise<AuthUser | null> {
      if (this.loaded && !force) {
        return this.user
      }

      const revision = ++this.userLoadRevision
      this.loading = true
      try {
        const response = await fetchCurrentUser()
        if (revision !== this.userLoadRevision) return this.user
        this.basePermissions = [...response.user.permissions]
        this.user = response.user
        this.applyStorePermissions(this.permissionStoreId)
      } catch (error) {
        if (revision !== this.userLoadRevision) return this.user
        if (!(error instanceof AuthApiError) || error.status !== 401) {
          this.user = null
        } else {
          this.user = null
        }
        this.authorizedStores = []
        this.basePermissions = []
        this.storesLoaded = false
      } finally {
        if (revision === this.userLoadRevision) {
          this.loaded = true
          this.loading = false
        }
      }

      return this.user
    },
    applyStorePermissions(storeId: string): void {
      this.permissionStoreId = storeId
      if (this.user) this.user.permissions = permissionsForStore(this.basePermissions, this.user.storePermissions, storeId)
    },
    async refreshStorePermissions(storeId: string): Promise<AuthUser | null> {
      this.permissionStoreId = storeId
      return this.ensureCurrentUser(true)
    },
    async ensureAuthorizedStores(force = false): Promise<AuthStoreAccess[]> {
      if (!this.user || this.isPlatformAdmin) {
        this.authorizedStores = []
        this.storesLoaded = true
        return this.authorizedStores
      }
      if (this.storesLoaded && !force) {
        return this.authorizedStores
      }

      this.storesLoading = true
      try {
        const response = await fetchCurrentStores()
        this.authorizedStores = response.stores
        this.storesLoaded = true
      } catch {
        this.authorizedStores = []
        this.storesLoaded = true
      } finally {
        this.storesLoading = false
      }

      return this.authorizedStores
    },
    async loginWithPassword(request: LoginRequest): Promise<AuthUser> {
      const response = await login(request)
      ++this.userLoadRevision
      this.basePermissions = [...response.user.permissions]
      this.user = response.user
      this.applyStorePermissions(response.user.defaultStoreId || response.user.storeIds[0] || '')
      this.loaded = true
      this.loading = false
      this.authorizedStores = []
      this.storesLoaded = false
      return response.user
    },
    async logoutCurrentUser(): Promise<void> {
      try {
        await logout()
      } finally {
        ++this.userLoadRevision
        this.user = null
        this.basePermissions = []
        this.permissionStoreId = ''
        this.authorizedStores = []
        this.storesLoaded = false
        this.loaded = true
        this.loading = false
      }
    },
    clear(): void {
      ++this.userLoadRevision
      this.user = null
      this.basePermissions = []
      this.permissionStoreId = ''
      this.authorizedStores = []
      this.storesLoaded = false
      this.loaded = true
      this.loading = false
    }
  }
})
