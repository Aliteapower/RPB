<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import type {MeAppEntry} from '../../types/meApps'
defineProps<{entries:MeAppEntry[]}>()
const {t}=useI18n()
</script>
<template><section v-if="entries.length" class="product-history-links" :aria-label="t('tenantProducts.historyTitle')"><h3>{{t('tenantProducts.historyTitle')}}</h3><RouterLink v-for="entry in entries" :key="entry.appKey" :to="entry.entryRoute">{{t(`tenantProducts.names.${entry.appKey}`)}} · {{t('tenantProducts.historyOnly')}}</RouterLink></section></template>
<style scoped>.product-history-links{display:grid;gap:8px;padding:14px 12px;border:1px solid #dbe3ea;border-radius:8px;background:#fff}.product-history-links h3{margin:0;font-size:14px;color:#64748b}.product-history-links a{color:#0f766e;text-decoration:none;padding:6px 0}</style>
