<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import { useWalletHistory } from '../useWalletHistory'
import { billStage, staffWalletPath } from '../navigation'
import type { Bill, Topup } from '../types'
import WalletRecordRow from '../components/WalletRecordRow.vue'
const { t } = useI18n(), route = useRoute(), { store, context } = useStaffWallet()
const topups = computed(() => route.query.kind === 'topup'), resource = computed(() => topups.value ? '/topups' : '/bills')
const status = computed(() => ['pending','completed','closed'].includes(String(route.query.status)) ? String(route.query.status) : 'all')
const { rows, more, loaded, busy, error, refresh, loadMore } = useWalletHistory<Bill | Topup>(store, resource, status)
const link = (filter: string, kind = topups.value ? 'topup' : 'bill') => ({ path: staffWalletPath(store.value,'bills'), query: { kind, status: filter } })
const stage = (row: Bill | Topup) => 'channel' in row ? row.status === 'credited' ? 'paid' : row.status === 'cancelled' ? 'closed' : 'pending' : billStage(row)
</script>
<template><div class="wallet-page-heading"><h2>{{ t('wallet.bills') }}</h2><button class="wallet-link-button" :disabled="busy" @click="refresh">{{ t('wallet.refresh') }}</button></div>
  <nav class="wallet-segmented" :aria-label="t('wallet.ui.recordType')"><RouterLink :to="link('all','bill')" :class="{'is-current':!topups}">{{ t('wallet.ui.collectRecords') }}</RouterLink><RouterLink :to="link('all','topup')" :class="{'is-current':topups}">{{ t('wallet.pendingTopups') }}</RouterLink></nav>
  <nav class="wallet-filter-tabs" :aria-label="t('wallet.ui.filterStatus')"><RouterLink v-for="s in ['all','pending','completed','closed']" :key="s" :to="link(s)" :class="{'is-current':status===s}">{{ t(`wallet.ui.filters.${s}`) }}</RouterLink></nav>
  <p v-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-if="!loaded && busy" role="status" class="wallet-empty">{{ t('wallet.loading') }}</p>
  <section class="wallet-list"><WalletRecordRow v-for="r in rows" :key="r.id" :to="staffWalletPath(store,`${topups?'topups':'bills'}/${r.id}`)" :title="topups ? t(`wallet.channels.${(r as Topup).channel}`) : t('wallet.ui.walletCollection')" :cents="r.amountCents" :status="stage(r)" :time="r.createdAt" :timezone="context?.store.timezone ?? 'Asia/Singapore'" /></section>
  <p v-if="loaded && !rows.length" class="wallet-empty">{{ t('wallet.ui.noMatchingRecords') }}</p><button v-if="more" class="wallet-block" :disabled="busy" @click="loadMore">{{ t('wallet.loadMore') }}</button>
</template>
