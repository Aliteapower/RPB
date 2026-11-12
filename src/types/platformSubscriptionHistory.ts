export interface SubscriptionStoreSnapshot {
  storeId: string
  storeCode: string | null
  storeName: string
  operatingEntityId: string | null
  operatingEntityName: string | null
  storeStatus: string | null
  itemStatus: string | null
  quantity: number | null
  unitAmount: number | null
  amount: number | null
  currency: string | null
  periodStart: string | null
  periodEnd: string | null
}

export interface SubscriptionHistoryEvent {
  id: string
  eventType: string
  billingCycle: string | null
  status: string | null
  periodStart: string | null
  periodEnd: string | null
  amount: number | null
  currency: string | null
  paymentNote: string | null
  createdAt: string
  snapshotAvailable: boolean
  stores: SubscriptionStoreSnapshot[]
}
export interface SubscriptionHistoryResponse { events: SubscriptionHistoryEvent[] }
