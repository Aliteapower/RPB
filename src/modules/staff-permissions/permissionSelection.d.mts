export interface PermissionChoice {code: string; requires: string[]; grantable: boolean}
export function togglePermission(selected: string[], code: string, checked: boolean, choices: PermissionChoice[]): string[]
export function permissionsForStore(base: string[], storePermissions: Record<string, string[]> | undefined, storeId: string): string[]
