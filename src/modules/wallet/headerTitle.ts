export function walletHeaderTitleKey(routeName: unknown, customer = false): string {
  if (routeName === 'wallet-cards') return 'wallet.ui.cardsEntry'
  if (routeName === 'wallet-customer-cards') return 'wallet.ui.myCards'
  return customer ? 'wallet.customerWallet' : 'wallet.ui.staffWallet'
}
