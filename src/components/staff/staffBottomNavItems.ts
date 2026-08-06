export type StaffBottomNavTab = 'home' | 'reservation' | 'queue' | 'table' | 'payment'

export interface StaffBottomNavItem {
  tab: StaffBottomNavTab
  labelKey: string
  routeName: string
  symbol: string
  appKey?: 'reservation_queue' | 'payment'
}

export const staffBottomNavItems: StaffBottomNavItem[] = [
  {
    tab: 'home',
    labelKey: 'nav.staff.home',
    routeName: 'store-staff-home',
    symbol: '⌂'
  },
  {
    tab: 'reservation',
    labelKey: 'nav.staff.reservation',
    routeName: 'reservation-today-view',
    symbol: '□',
    appKey: 'reservation_queue'
  },
  {
    tab: 'queue',
    labelKey: 'nav.staff.queue',
    routeName: 'queue-ticket-list',
    symbol: '≡',
    appKey: 'reservation_queue'
  },
  {
    tab: 'table',
    labelKey: 'nav.staff.table',
    routeName: 'table-resource-list',
    symbol: '▦',
    appKey: 'reservation_queue'
  },
  {
    tab: 'payment',
    labelKey: 'nav.staff.payment',
    routeName: 'payment-quick-pay',
    symbol: '$',
    appKey: 'payment'
  }
]
