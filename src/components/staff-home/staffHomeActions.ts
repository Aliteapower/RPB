import type { RouteLocationRaw } from 'vue-router'

export type StaffHomeActionTone =
  | 'primary'
  | 'reservation'
  | 'queue'
  | 'success'
  | 'support'

export type StaffHomeActionItem = {
  id: string
  label: string
  description?: string
  symbol: string
  to: RouteLocationRaw
  tone: StaffHomeActionTone
  emphasis?: boolean
}
