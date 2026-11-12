type Availability = { transactionsEnabled: boolean; denialReason?: string | null; program?: unknown | null }

export function walletHistoryOnly(context: Availability | null, explicitHistory = false): boolean {
  return explicitHistory || Boolean(context && !context.transactionsEnabled && context.denialReason && context.denialReason !== 'WALLET_POINTS_DISABLED')
}
export function walletNeedsSetup(context: Availability | null): boolean {
  return Boolean(context && !context.program && !context.transactionsEnabled && !context.denialReason)
}
