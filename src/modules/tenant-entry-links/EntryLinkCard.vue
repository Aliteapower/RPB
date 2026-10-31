<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import type { EntryIdentity, TenantEntry } from './types';
import TenantNameQr from './TenantNameQr.vue';
const props = defineProps<{
    entry: TenantEntry;
    identity: EntryIdentity;
}>(), { t,te } = useI18n(), feedback = ref('');
const title=computed(()=>{const key=`tenantEntryLinks.entries.${props.entry.key}.title`;return t(te(key)?key:props.entry.titleKey||key)});
const description=computed(()=>{const key=`tenantEntryLinks.entries.${props.entry.key}.description`;return t(te(key)?key:props.entry.descriptionKey||key)});
let generation = 0;
watch(() => props.entry.url, () => { generation++; feedback.value = ''; });
onUnmounted(() => generation++);
async function copy() { const current = generation; try {
    await navigator.clipboard.writeText(props.entry.url);
    if (current === generation)
        feedback.value = t('tenantEntryLinks.copied');
}
catch {
    if (current === generation)
        feedback.value = t('tenantEntryLinks.copyManual');
} }
</script>
<template>
  <article class="entry-card" :class="{ 'is-unavailable': !entry.available }">
    <header>
      <div>
        <span class="entry-audience">{{ t(`tenantEntryLinks.audiences.${entry.audience}`) }}</span>
        <h3>{{ title }}</h3>
      </div>
      <span class="entry-badge" :class="{ 'is-active': entry.available }">
        {{ t(entry.available ? 'tenantEntryLinks.available' : 'tenantEntryLinks.unavailable') }}
      </span>
    </header>
    <p class="entry-description">{{ description }}</p>
    <template v-if="entry.available">
      <label class="entry-url">
        <span class="sr-only">{{ title }} URL</span>
        <input :value="entry.url" readonly spellcheck="false">
      </label>
      <div class="entry-actions">
        <button type="button" @click="copy">{{ t('tenantEntryLinks.copy') }}</button>
        <a :href="entry.url" target="_blank" rel="noopener noreferrer">{{ t('tenantEntryLinks.open') }}</a>
      </div>
      <p v-if="feedback" role="status" class="entry-feedback">{{ feedback }}</p>
      <TenantNameQr
        :value="entry.url"
        :tenant-name="identity.tenantName"
        :caption="`${identity.storeName} · ${title}`"
        :file-name="`${identity.tenantCode}-${identity.storeCode}-${entry.key}.png`"
      />
    </template>
    <div v-else class="entry-disabled">
      <strong>{{ t(`tenantEntryLinks.reasons.${entry.denialReason}`, t('tenantEntryLinks.unavailable')) }}</strong>
      <p>{{ t('tenantEntryLinks.disabledHint') }}</p>
    </div>
  </article>
</template>
<style scoped>
.entry-card{display:grid;align-content:start;gap:12px;padding:20px;border:1px solid #dce5e9;border-radius:16px;background:#fff;min-width:0}.entry-card header{display:flex;justify-content:space-between;align-items:center;gap:8px}.entry-audience{font-size:11px;letter-spacing:.05em;color:#657b88}.entry-card h3{font-size:18px;color:#18333d;margin:5px 0 0}.entry-badge{border-radius:20px;background:#f1f3f5;color:#78858e;padding:5px 9px;white-space:nowrap;font-size:11px}.entry-badge.is-active{background:#e4f3ed;color:#14715e}.entry-description{font-size:13px;line-height:1.7;color:#6a7c86;min-height:44px;margin:0}.entry-url input{width:100%;min-width:0;padding:10px;border:1px solid #d7e2e7;border-radius:8px;font-size:12px;color:#506977;background:#f7fafb;box-sizing:border-box}.entry-actions{display:flex;gap:8px}.entry-actions>*{flex:1;text-align:center;padding:9px 6px;border-radius:8px;border:1px solid #cbdcd9;background:white;color:#176b5e;text-decoration:none;font:inherit;font-size:13px;font-weight:650;cursor:pointer}.entry-actions>button{background:#176b5e;color:white;border-color:#176b5e}.entry-feedback{margin:0;color:#176b5e;font-size:12px}.entry-disabled{display:grid;align-content:center;min-height:180px;border-radius:10px;background:#f6f8f9;padding:24px;color:#7e8d94}.entry-disabled strong{font-size:15px}.entry-disabled p{font-size:13px;line-height:1.7}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}
</style>
