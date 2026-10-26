export type ManagedProductKey = 'wallet' | 'daily_ledger' | 'wine_storage' | 'payment' | 'reservation_queue'
export interface StoreProductSetting {
  appKey: ManagedProductKey
  enabled: boolean
  canEnable: boolean
  unavailableReason: string | null
}
