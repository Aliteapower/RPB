/** Keep the issuing entity when it differs from the store, without repeating identical names. */
export function walletStoreCaption(store: {storeName?: string | null; operatingEntityName?: string | null} | undefined, fallback: string): string {
  return [...new Set([store?.storeName, store?.operatingEntityName].map(name => name?.trim()).filter(Boolean))].join(' · ') || fallback
}
