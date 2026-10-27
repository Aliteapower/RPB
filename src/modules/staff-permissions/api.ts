export interface StaffPermissionChoice {
  code: string
  labelZh: string
  labelEn: string
  requires: string[]
  grantable: boolean
}
export interface StaffPermissionGroup {
  key: string
  appKey: string
  labelZh: string
  labelEn: string
  provisioned: boolean
  enabled: boolean
  permissions: StaffPermissionChoice[]
}
export interface StaffPermissions {
  success: true
  version: number
  inherited: boolean
  permissions: string[]
  groups: StaffPermissionGroup[]
}
export class StaffPermissionsError extends Error {
  constructor(readonly status: number, readonly code: string) { super(code) }
}
function endpoint(store: string, staff: string) {
  return `/api/v1/stores/${encodeURIComponent(store)}/tenant-admin/staff/${encodeURIComponent(staff)}/permissions`
}
async function request(store: string, staff: string, init: RequestInit, fetcher: typeof fetch): Promise<StaffPermissions> {
  const controller = new AbortController()
  const timeout = globalThis.setTimeout(() => controller.abort(), 12000)
  try {
    const response = await fetcher(endpoint(store, staff), {credentials: 'include', cache: 'no-store', signal: controller.signal, ...init})
    const value = await response.json().catch(() => null)
    if (!response.ok || value?.success !== true) throw new StaffPermissionsError(response.status, value?.error?.code ?? 'REQUEST_FAILED')
    return value
  } finally { globalThis.clearTimeout(timeout) }
}
export function loadStaffPermissions(store: string, staff: string, fetcher: typeof fetch = fetch): Promise<StaffPermissions> {
  return request(store, staff, {method: 'GET', headers: {Accept: 'application/json'}}, fetcher)
}
export function saveStaffPermissions(store: string, staff: string, expectedVersion: number, permissions: string[], fetcher: typeof fetch = fetch): Promise<StaffPermissions> {
  return request(store, staff, {method: 'PUT', headers: {Accept: 'application/json', 'Content-Type': 'application/json', 'X-RPB-Staff-Permissions': '1'}, body: JSON.stringify({expectedVersion, permissions})}, fetcher)
}
