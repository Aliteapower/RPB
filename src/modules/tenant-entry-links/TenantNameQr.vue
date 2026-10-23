<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { renderTenantEntryQr } from './nameQr';
import { safeQrDownloadFileName } from '../../utils/qrCodeRenderer';
const props = defineProps<{
    value: string;
    tenantName: string;
    caption: string;
    fileName: string;
}>(), { t } = useI18n();
const source = ref(''), error = ref(false), rendering = ref(false);
let generation = 0;
watch(() => [props.value, props.tenantName, props.caption], async () => {
    const current = ++generation;
    source.value = '';
    error.value = false;
    rendering.value = true;
    try {
        const image = await renderTenantEntryQr(props.value, props.tenantName, props.caption);
        if (current === generation)
            source.value = image;
    }
    catch {
        if (current === generation)
            error.value = true;
    }
    finally {
        if (current === generation)
            rendering.value = false;
    }
}, { immediate: true });
onUnmounted(() => generation++);
</script>
<template>
  <div class="entry-qr">
    <img v-if="source" :src="source" :alt="`${tenantName} · ${caption}`">
    <p v-else :role="error ? 'alert' : 'status'">
      {{ t(error ? 'tenantEntryLinks.qrFailed' : 'tenantEntryLinks.generating') }}
    </p>
  </div>
  <a v-if="source" class="entry-download" :href="source" :download="safeQrDownloadFileName(fileName)">
    {{ t('tenantEntryLinks.download') }}
  </a>
  <button v-else class="entry-download" disabled>
    {{ t(rendering ? 'tenantEntryLinks.generating' : 'tenantEntryLinks.download') }}
  </button>
</template>
<style scoped>
.entry-qr{display:grid;place-items:center;min-height:330px;background:#fff;border:1px solid #e3e9ed;border-radius:12px;padding:4px}.entry-qr img{width:min(100%,300px);height:auto;display:block}.entry-qr p{padding:20px;color:#667784;font-size:13px}.entry-download{display:block;text-align:center;text-decoration:none;background:#edf5f3;color:#13665a;border:1px solid #cee1dc;border-radius:8px;padding:10px;font:inherit;font-size:13px;font-weight:700}.entry-download:disabled{color:#8b999f;background:#f3f5f6}
</style>
