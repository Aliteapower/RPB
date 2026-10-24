import type { Transaction } from './types'

/** The API returns null for system defaults; literal business descriptions stay intact. */
export function transactionSummary(
  transaction: Pick<Transaction, 'summary' | 'sourceType'>,
  t: (key: string) => string,
): string {
  if (transaction.summary != null) return transaction.summary
  if (transaction.sourceType === 'bill') return t('wallet.transactions.payment')
  if (transaction.sourceType === 'topup') return t('wallet.transactions.topup')
  return ''
}
