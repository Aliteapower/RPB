/** Preserve existing vouchers and keep each campaign independent on this browser. */
export function voucherReceipt(campaign: string, storage: Pick<Storage, 'getItem' | 'setItem'> = localStorage): string {
  const key = 'rpb.voucher.receipt:' + campaign
  const existing = storage.getItem(key)
  if (existing) return existing
  const receipt = crypto.randomUUID()
  storage.setItem(key, receipt)
  return receipt
}
