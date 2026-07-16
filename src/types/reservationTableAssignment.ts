export interface AssignableReservationTable {
  tableId: string
  tableCode: string
  displayName: string
  areaName?: string | null
  capacityMin: number
  capacityMax: number
}

export interface AssignableReservationTablesResponse {
  success: true
  reservationId: string
  partySize: number
  tables: AssignableReservationTable[]
}

export interface ReservationTableAssignmentResponse {
  success: true
  reservationId: string
  tableId: string
  tableCode: string
  assignmentStatus: 'active' | (string & {})
  idempotency: {
    status: string
    replayed: boolean
  }
}

export interface ReservationTableAssignmentApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
  idempotency?: {
    status: string
    replayed?: boolean | null
  } | null
}
