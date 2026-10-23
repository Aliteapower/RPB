export interface EntryIdentity {
    tenantId: string;
    tenantCode: string;
    tenantName: string;
    tenantStatus: string;
    storeId: string;
    storeCode: string;
    storeName: string;
    storeStatus: string;
}
export interface EntryLinksContext {
    success: true;
    identity: EntryIdentity;
    publicBookingEnabled: boolean;
    products: Array<{
        appKey: string;
        available: boolean;
        denialReason: string | null;
    }>;
}
export interface TenantEntry {
    key: string;
    audience: 'customer' | 'staff' | 'admin';
    url: string;
    available: boolean;
    denialReason: string | null;
}
