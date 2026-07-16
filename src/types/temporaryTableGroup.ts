export interface SaveTemporaryTableGroupRequest {
  groupName: string
  tableIds: string[]
  businessDate: string
}

export interface TemporaryTableGroupResponse {
  success: true
  tableGroupId: string
  groupName: string
  groupType: string
  status: string
  capacityMin: number
  capacityMax: number
  tableIds: string[]
}

export interface TemporaryTableGroupApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface TemporaryTableGroupApiErrorResponse {
  success: false
  error: TemporaryTableGroupApiErrorBody
}
