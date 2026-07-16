export interface StaffHomeReservationMetrics {
  totalReservations: number
  totalPartySize: number
  arrivedReservations: number
  arrivedPartySize: number
  seatedReservations: number
  seatedPartySize: number
  cancelledReservations: number
}

export interface StaffHomeQueueMetrics {
  waitingTickets: number
  waitingPartySize: number
  calledTickets: number
  calledPartySize: number
  seatedTickets: number
  skippedTickets: number
  cancelledTickets: number
  expiredTickets: number
}

export interface StaffHomeTableMetrics {
  totalTables: number
  availableTables: number
  reservedTables: number
  occupiedTables: number
  cleaningTables: number
  temporaryGroups: number
}

export interface StaffHomePartySizeGroupMetrics {
  label: string
  groups: number
  partySize: number
}

export interface StaffHomeOverviewResponse {
  success: true
  storeId: string
  businessDate: string
  storeTimezone: string
  reservation: StaffHomeReservationMetrics
  queue: StaffHomeQueueMetrics
  tables: StaffHomeTableMetrics
  partySizeGroups: StaffHomePartySizeGroupMetrics[]
}

export interface StaffHomeOverviewQuery {
  businessDate?: string
}

export interface StaffHomeOverviewApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface StaffHomeOverviewApiErrorResponse {
  success: false
  error: StaffHomeOverviewApiErrorBody
}

export type StaffHomeOverviewApiResponse =
  | StaffHomeOverviewResponse
  | StaffHomeOverviewApiErrorResponse
