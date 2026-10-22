<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthSessionStore } from '../../stores/authSession'
import StoreSwitcher from '../../components/store/StoreSwitcher.vue'
import { walletApi } from './api'
import { money } from './format'
import { useWalletTask } from './useWalletTask'
import type { Account, Context, Customer } from './types'
import WalletTopupPanel from './components/WalletTopupPanel.vue'
import WalletCheckoutPanel from './components/WalletCheckoutPanel.vue'
import WalletLedgerPanel from './components/WalletLedgerPanel.vue'
import WalletSettingsPanel from './components/WalletSettingsPanel.vue'
import WalletReportPanel from './components/WalletReportPanel.vue'
import WalletQrScanner from './components/WalletQrScanner.vue'
import WalletExceptionsPanel from './components/WalletExceptionsPanel.vue'
import './wallet.css'
const route = useRoute(), auth = useAuthSessionStore(), { t } = useI18n(), { busy, error, run } = useWalletTask()
const store = computed(() => String(route.params.storeId)), context = ref<Context | null>(null), tab = ref('topup')
const query = ref(''), customers = ref<Customer[]>([]), customer = ref<Customer | null>(null), account = ref<Account | null>(null), scannedCode = ref(''), scanning = ref(false)
const can = (permission: string) => auth.user?.permissions.includes(permission) === true
let generation = 0
async function refresh() {
  const current = generation, id = store.value
  const result = await walletApi(id).get<Context>('/context')
  if (current !== generation) return
  context.value = result
  if (!result.program && can('wallet.settings.manage')) tab.value = 'settings'
  if (account.value) { const selectedId=account.value.id, latest = await walletApi(id).get<Account>(`/accounts/${selectedId}`); if(current === generation && account.value?.id===selectedId) account.value = latest }
}
async function search() { await run(async () => { customers.value = await walletApi(store.value).get<Customer[]>(`/customers?query=${encodeURIComponent(query.value)}`) }) }
async function choose(value: Customer) {
  await run(async () => {
    const id = store.value, current = generation; const existing = await walletApi(id).get<Account[]>(`/customers/${value.id}/accounts`)
    if(current !== generation) return
    let selected = existing[0] ?? null
    if (!selected && context.value?.transactionsEnabled && can('wallet.topup.create')) selected = await walletApi(id).command<Account>('/accounts', { customerId: value.id })
    if(current !== generation) return
    customer.value = value; account.value = selected; scannedCode.value = ''
  })
}
async function scan(value: string) {
  await run(async () => {
    const current = generation, result = await walletApi(store.value).post<{ account: Account; customer: Customer }>('/payment-codes/resolve', { code: value })
    if(current !== generation) return
    account.value = result.account; customer.value = result.customer; scannedCode.value = value; scanning.value = false; tab.value = 'charge'
  })
}
watch(store, () => {
  generation++; context.value = null; account.value = null; customer.value = null; customers.value = []; scannedCode.value = ''; scanning.value = false
  void refresh().catch(e => { error.value = e instanceof Error ? e.message : String(e) })
}, { immediate: true })
const refreshTimer = window.setInterval(() => { if(!busy.value && document.visibilityState === 'visible') void refresh().catch(e => {error.value = e instanceof Error ? e.message : String(e)}) }, 5000)
onUnmounted(() => { window.clearInterval(refreshTimer); generation++ })
</script>
<template>
  <main class="wallet-shell">
    <header class="wallet-header"><img v-if="context?.store.logoMediaAssetId" class="wallet-logo" :src="`/api/v1/stores/${store}/wallet/logo/${context.store.logoMediaAssetId}`" alt=""><div><h1>{{ t('wallet.entry') }}</h1><p>{{ context?.store.storeName }}</p><small>{{ t('wallet.issuer') }}：{{ context?.store.operatingEntityName || '-' }}</small></div><StoreSwitcher surface="staff" /><div class="wallet-actions"><RouterLink :to="`/stores/${store}/staff`">{{ t('nav.staff.home') }}</RouterLink><a :href="`/wallet/${store}`" target="_blank" rel="noreferrer">{{ t('wallet.customerEntry') }}</a><button @click="run(refresh)">{{ t('wallet.refresh') }}</button></div></header>
    <p v-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-if="busy" role="status">{{ t('wallet.busy') }}</p>
    <template v-if="context">
      <p v-if="!context.transactionsEnabled" class="wallet-warning" role="status">{{ context.program ? t('wallet.expired') : t('wallet.noProgram') }}</p>
      <section class="wallet-card"><form class="wallet-actions" @submit.prevent="search"><label>{{ t('wallet.customer') }}<input v-model="query" :placeholder="t('wallet.search')" maxlength="160"></label><button :disabled="busy">{{ t('wallet.choose') }}</button><button v-if="context.transactionsEnabled && can('wallet.charge.create')" type="button" @click="scanning = !scanning">{{ t('wallet.scanCustomer') }}</button></form>
        <WalletQrScanner v-if="scanning" @code="scan" /><p>{{ t('wallet.authenticatedOnly') }}</p>
        <div v-if="customers.length" class="wallet-customer-results"><button v-for="c in customers" :key="c.id" :disabled="busy" @click="choose(c)">{{ c.displayName || c.customerCode }} · {{ c.email || c.phone }}</button></div>
        <template v-if="customer"><h2>{{ customer.displayName || customer.customerCode }}</h2><p>{{ customer.email || customer.phone }}</p></template>
        <div v-if="account" class="wallet-balances"><strong>{{ t('wallet.balance') }} {{ money(account.balanceCents) }}</strong><span>{{ t('wallet.available') }} {{ money(account.balanceCents - account.reservedCents) }}</span><span>{{ t('wallet.reserved') }} {{ money(account.reservedCents) }}</span></div><p v-else>{{ t('wallet.noCustomer') }}</p>
      </section>
      <nav class="wallet-tabs"><button v-for="key in ['topup', 'charge', 'history', 'settings', ...(can('wallet.reconcile.view') ? ['report'] : [])]" :key="key" :aria-current="tab === key ? 'page' : undefined" @click="tab = key">{{ t(`wallet.${key}`) }}</button></nav>
      <WalletTopupPanel v-if="tab === 'topup' && context.program" :key="store" :store="store" :context="context" :account="account" :can-create="can('wallet.topup.create')" :can-confirm="can('wallet.collection.confirm')" @changed="run(refresh)" />
      <WalletCheckoutPanel v-if="tab === 'charge' && context.program" :key="store" :store="store" :context="context" :account="account" :customer-code="scannedCode" :can-create="can('wallet.charge.create')" :can-confirm="can('wallet.collection.confirm')" @code-used="scannedCode = ''" @changed="run(refresh)" />
      <WalletLedgerPanel v-if="tab === 'history' && context.program" :key="store" :store="store" :account="account" :timezone="context.store.timezone" :can-refund="can('wallet.refund.manage')" @changed="run(refresh)" />
      <WalletExceptionsPanel v-if="tab === 'history' && context.program && can('wallet.refund.manage')" :key="store" :store="store" :paynow-configured="context.paynowConfigured" />
      <WalletSettingsPanel v-if="tab === 'settings'" :key="store" :store="store" :context="context" :allowed="can('wallet.settings.manage')" @changed="run(refresh)" />
      <WalletReportPanel v-if="tab === 'report' && can('wallet.reconcile.view') && context.program" :key="store" :store="store" />
    </template>
  </main>
</template>
