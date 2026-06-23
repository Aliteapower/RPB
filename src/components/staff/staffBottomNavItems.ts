export type StaffBottomNavTab = 'home' | 'reservation' | 'queue' | 'table'

export interface StaffBottomNavItem {
  tab: StaffBottomNavTab
  label: string
  routeName: string
  symbol: string
}

export const staffBottomNavItems: StaffBottomNavItem[] = [
  {
    tab: 'home',
    label: '首页',
    routeName: 'store-staff-home',
    symbol: '⌂'
  },
  {
    tab: 'reservation',
    label: '预约',
    routeName: 'reservation-today-view',
    symbol: '□'
  },
  {
    tab: 'queue',
    label: '排队',
    routeName: 'queue-ticket-list',
    symbol: '≡'
  },
  {
    tab: 'table',
    label: '桌台',
    routeName: 'table-resource-list',
    symbol: '▦'
  }
]
