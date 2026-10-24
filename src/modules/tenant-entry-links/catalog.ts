import { publicBookingUrlForTenant } from '../../utils/hostContext';
import type { EntryLinksContext, TenantEntry } from './types';
export function buildTenantEntries(context: EntryLinksContext, location: Pick<Location, 'protocol' | 'hostname' | 'port' | 'origin'> = window.location): TenantEntry[] {
    const id = context.identity, code = id.tenantCode.trim().toLowerCase();
    if (!/^(?!platform$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(code) || !/^https?:$/.test(location.protocol))
        throw new Error('Invalid tenant entry origin');
    const origin = new URL(publicBookingUrlForTenant(code, id.storeId, location)).origin;
    const store = encodeURIComponent(id.storeId), active = id.tenantStatus === 'active' && id.storeStatus === 'active';
    const login = (path: string, entry = 'staff') => `${origin}/login?${new URLSearchParams({ entry, tenantCode: code, redirect: path })}`;
    const entries: Array<{
        key: string;
        audience: TenantEntry['audience'];
        path: string;
        app?: string;
        login?: string;
    }> = [
        { key: 'walletCustomer', audience: 'customer', path: `/wallet/${store}`, app: 'wallet' },
        { key: 'booking', audience: 'customer', path: `/book/${store}`, app: 'reservation_queue' },
        { key: 'staff', audience: 'staff', path: `/stores/${store}/staff`, login: 'staff' },
        { key: 'walletStaff', audience: 'staff', path: `/stores/${store}/wallet`, app: 'wallet', login: 'staff' },
        { key: 'payment', audience: 'staff', path: `/stores/${store}/payments`, app: 'payment', login: 'staff' },
        { key: 'ledger', audience: 'staff', path: `/stores/${store}/daily-ledger`, app: 'daily_ledger', login: 'staff' },
        { key: 'admin', audience: 'admin', path: `/stores/${store}/admin/profile`, login: 'tenant_admin' }
    ];
    return entries.map(e => {
        const product = e.app ? context.products.find(p => p.appKey === e.app) : null;
        const denialReason = !active ? (id.tenantStatus !== 'active' ? 'TENANT_INACTIVE' : 'STORE_INACTIVE')
            : e.app && !product?.available ? (product?.denialReason ?? 'TENANT_APP_NOT_ENABLED')
                : e.key === 'booking' && !context.publicBookingEnabled ? 'BOOKING_DISABLED' : null;
        return { key: e.key, audience: e.audience, url: e.login ? login(e.path, e.login) : origin + e.path, available: denialReason === null, denialReason };
    });
}
