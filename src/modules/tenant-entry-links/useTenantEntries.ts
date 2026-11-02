import { onUnmounted, ref, watch, type Ref } from 'vue';
import type { EntryLinksContext } from './types';
import { useVisiblePageRefresh } from '../../composables/useVisiblePageRefresh';
export function useTenantEntries(store: Ref<string>) {
    const context = ref<EntryLinksContext | null>(null), loading = ref(false), error = ref('');
    let generation = 0, pendingStore: string | null = null, disposed = false;
    async function refresh() {
        const id = store.value;
        if (disposed || pendingStore === id || !id) return;
        const current = ++generation;
        const isCurrent = () => !disposed && current === generation && store.value === id;
        pendingStore = id;
        error.value = '';
        loading.value = true;
        try {
            const response = await fetch(`/api/v1/stores/${encodeURIComponent(id)}/tenant-admin/entry-links`, { credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json' } });
            const result = await response.json();
            if (!response.ok || !result.success)
                throw new Error(result.error?.messageKey ?? 'tenantEntryLinks.loadFailed');
            if (result.identity?.storeId !== id)
                throw new Error('tenantEntryLinks.scopeMismatch');
            if (isCurrent())
                context.value = result;
        }
        catch (e) {
            if (isCurrent()) {
                context.value = null;
                error.value = e instanceof Error ? e.message : 'tenantEntryLinks.loadFailed';
            }
        }
        finally {
            if (isCurrent()) {
                pendingStore = null;
                loading.value = false;
            }
        }
    }
    watch(store, () => {
        generation++;
        pendingStore = null;
        context.value = null;
        error.value = '';
        loading.value = false;
        void refresh();
    }, { immediate: true, flush: 'sync' });
    useVisiblePageRefresh(refresh, () => !!store.value && !loading.value);
    onUnmounted(() => { disposed = true; generation++; });
    return { context, loading, error, refresh };
}
