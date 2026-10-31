<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import TenantAdminNav from '../../components/tenant-admin/TenantAdminNav.vue'
import { useTenantEntries } from './useTenantEntries'
import { buildTenantEntries } from './catalog'
import EntryLinkCard from './EntryLinkCard.vue'

const { t } = useI18n()
const route = useRoute()
const store = computed(() => String(route.params.storeId || ''))
const { context, loading, error, refresh } = useTenantEntries(store)
const entries = computed(() => {
  try {
    return context.value ? buildTenantEntries(context.value) : []
  } catch {
    return []
  }
})
const groups = ['customer', 'staff', 'admin'] as const
</script>
<template>
  <main class="tenant-shell">
    <TenantAdminNav />
    <section class="entry-workspace">
      <header class="entry-heading">
        <div>
          <span>{{ t('nav.tenant.title') }}</span>
          <h1>{{ t('tenantEntryLinks.title') }}</h1>
          <p>{{ t('tenantEntryLinks.subtitle') }}</p>
        </div>
        <button type="button" :disabled="loading" @click="refresh">
          {{ t('tenantEntryLinks.refresh') }}
        </button>
      </header>
      <p v-if="error" class="entry-error" role="alert">{{ t(error, t('tenantEntryLinks.loadFailed')) }}</p>
      <p v-if="loading" role="status" class="entry-loading">{{ t('tenantEntryLinks.loading') }}</p>
      <template v-if="context">
        <section class="entry-identity">
          <div>
            <span>{{ t('tenantEntryLinks.tenant') }}</span>
            <strong>{{ context.identity.tenantName }}</strong>
            <small>{{ context.identity.tenantCode }}</small>
          </div>
          <div>
            <span>{{ t('tenantEntryLinks.store') }}</span>
            <strong>{{ context.identity.storeName }}</strong>
            <small>{{ context.identity.storeCode }}</small>
          </div>
        </section>
        <p class="entry-hint">{{ t('tenantEntryLinks.hint') }}</p>
        <p v-if="!entries.length" class="entry-error" role="alert">{{ t('tenantEntryLinks.loadFailed') }}</p>
        <section v-for="group in groups" :key="group" class="entry-section">
          <h2>{{ t(`tenantEntryLinks.groups.${group}`) }}</h2>
          <div class="entry-grid">
            <EntryLinkCard
              v-for="entry in entries.filter(e => e.audience === group)"
              :key="`${context.identity.storeId}:${entry.key}`"
              :entry="entry"
              :identity="context.identity"
            />
          </div>
        </section>
      </template>
    </section>
  </main>
</template>
<style scoped>
.tenant-shell{min-height:100dvh;display:grid;grid-template-columns:230px minmax(0,1fr);background:#f4f7f9;color:#17323d}.entry-workspace{min-width:0;padding:32px 36px 60px;max-width:1480px;width:100%;box-sizing:border-box}.entry-heading{display:flex;justify-content:space-between;gap:20px;align-items:start;margin-bottom:24px}.entry-heading span{font-size:12px;color:#6f858f}.entry-heading h1{font-size:27px;margin:7px 0 10px}.entry-heading p{color:#6c808c;font-size:14px;line-height:1.7;margin:0}.entry-heading button{flex-shrink:0;background:#fff;border:1px solid #ccdbe2;color:#365f70;padding:10px 15px;border-radius:8px;font:inherit;font-size:13px;cursor:pointer}.entry-heading button:disabled{opacity:.5}.entry-identity{display:flex;gap:48px;padding:20px 24px;border-radius:12px;background:#e7f0ee;border:1px solid #d3e3de}.entry-identity div{display:grid;gap:5px;min-width:0}.entry-identity span,.entry-identity small{font-size:12px;color:#617c79}.entry-identity strong{font-size:17px;overflow-wrap:anywhere}.entry-hint{font-size:12px;color:#778b94;line-height:1.8;margin:14px 0 26px}.entry-section h2{font-size:17px;font-weight:700;margin:26px 0 15px}.entry-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:18px;align-items:start;max-width:1240px}.entry-error{padding:14px;background:#fff1ed;border:1px solid #f0d1c7;color:#a14d36;border-radius:8px;line-height:1.7}.entry-loading{color:#657a85;padding:24px 0}@media(min-width:1300px){.entry-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:980px){.tenant-shell{grid-template-columns:1fr}.entry-workspace{padding:24px}.entry-identity{gap:24px}}@media(max-width:550px){.entry-workspace{padding:18px 14px 36px}.entry-heading{flex-wrap:wrap;gap:14px}.entry-heading h1{font-size:23px}.entry-identity{padding:16px;display:grid;gap:18px}.entry-grid{grid-template-columns:minmax(0,1fr)}}
/* The global language switcher is fixed at the top right. Keep the refresh action below it. */
.entry-heading > button { margin-top: 32px; }
@media (max-width: 550px) {
  .entry-heading > button { margin-top: 0; }
}
</style>
