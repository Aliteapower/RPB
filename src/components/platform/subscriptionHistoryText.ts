export function subscriptionHistoryText(english: boolean) {
  return english ? {
    title: 'Commercial operation history', notice: 'Snapshots record subscription items after each commercial operation; they are not bank receipt evidence.',
    loading: 'Loading history…', empty: 'No commercial operations recorded.', error: 'Unable to load history.', forbidden: 'You do not have permission to view this history.',
    legacy: 'This older record has no saved store snapshot. Historical names cannot be reconstructed.', noStores: 'No store items in this snapshot.',
    entity: 'Operating entity', store: 'Store', period: 'Period', amount: 'Amount', status: 'Status', note: 'Payment note', retry: 'Retry', quantity: 'Quantity'
  } : {
    title: '商业操作历史', notice: '快照记录每次商业操作后的订阅项目，不作为银行实收凭证。',
    loading: '正在加载历史记录…', empty: '暂无商业操作记录。', error: '历史记录加载失败。', forbidden: '无权查看此商业历史。',
    legacy: '此旧记录未保存门店快照，无法还原当时的门店及主体名称。', noStores: '此快照没有门店项目。',
    entity: '经营主体', store: '门店', period: '期间', amount: '金额', status: '状态', note: '付款备注', retry: '重试', quantity: '数量'
  }
}

export function historyValue(value: string | null, english: boolean): string {
  if (!value) return '—'
  const values: Record<string, [string, string]> = {
    purchase: ['购买', 'Purchase'], renew: ['续费', 'Renewal'], renew_item: ['单店续费', 'Store renewal'],
    item_renew: ['单店续费', 'Store renewal'], add_stores: ['添加门店', 'Add stores'], suspend: ['暂停', 'Suspend'],
    cancel: ['取消', 'Cancel'], convert_from_legacy: ['转为付费', 'Convert legacy grant'], manual_adjust: ['手动调整', 'Manual adjustment'],
    active: ['有效', 'Active'], inactive: ['停用', 'Inactive'], suspended: ['已暂停', 'Suspended'], cancelled: ['已取消', 'Cancelled'],
    monthly: ['按月', 'Monthly'], yearly: ['按年', 'Yearly'], legacy_grant: ['历史赠送', 'Legacy grant']
  }
  return values[value]?.[english ? 1 : 0] ?? value
}
export function historyAmount(amount: number | null, currency: string | null): string {
  return amount == null ? '—' : `${currency ?? ''} ${Number(amount).toFixed(2)}`.trim()
}
