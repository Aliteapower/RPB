import { onUnmounted, ref, watch, type Ref } from 'vue';
import type { EntryLinksContext } from './types';
export function useTenantEntries(store: Ref<string>) {
    const context = ref<EntryLinksContext | null>(null), loading = ref(false), error = ref('');
    let generation = 0;
    async function refresh() {
        const current = ++generation, id = store.value;
        context.value = null;
        error.value = '';
        loading.value = true;
        try {
            const response = await fetch(`/api/v1/stores/${encodeURIComponent(id)}/tenant-admin/entry-links`, { credentials: 'same-origin', headers: { Accept: 'application/json' } });
            const result = await response.json();
            if (!response.ok || !result.success)
                throw new Error(result.error?.messageKey ?? 'tenantEntryLinks.loadFailed');
            if (result.identity?.storeId !== id)
                throw new Error('tenantEntryLinks.scopeMismatch');
            if (current === generation)
                context.value = result;
        }
        catch (e) {
            if (current === generation)
                error.value = e instanceof Error ? e.message : 'tenantEntryLinks.loadFailed';
        }
        finally {
            if (current === generation)
                loading.value = false;
        }
    }
    watch(store, () => void refresh(), { immediate: true });
    onUnmounted(() => generation++);
    return { context, loading, error, refresh };
}
