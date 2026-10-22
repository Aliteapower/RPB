type Store = { id: string; status: string; operatingEntityId: string | null }

export function storesForEntity<T extends Store>(stores: T[], entity: string): T[] {
  return stores.filter(store => !entity || (entity === 'unassigned' ? !store.operatingEntityId : store.operatingEntityId === entity))
}

export function walletBillingWarning(stores: Store[], selectedCount: number, eligibleCount: number, activePrice: boolean): string {
  if (!activePrice) return 'inactivePrice'
  if (selectedCount > 0) return ''
  if (!stores.length) return 'noStores'
  const active = stores.filter(store => store.status === 'active')
  if (!active.length) return 'noActiveStores'
  if (eligibleCount > 0) return 'selectStores'
  if (active.some(store => !store.operatingEntityId)) return 'unassignedStores'
  return 'noAdditionalStores'
}
