import { publicBookingUrlForTenant } from '../../utils/hostContext';
import type { EntryLinksContext, TenantEntry } from './types';
import productLines from '../../main/resources/product-line-entries.json';
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
        titleKey?: string;
        descriptionKey?: string;
    }> = [
        { key: 'walletCustomer', audience: 'customer', path: `/wallet/${store}`, app: 'wallet' },
        { key: 'booking', audience: 'customer', path: `/book/${store}`, app: 'reservation_queue' },
        { key: 'wineCustomer', audience: 'customer', path: `/wallet/${store}/wine-storage`, app: 'wine_storage' },
        { key: 'staff', audience: 'staff', path: `/stores/${store}/staff`, login: 'staff' },
        ...productLines.filter(p=>p.appKey&&p.appKey!=='reservation_queue'&&p.value.startsWith('/stores/:storeId/')).map(p=>({
            key:({wallet:'walletStaff',daily_ledger:'ledger',wine_storage:'wineStaff'} as Record<string,string>)[p.appKey]||p.appKey,
            audience:'staff' as const,path:p.value.replace(':storeId',store),app:p.appKey,login:'staff',titleKey:p.labelKey,descriptionKey:p.descriptionKey
        })),
        { key: 'workforceClock', audience: 'staff', path: `/stores/${store}/workforce/clock`, app: 'workforce', login: 'staff' },
        { key: 'workforceAdmin', audience: 'admin', path: `/stores/${store}/workforce/management`, app: 'workforce', login: 'tenant_admin' },
        { key: 'admin', audience: 'admin', path: `/stores/${store}/admin/profile`, login: 'tenant_admin' }
    ];
    return entries.filter(e => !e.app || context.products.some(p => p.appKey===e.app && p.available)).filter(e => e.key!=='booking' || context.publicBookingEnabled).map(e => {
        const product = e.app ? context.products.find(p => p.appKey === e.app) : null;
        const denialReason = !active ? (id.tenantStatus !== 'active' ? 'TENANT_INACTIVE' : 'STORE_INACTIVE')
            : e.app && !product?.available ? (product?.denialReason ?? 'TENANT_APP_NOT_ENABLED')
                : e.key === 'booking' && !context.publicBookingEnabled ? 'BOOKING_DISABLED' : null;
        return { key: e.key, audience: e.audience, ...(e.titleKey?{titleKey:e.titleKey,descriptionKey:e.descriptionKey}:{}), url: e.login ? login(e.path, e.login) : origin + e.path, available: denialReason === null, denialReason };
    });
}
