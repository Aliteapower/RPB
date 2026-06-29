export type ReservationMealPeriodStatus = 'active' | 'disabled'

export interface ReservationMealPeriod {
  id: string
  periodKey: string
  displayName: string
  startLocalTime: string
  endLocalTime: string
  crossesNextDay: boolean
  slotIntervalMinutes: number
  status: ReservationMealPeriodStatus
  sortOrder: number
  version: number
}

export interface ReservationMealPeriodMutation {
  periodKey: string
  displayName: string
  startLocalTime: string
  endLocalTime: string
  crossesNextDay: boolean
  slotIntervalMinutes: number
  status: ReservationMealPeriodStatus
  sortOrder: number
  version?: number
}

export interface PlatformReservationMealPeriodSeedMutation {
  periods: ReservationMealPeriodMutation[]
}

export interface PlatformReservationMealPeriodSeedResponse {
  success: true
  periods: ReservationMealPeriod[]
}

export interface ReservationMealPeriodSettingsMutation {
  usePlatformSeed: boolean
  copyPlatformSeed?: boolean
  periods?: ReservationMealPeriodMutation[]
}

export interface ReservationMealPeriodSettingsResponse {
  success: true
  usePlatformSeed: boolean
  platformPeriods: ReservationMealPeriod[]
  storePeriods: ReservationMealPeriod[]
  effectivePeriods: ReservationMealPeriod[]
}

export interface ReservationTimeSlot {
  periodId: string
  periodKey: string
  displayName: string
  businessDate: string
  time: string
  startAt: string
  nextDay: boolean
  selectable: boolean
}

export interface ReservationTimeSlotListResponse {
  success: true
  storeId: string
  businessDate: string
  timezone: string
  slots: ReservationTimeSlot[]
}

export interface ReservationMealPeriodApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}
