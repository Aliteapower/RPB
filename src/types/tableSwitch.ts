export type TableSwitchIdempotencyStatus =
  | 'started'
  | 'completed'
  | 'failed'
  | 'conflict'
  | 'in_progress'
  | 'expired'
  | (string & {})

export type TableSwitchResourceType = 'TABLE' | 'TABLE_GROUP' | 'dining_table' | 'table_group' | (string & {})

export interface SwitchTableRequest {
  tableId?: string | null
  tableGroupId?: string | null
  reasonCode?: string | null
  note?: string | null
}

export interface TableSwitchResourceResponse {
  type: TableSwitchResourceType
  id: string
  status: string
}

export interface TableSwitchIdempotency {
  status: TableSwitchIdempotencyStatus
  replayed?: boolean
}

export interface SwitchTableResponse {
  success: true
  seatingId: string
  fromResource: TableSwitchResourceResponse
  toResource: TableSwitchResourceResponse
  cleaningId: string | null
  seatingStatus: string
  events: string[]
  idempotency: TableSwitchIdempotency
}

export interface TableSwitchApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface TableSwitchApiErrorResponse {
  success: false
  error: TableSwitchApiErrorBody
  idempotency?: TableSwitchIdempotency
}

export type TableSwitchApiResponse = SwitchTableResponse | TableSwitchApiErrorResponse
