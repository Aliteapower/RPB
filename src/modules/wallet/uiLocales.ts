export const walletUiZh = {
  navigation:'钱包导航', myWallet:'钱包', cashier:'收银台', staffWallet:'钱包收银台', back:'返回', more:'更多', entries:'资金流水',
  cashierTitle:'发起钱包收款', cashierHint:'扫顾客码由员工确认扣款，顾客扫账单码由顾客确认支付。', scanStart:'识别顾客付款码', selectStart:'查找已认证顾客',
  customerCollect:'选择顾客收款', customerTopup:'为顾客充值', offlineChannels:'现金 · PayNow · 信用卡', collectRecords:'收款账单',
  recordsHint:'待确认、待补付与已完成', pendingTopups:'待核实充值', pendingTopupsHint:'核对商户实际到账后入账', managementHint:'退款、对账与设置',
  changeCustomer:'更换', rescan:'重新扫码', noAccount:'该顾客尚无本店钱包，请先办理充值开通', scannedIdentity:'已识别付款码；有效期较短，请尽快发起收款。扫码不会直接扣款。',
  collect:'向顾客收款', receivable:'本次应收金额', collectConsent:'发起后由顾客在本人钱包核对并确认，余额不足部分仍需补付。', startCollection:'发起钱包收款',
  topupNotCredited:'这笔充值尚未入账。请核对实际收款，再确认入账。', nextConfirm:'下一步，核实收款', walletCollection:'钱包消费',
  recordType:'账单类型', filterStatus:'账单状态', noMatchingRecords:'暂无符合条件的账单', transactionInfo:'交易信息',
  scanPay:'扫一扫付款', scanPayHint:'扫描本门店的消费账单码，先核对金额，再确认付款。', pendingNotice:'有 {count} 笔账单待处理，点此查看',
  customerBillsHint:'查看消费与付款结果', entriesHint:'逐笔查看充值、消费和退款记录', offlineTopupHint:'充值请到店办理，支持现金、PayNow 和信用卡线下核实。',
  customerNoAccount:'尚无本店钱包，请到店联系员工办理充值。已有顾客资料会统一复用。', secureWallet:'统一顾客身份 · 储值钱包',
  codePrivate:'付款码仅供现场收银员识别，请勿转发。', viewBill:'查看原账单', confirmAmount:'确认付款金额', hideCollection:'收起补付表单',
  supplementHint:'钱包付款后仍需支付 {amount}，请向门店补付并由员工核实。',
  filters:{all:'全部',pending:'待处理',completed:'已完成',closed:'已关闭'},
  status:{waiting:'待顾客确认',due:'待补付',paid:'已完成',closed:'已关闭',pending:'待核实'}
}
export const walletUiEn = {
  navigation:'Wallet navigation', myWallet:'Wallet', cashier:'Cashier', staffWallet:'Wallet cashier', back:'Back', more:'More', entries:'Activity',
  cashierTitle:'Start a wallet payment', cashierHint:'Scan a code or choose a customer, then review the amount.', scanStart:'Read the customer’s payment code', selectStart:'Find a verified customer',
  customerCollect:'Select customer', customerTopup:'Top up a customer', offlineChannels:'Cash · PayNow · Card', collectRecords:'Payment bills',
  recordsHint:'Confirmation, balance due and completed', pendingTopups:'Unverified top-ups', pendingTopupsHint:'Verify merchant receipt before crediting', managementHint:'Refunds, reconciliation and settings',
  changeCustomer:'Change', rescan:'Scan again', noAccount:'No wallet at this store yet. Start with a top-up.', scannedIdentity:'Payment code identified. Start promptly before it expires. Scanning does not debit the wallet.',
  collect:'Collect from customer', receivable:'Amount to collect', collectConsent:'The customer reviews and confirms in their own wallet. Any shortfall remains payable.', startCollection:'Request wallet payment',
  topupNotCredited:'This top-up has not been credited. Check actual receipt before confirming.', nextConfirm:'Next: verify receipt', walletCollection:'Wallet purchase',
  recordType:'Bill type', filterStatus:'Bill status', noMatchingRecords:'No matching transactions', transactionInfo:'Transaction details',
  scanPay:'Scan to pay', scanPayHint:'Scan the store’s bill code, review the amount, then confirm.', pendingNotice:'{count} bill(s) need attention. View bills',
  customerBillsHint:'Purchases and payment results', entriesHint:'Individual top-ups, payments and refunds', offlineTopupHint:'Top up at the store. Cash, PayNow and card are verified offline.',
  customerNoAccount:'No wallet at this store yet. Ask staff to arrange a top-up using your existing customer profile.', secureWallet:'Shared customer identity · Wallet',
  codePrivate:'Only show this code to the cashier. Do not forward it.', viewBill:'View bill', confirmAmount:'Review payment amount', hideCollection:'Hide receipt form',
  supplementHint:'{amount} remains payable after wallet payment. Pay the store and ask staff to verify receipt.',
  filters:{all:'All',pending:'Pending',completed:'Completed',closed:'Closed'},
  status:{waiting:'Awaiting confirmation',due:'Balance due',paid:'Completed',closed:'Closed',pending:'Unverified'}
}
