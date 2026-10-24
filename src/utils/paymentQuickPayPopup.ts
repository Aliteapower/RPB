export const QUICK_PAYMENT_POPUP_TARGET = 'rpb-quick-payment'

export const QUICK_PAYMENT_POPUP_FEATURES = [
  'popup=yes',
  'width=430',
  'height=860',
  'left=80',
  'top=40',
  'resizable=yes',
  'scrollbars=yes'
].join(',')

export function openQuickPaymentPopup(href: string): boolean {
  const popup = window.open(href, QUICK_PAYMENT_POPUP_TARGET, QUICK_PAYMENT_POPUP_FEATURES)

  if (!popup) {
    return false
  }

  popup.focus()
  return true
}
