export type QueueTicketListStatusFilter =
  | 'waiting'
  | 'called'
  | 'skipped'
  | 'rejoined'
  | 'seated'
  | 'cancelled'
  | 'expired'
  | (string & {})

export interface QueueTicketListQuery {
  status?: QueueTicketListStatusFilter
  tableArea?: string
  partySize?: number
  phone?: string
  limit?: number
  offset?: number
}

export interface QueueTicketListItem {
  queueTicketId: string
  queueTicketNumber: number
  queueTicketDisplayNumber: string
  queueTicketStatus: string
  partySize: number
  partySizeGroup: string
  reservationId?: string | null
  reservationCode?: string | null
  reservationStatus?: string | null
  customerName?: string | null
  customerPhoneMasked?: string | null
  assignedResourceType?: 'dining_table' | 'table_group' | (string & {}) | null
  assignedResourceId?: string | null
  assignedResourceCode?: string | null
  assignedResourceGroupType?: string | null
  assignedResourceLabel?: string | null
  assignedResourceAreaName?: string | null
  createdAt: string
  calledAt?: string | null
  holdUntilAt?: string | null
  expiresAt?: string | null
}

export interface QueueTicketListPage {
  limit: number
  offset: number
  total: number
}

export interface QueueTicketListResponse {
  success: true
  items: QueueTicketListItem[]
  page: QueueTicketListPage
}

export interface QueueTicketListApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface QueueTicketListApiErrorResponse {
  success: false
  error: QueueTicketListApiErrorBody
}

export type QueueTicketListApiResponse =
  | QueueTicketListResponse
  | QueueTicketListApiErrorResponse
