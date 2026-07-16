export type StaffBottomNavTab = 'home' | 'reservation' | 'queue' | 'table'

export interface StaffBottomNavItem {
  tab: StaffBottomNavTab
  labelKey: string
  routeName: string
  symbol: string
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
    symbol: '□'
  },
  {
    tab: 'queue',
    labelKey: 'nav.staff.queue',
    routeName: 'queue-ticket-list',
    symbol: '≡'
  },
  {
    tab: 'table',
    labelKey: 'nav.staff.table',
    routeName: 'table-resource-list',
    symbol: '▦'
  }
]
