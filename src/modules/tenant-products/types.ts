export type ManagedProductKey = 'wallet' | 'daily_ledger'
export interface StoreProductSetting {
  appKey: ManagedProductKey
  enabled: boolean
  canEnable: boolean
  unavailableReason: string | null
}
