import { publicEntryOrigin } from '../../utils/hostContext';
import type { EntryLinksContext, TenantEntry } from './types';
import productLines from '../../main/resources/product-line-entries.json';
export function buildTenantEntries(context: EntryLinksContext, location: Pick<Location, 'protocol' | 'hostname' | 'port' | 'origin'> = window.location): TenantEntry[] {
    const id = context.identity, code = id.tenantCode.trim().toLowerCase();
    if (!/^(?!platform$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(code) || !/^https?:$/.test(location.protocol))
        throw new Error('Invalid tenant entry origin');
    const origin = publicEntryOrigin(location);
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
        { key: 'staff', audience: 'staff', path: `/stores/${store}/staff`, login: 'staff' },
        ...productEntryDefinitions(productLines).map(e => ({ ...e, path: e.path.replace(':storeId', store) })),
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

interface ProductEntry {
    appKey: string;
    value: string;
    labelKey: string;
    descriptionKey: string;
    distributionEntries?: Array<{ key: string; audience: string; path: string }>;
}

/** All product-specific distribution routes are owned by the shared code manifest. */
export function productEntryDefinitions(products: readonly ProductEntry[]) {
    return products.filter(p => p.appKey).flatMap(p => {
        const definitions = p.distributionEntries ?? [{ key: p.appKey, audience: 'staff', path: p.value }];
        return definitions.map(e => {
            if (!['customer', 'staff', 'admin'].includes(e.audience)
                || !/^\/(?:stores\/:storeId\/[a-z0-9/-]+|wallet\/:storeId(?:\/[a-z0-9/-]+)?|book\/:storeId)$/.test(e.path)) {
                throw new Error('Invalid product distribution entry');
            }
            const audience = e.audience as TenantEntry['audience'];
            return { ...e, audience, app: p.appKey, login: audience === 'customer' ? undefined : audience === 'admin' ? 'tenant_admin' : 'staff', titleKey: p.labelKey, descriptionKey: p.descriptionKey };
        });
    });
}
