export type TableResourceType = 'dining_table' | 'table_group'

export interface TableResourceQuery {
  status?: string
  partySize?: number
  includeGroups?: boolean
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
