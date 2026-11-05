export interface MeAppEntry {
  appKey: string
  appName: string
  status: string
  entryRoute: string
  entryVisible: boolean
  permissions: string[]
}

export interface ProductRouteAccess {
  appKey: string
  businessAllowed: boolean
  historyAllowed: boolean
  historyRoute: string | null
  denialReason?: string | null
}

export interface MeAppsResponse {
  success: true
  apps: MeAppEntry[]
  historyApps?: MeAppEntry[]
  productAccess?: ProductRouteAccess[]
}

export interface MeAppsApiErrorBody {
  code: string
  messageKey: string
  details: Record<string, unknown>
}

export interface MeAppsApiErrorResponse {
  success: false
  error: MeAppsApiErrorBody
}

export type MeAppsApiResponse = MeAppsResponse | MeAppsApiErrorResponse
