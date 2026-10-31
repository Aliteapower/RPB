export type LoginEntryId = 'platform-admin' | 'tenant-admin' | 'tenant-staff';
export function initialLoginEntry(kind: 'platform' | 'tenant' | 'legacy', entry: unknown): LoginEntryId {
    if (kind === 'platform')
        return 'platform-admin';
    if (entry === 'staff')
        return 'tenant-staff';
    if (entry === 'tenant_admin')
        return 'tenant-admin';
    return kind === 'tenant' ? 'tenant-admin' : 'tenant-staff';
}
export function entryTenantCode(kind: 'platform' | 'tenant' | 'legacy', hostTenant: string, query: unknown, fallback: string): string {
    if (kind === 'tenant')
        return hostTenant;
    return kind === 'legacy' && typeof query === 'string' && /^(?!platform$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(query) ? query : fallback;
}
