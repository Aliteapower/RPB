import { walletApi, WalletApiError } from './api'
import type { Account, Bill } from './types'

export function chargePreview(amount: string | number, availableCents: number) {
  // Vue type=number inputs coerce v-model to a number even without the .number modifier.
  const text=String(amount)
  if (!/^(0|[1-9]\d*)(\.\d{1,2})?$/.test(text)) return null
  const [whole, fraction = ''] = text.split('.')
  const totalCents = Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
  if (!Number.isSafeInteger(totalCents) || totalCents <= 0 || totalCents > 9_999_999_999_999 || availableCents < 0) return null
  const debitCents = Math.min(totalCents, availableCents)
  return { totalCents, debitCents, dueCents: totalCents - debitCents }
}
export const isChargeCode = (code: string) => /^WC\.[A-Za-z0-9_-]{43}$/.test(code)

export function confirmScanCharge(store: string, account: Account, amount: string | number, customerCode: string, bill?: Bill) {
  return walletApi(store).command<{ bill: Bill; account: Account }>('/scan-charges', {
    accountId: account.id, amount: String(amount), customerCode, expectedAccountVersion: account.version,
    ...(bill ? { billId: bill.id, expectedBillVersion: bill.version } : {})
  })
}

/** Keep the reviewed versions in memory after an uncertain response, even if balance polling advances. */
export function createScanChargeAttempt() {
  let pending: { key: string; account: Account; bill?: Bill } | null = null
  return async (store: string, account: Account, amount: string | number, code: string, bill?: Bill) => {
    const key=JSON.stringify([store,account.id,String(amount),code,bill?.id])
    if(pending?.key!==key)pending={key,account:{...account},bill:bill?{...bill}:undefined}
    try {
      const result=await confirmScanCharge(store,pending.account,amount,code,pending.bill)
      pending=null;return result
    }catch(e){if(e instanceof WalletApiError && e.status>=400 && e.status<500)pending=null;throw e}
  }
}
