import { defineStore } from 'pinia'

const localValidationStoreId = '20000000-0000-0000-0000-000000000983'
const envDefaultStoreId = import.meta.env.VITE_DEFAULT_STORE_ID || localValidationStoreId

export const useStoreContextStore = defineStore('storeContext', {
  state: () => ({
    defaultStoreId: envDefaultStoreId
  }),
  actions: {
    resolveStoreId(routeStoreId: string | string[] | undefined): string {
      if (Array.isArray(routeStoreId)) {
        return routeStoreId[0] || this.defaultStoreId
      }

      return routeStoreId || this.defaultStoreId
    }
  }
})
