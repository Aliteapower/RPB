export type TableResourceType = 'dining_table' | 'table_group'

export interface TableResourceQuery {
  status?: string
  partySize?: number
  includeGroups?: boolean
  businessDate?: string
}

export interface TableResourceItem {
  resourceType: 'dining_table' | 'table_group'
  resourceId: string
  code: string
  displayName: string
  areaName?: string | null
  capacityMin: number
  capacityMax: number
  status: string
  selectable: boolean
  selectionDisabledReason?: string | null
  memberTableCodes: string[]
  currentSeatingId?: string | null
  currentCleaningId?: string | null
  currentReservationId?: string | null
  currentPartySize?: number | null
  preassignedReservationId?: string | null
  preassignedReservationCode?: string | null
  preassignedCustomerName?: string | null
  preassignedPhoneMasked?: string | null
  preassignedReservationStatus?: string | null
  preassignedPartySize?: number | null
  preassignedStartAt?: string | null
  preassignedEndAt?: string | null
  preassignedResourceCode?: string | null
  preassignedQueueTicketId?: string | null
  preassignedQueueTicketNumber?: number | null
  preassignedQueueTicketStatus?: string | null
}

export interface TableResourceListResponse {
  success: true
  resources: TableResourceItem[]
}

export interface TableResourceApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface TableResourceApiErrorResponse {
  success: false
  error: TableResourceApiErrorBody
}

export type TableResourceApiResponse =
  | TableResourceListResponse
  | TableResourceApiErrorResponse
