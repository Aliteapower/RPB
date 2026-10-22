<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { walletApi, WalletApiError } from './api'
import { money, dateTime, billCode } from './format'
import { useWalletTask } from './useWalletTask'
import type { Bill, CustomerHome, Entry, PaymentCode } from './types'
import WalletCustomerLogin from './components/WalletCustomerLogin.vue'
import WalletCustomerBill from './components/WalletCustomerBill.vue'
import WalletQrDisplay from './components/WalletQrDisplay.vue'
import WalletQrScanner from './components/WalletQrScanner.vue'
import './wallet.css'
const route = useRoute(), router = useRouter(), { t } = useI18n(), { busy, error, run } = useWalletTask()
const store = computed(() => String(route.params.storeId)), home = ref<CustomerHome | null>(null), needsLogin = ref(false), code = ref<PaymentCode | null>(null), entries = ref<Entry[]>([]), scanning = ref(false), scannedBill = ref<Bill | null>(null)
const account = computed(() => home.value?.accounts[0] ?? null)
const receipt = ref<Bill | null>(null)
const more = ref(false)
let generation = 0, loadSequence = 0
async function load() {
  const id = store.value, current = generation, sequence = ++loadSequence
  let homeRead = false
  try {
    const result = await walletApi(id, true).get<CustomerHome>('/me')
    if (generation !== current || sequence!==loadSequence) return
    homeRead = true
    if(home.value?.accounts[0]?.id!==result.accounts[0]?.id) {entries.value=[];more.value=false;receipt.value=null;code.value=null;scannedBill.value=null}
    home.value = result; needsLogin.value = false
    if (account.value) { const accountId=account.value.id, resultEntries = await walletApi(id, true).get<Entry[]>(`/accounts/${accountId}/entries`); if(generation !== current || sequence!==loadSequence || account.value?.id!==accountId) return; if(entries.value.length<=100 || entries.value[0]?.accountId!==accountId)more.value=resultEntries.length===100; entries.value = [...resultEntries,...entries.value.filter(e=>e.accountId===accountId && !resultEntries.some(fresh=>fresh.id===e.id))] }
    else {entries.value=[];more.value=false}
    if (route.hash.startsWith('#receipt=')) { const resultReceipt = await walletApi(id, true).get<Bill>(`/bills/${encodeURIComponent(new URLSearchParams(route.hash.slice(1)).get('receipt') ?? '')}`); if(generation !== current || sequence!==loadSequence) return; receipt.value = resultReceipt }
    if (route.hash.startsWith('#bill=')) await scan(new URLSearchParams(route.hash.slice(1)).get('bill') ?? '')
  } catch (e) {
    if (generation !== current || sequence!==loadSequence) return
    if (e instanceof WalletApiError && e.status === 401) { needsLogin.value = true; home.value = null; code.value = null;receipt.value=null;entries.value=[];more.value=false }
    else { if(!homeRead && e instanceof WalletApiError && [403,404].includes(e.status)) {home.value=null;receipt.value=null;entries.value=[];code.value=null;scannedBill.value=null} error.value = e instanceof Error ? e.message : String(e) }
  }
}
async function issue() { const id = account.value?.id; if (!id) return; await run(async () => { code.value = await walletApi(store.value, true).post<PaymentCode>('/payment-codes', { accountId: id }) }) }
async function authenticated() { needsLogin.value = false; error.value = ''; await load() }
async function scan(value: string) {
  try {
    const decoded = billCode(value)
    if (decoded.store && decoded.store !== store.value) { await router.push(`/wallet/${decoded.store}#bill=${encodeURIComponent(decoded.code)}`); return }
    const current = generation, resultBill = await walletApi(store.value, true).post<Bill>('/bill-code', { code: decoded.code })
    if(current !== generation) return
    scannedBill.value = resultBill
    scanning.value = false; if (route.hash) await router.replace({ hash: '' })
  } catch (e) { error.value = e instanceof WalletApiError ? e.message : t('wallet.invalidCode') }
}
async function changed(bill: Bill) { receipt.value = bill; scannedBill.value = null; code.value = null; await router.replace({ hash: `#receipt=${bill.id}` }); await load() }
async function loadMore() { const id=account.value?.id,current=generation;if(!id)return;await run(async()=>{const next=await walletApi(store.value,true).get<Entry[]>(`/accounts/${id}/entries?offset=${entries.value.length}`);if(current!==generation)return;entries.value.push(...next.filter(e=>!entries.value.some(old=>old.id===e.id)));more.value=next.length===100}) }
watch(store, () => { generation++; home.value = null; entries.value = []; code.value = null; scannedBill.value = null; receipt.value = null; scanning.value = false; error.value = ''; void load() }, { immediate: true })
watch(() => route.hash, value => { if(!home.value) return; if (value.startsWith('#bill=')) void scan(new URLSearchParams(value.slice(1)).get('bill') ?? ''); else if(value.startsWith('#receipt=')) void load() })
const timer = window.setInterval(() => { if (!busy.value && !needsLogin.value && document.visibilityState === 'visible') void load() }, 5000)
onUnmounted(() => { window.clearInterval(timer); generation++ })
</script>
<template>
  <main class="wallet-shell wallet-customer"><header class="wallet-header"><img v-if="home?.context.store.logoMediaAssetId" class="wallet-logo" :src="`/api/v1/public/stores/${store}/wallet/logo/${home.context.store.logoMediaAssetId}`" alt=""><div><h1>{{ t('wallet.customerWallet') }}</h1><p>{{ home?.context.store.storeName }}</p><small v-if="home">{{ t('wallet.issuer') }}：{{ home.context.store.operatingEntityName }}</small></div><button :disabled="busy" @click="load">{{ t('wallet.refresh') }}</button></header>
    <p v-if="error" class="wallet-error" role="alert">{{ error }}</p><WalletCustomerLogin v-if="needsLogin" :key="store" :store="store" @authenticated="authenticated" />
    <template v-if="home"><p v-if="home.context.store.contactPhone || home.context.store.address">{{ home.context.store.contactPhone }} · {{ home.context.store.address }}</p><p v-if="!home.context.transactionsEnabled" class="wallet-warning">{{ t('wallet.expired') }}</p>
      <section v-if="account" class="wallet-card"><div class="wallet-balances"><strong>{{ t('wallet.balance') }} {{ money(account.balanceCents) }}</strong><span>{{ t('wallet.available') }} {{ money(account.balanceCents - account.reservedCents) }}</span><span>{{ t('wallet.reserved') }} {{ money(account.reservedCents) }}</span></div>
        <div v-if="home.context.transactionsEnabled" class="wallet-actions"><button :disabled="busy" @click="issue">{{ t('wallet.showMyCode') }}</button><button @click="scanning = !scanning">{{ t('wallet.scanBill') }}</button></div>
        <template v-if="code"><WalletQrDisplay :value="code.code" :expires-at="code.expiresAt" /><p>{{ t('wallet.cashierScanned') }}</p><button :disabled="busy" @click="issue">{{ t('wallet.regenerate') }}</button></template>
      </section><p v-else>{{ t('wallet.empty') }}</p>
      <WalletQrScanner v-if="scanning" @code="scan" />
      <section v-if="receipt" class="wallet-card" role="status"><h2>{{ t('wallet.paymentDone') }}</h2><p>{{ t('wallet.reference') }} {{ receipt.id }}</p><p>{{ t('wallet.walletPaid') }} {{ money(receipt.walletPaidCents) }} · {{ t('wallet.otherPaid') }} {{ money(receipt.externalPaidCents) }}</p><strong :class="{'wallet-due': !receipt.closed && receipt.amountCents - receipt.walletPaidCents - receipt.externalPaidCents > 0}">{{ receipt.closed ? t('wallet.states.closed') : receipt.amountCents - receipt.walletPaidCents - receipt.externalPaidCents > 0 ? `${t('wallet.stillDue')} ${money(receipt.amountCents - receipt.walletPaidCents - receipt.externalPaidCents)}` : t('wallet.settled') }}</strong><span v-if="receipt.refundStatus !== 'none'"> · {{ t(`wallet.states.${receipt.refundStatus}`) }}</span><button @click="receipt = null; router.replace({hash: ''})">{{ t('wallet.close') }}</button></section>
      <WalletCustomerBill v-if="scannedBill && !home.pendingBills.some(b => b.id === scannedBill?.id)" :key="scannedBill.id" :store="store" :bill="scannedBill" :context="home.context" @changed="changed" />
      <WalletCustomerBill v-for="b in home.pendingBills" :key="b.id" :store="store" :bill="b" :context="home.context" @changed="changed" />
      <h2>{{ t('wallet.history') }}</h2><p v-if="!entries.length">{{ t('wallet.empty') }}</p><article v-for="e in entries" :key="e.id" class="wallet-record"><strong>{{ t(`wallet.states.${e.kind}`) }} {{ money(e.amountCents) }}</strong><span>{{ t('wallet.balance') }} {{ money(e.afterCents) }}</span><small>{{ dateTime(e.createdAt, home.context.store.timezone) }} · {{ e.sourceId }}</small><a v-if="e.kind === 'bill_debit'" :href="`/wallet/${e.storeId}#receipt=${e.sourceId}`">{{ t('wallet.reference') }}</a></article>
      <button v-if="more" :disabled="busy" @click="loadMore">{{ t('wallet.loadMore') }}</button>
    </template>
  </main>
</template>
