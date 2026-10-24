import type { TransactionDetail } from './types'

// Audit and wallet-entry events currently share a generic type. Translate only
// the server's known system labels; preserve unknown labels and business data.
const legacyLabels: Record<string, string> = {
  '创建': 'created',
  '完成记账': 'postedCompleted',
  '实际记账': 'posted',
  '创建消费账单': 'billCreated',
  '确认外部收款': 'externalCollected',
  '顾客确认钱包付款': 'walletPaymentConfirmed',
  '扫码钱包付款': 'scanPayment',
  '关联付款确认': 'paymentLinked',
  '取消账单': 'billCancelled',
  '创建充值申请': 'topupCreated',
  '确认充值入账': 'topupConfirmed',
  '取消充值': 'topupCancelled',
  '发起退款': 'refundCreated',
  '确认外部退款': 'externalRefundConfirmed',
  '取消退款': 'refundCancelled',
  '异常收款已退回': 'exceptionRefunded',
  '充值权益入账': 'topupCredited',
  '钱包扣款': 'walletDebited',
  '钱包退款回存': 'walletRefunded',
  '退回充值并移除权益': 'topupRefunded',
  '移除赠送权益': 'bonusRevoked',
  '线下实收确认': 'collection',
  '外部退款完成': 'refundPartCompleted',
  '交易操作记录': 'action'
}

export function transactionEventLabel(
  event: TransactionDetail['events'][number],
  status: string,
  t: (key: string) => string,
): string {
  const type = event.type ?? event.kind
  let key: string | undefined
  if (type === 'created') key = 'created'
  else if (type === 'posted') key = status === 'completed' ? 'postedCompleted' : 'posted'
  else if (type === 'collection') key = 'collection'
  else if (type === 'refund_part_completed') key = 'refundPartCompleted'
  else if (event.label && Object.hasOwn(legacyLabels, event.label)) key = legacyLabels[event.label]
  else if (type === 'action' && !event.label) key = 'action'
  return key ? t(`wallet.transactions.eventLabels.${key}`) : (event.label ?? type ?? '')
}
