import { defineStore } from 'pinia'

import { AuthApiError, fetchCurrentUser, login, logout } from '../api/authApi'
import type { AuthUser, LoginRequest } from '../types/auth'

const localValidationStoreId = '20000000-0000-0000-0000-000000000983'

export const useAuthSessionStore = defineStore('authSession', {
  state: () => ({
    user: null as AuthUser | null,
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
      const storeId = state.user?.defaultStoreId || state.user?.storeIds[0] || localValidationStoreId
      return `/stores/${storeId}/admin/profile`
    },
    defaultStoreRoute: state => {
      const storeId = state.user?.defaultStoreId || state.user?.storeIds[0] || localValidationStoreId
      return `/stores/${storeId}/staff`
    },
    defaultHomeRoute(): string {
      if (this.isPlatformAdmin) {
        return this.platformHomeRoute
      }
      return this.isTenantAdmin ? this.tenantAdminHomeRoute : this.defaultStoreRoute
    }
  },
  actions: {
    async ensureCurrentUser(): Promise<AuthUser | null> {
      if (this.loaded) {
        return this.user
      }

      this.loading = true
      try {
        const response = await fetchCurrentUser()
        this.user = response.user
      } catch (error) {
        if (!(error instanceof AuthApiError) || error.status !== 401) {
          this.user = null
        } else {
          this.user = null
        }
      } finally {
        this.loaded = true
        this.loading = false
      }

      return this.user
    },
    async loginWithPassword(request: LoginRequest): Promise<AuthUser> {
      const response = await login(request)
      this.user = response.user
      this.loaded = true
      return response.user
    },
    async logoutCurrentUser(): Promise<void> {
      try {
        await logout()
      } finally {
        this.user = null
        this.loaded = true
      }
    },
    clear(): void {
      this.user = null
      this.loaded = true
    }
  }
})
