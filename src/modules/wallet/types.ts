export type Channel = 'cash' | 'paynow' | 'credit_card'
export interface Program { id: string; tenantId: string; operatingEntityId: string; channels: Channel[]; status: string; version: number }
export interface Account { id: string; tenantId: string; programId: string; customerId: string; balanceCents: number; reservedCents: number; status: string; version: number }
export interface Customer { id: string; customerCode: string; displayName: string | null; email: string | null; phone: string | null }
export interface Context {
  store: { tenantId: string; storeId: string; operatingEntityId: string | null; operatingEntityName: string | null; storeName: string; tenantCode: string; timezone: string; locale: string; logoMediaAssetId: string | null; contactPhone: string | null; address: string | null }
  program: Program | null; transactionsEnabled: boolean; denialReason: string | null; paynowConfigured: boolean
}
export interface Entry { id: string; accountId: string; storeId: string; kind: string; sourceId: string; amountCents: number; beforeCents: number; afterCents: number; accountVersion: number; createdAt: string }
export interface Topup { id: string; programId: string; accountId: string; storeId: string; amountCents: number; channel: Channel; status: string; refundedCents: number; version: number; createdAt: string }
export interface Bill { id: string; programId: string; accountId: string; storeId: string; amountCents: number; walletPaidCents: number; externalPaidCents: number; status: string; walletApplied: boolean; closed: boolean; refundStatus: string; version: number; createdAt: string }
export interface Quote { id: string; billId: string; accountId: string; amountCents: number; remainingCents: number; accountVersion: number; billVersion: number; expiresAt: string }
export interface PaymentCode { type: 'customer_wallet' | 'customer_charge' | 'merchant_bill'; code: string; expiresAt: string }
export interface CollectionConfirm { channel: Channel; amount: string; collectionAccount: string; receiptReference: string; terminalCode: string; note: string; verified: boolean }
export interface Collection { id: string; topupId: string | null; billId: string | null; channel: Channel; amountCents: number; refundedCents: number; receiptReference: string; collectionAccount: string; terminalCode: string; exception: boolean; exceptionReason: string; createdAt: string }
export interface Refund { id: string; accountId: string; storeId: string; topupId: string | null; billId: string | null; amountCents: number; walletCents: number; status: string; reason: string; version: number; createdAt: string }
export interface RefundDetail { refund: Refund; parts: Array<{ id: string; collectionId: string; channel: Channel; amountCents: number; completed: boolean; receiptReference: string | null }> }
export interface CustomerHome { context: Context; accounts: Account[]; pendingBills: Bill[] }
export interface Reconciliation { topupCashCents: number; topupPaynowCents: number; topupCardCents: number; walletSpentCents: number; externalBillCents: number; refundsCents: number; pendingTopupCents: number; outstandingBillCents: number; programBalanceCents: number; programReservedCents: number; exceptionReceivedCents: number; creditedTopupCents: number; pendingRefundCents: number; ledgerMismatchAccounts: number; collections: Collection[] }
