<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { money, remaining, dateTime } from '../format'
import { useWalletTask } from '../useWalletTask'
import type { Account, Bill, CollectionConfirm, Context, PaymentCode } from '../types'
import WalletQrDisplay from './WalletQrDisplay.vue'
import WalletCollectionForm from './WalletCollectionForm.vue'
import WalletCustomerSummary from './WalletCustomerSummary.vue'
const props = defineProps<{ store: string; context: Context; account: Account | null; customerCode: string; canCreate: boolean; canConfirm: boolean }>()
const emit = defineEmits<{ changed: []; codeUsed: [] }>()
const { t } = useI18n(), { busy, error, run } = useWalletTask()
const amount = ref(''), rows = ref<Bill[]>([]), selected = ref<Bill | null>(null), code = ref<PaymentCode | null>(null), paynow = ref('')
const customerUrl = (value: string) => `${window.location.origin}/wallet/${encodeURIComponent(props.store)}#bill=${encodeURIComponent(value)}`
async function refresh() {
  rows.value = await walletApi(props.store).get<Bill[]>('/bills')
  if (selected.value) selected.value = rows.value.find(b => b.id === selected.value?.id) ?? selected.value
}
async function create() {
  const accountId = props.account?.id, scanned = props.customerCode; if (!accountId) return
  await run(async () => {
    let b = await walletApi(props.store).command<Bill>('/bills', { accountId, amount: amount.value })
    if (scanned) { b = await walletApi(props.store).post<Bill>(`/bills/${b.id}/bind-code`, { code: scanned }); emit('codeUsed') }
    selected.value = b; paynow.value = ''; code.value = await walletApi(props.store).post<PaymentCode>(`/bills/${b.id}/code`)
    await refresh(); emit('changed')
  })
}
async function showCode(b: Bill) { await run(async () => { code.value = await walletApi(props.store).post<PaymentCode>(`/bills/${b.id}/code`); selected.value = b; paynow.value = '' }) }
async function supplement(value: CollectionConfirm) {
  const id = selected.value?.id; if (!id) return
  await run(async () => { selected.value = await walletApi(props.store).command<Bill>(`/bills/${id}/collections`, value as unknown as Record<string, unknown>); code.value = null; paynow.value = ''; await refresh(); emit('changed') })
}
async function showPaynow(b: Bill) { await run(async () => { paynow.value = (await walletApi(props.store).get<{ payload: string }>(`/bills/${b.id}/paynow`)).payload; selected.value = b; code.value = null }) }
async function cancel(b: Bill) { await run(async () => { await walletApi(props.store).command(`/bills/${b.id}/cancel`); await refresh(); emit('changed') }) }
onMounted(() => { void run(refresh) })
watch(()=>props.account?.id,()=>{selected.value=null;code.value=null;paynow.value=''})
const timer = window.setInterval(() => { if (!busy.value && document.visibilityState === 'visible') void refresh().catch(() => {}) }, 5000)
onUnmounted(() => window.clearInterval(timer))
</script>
<template>
  <section>
    <form v-if="canCreate && context.transactionsEnabled && account" class="wallet-form" @submit.prevent="create"><h2>{{ t('wallet.charge') }}</h2>
      <p>{{ t('wallet.customerConfirm') }}</p><label>{{ t('wallet.amount') }}<input v-model="amount" type="number" inputmode="decimal" min="0.01" step="0.01" required></label>
      <button :disabled="busy">{{ t('wallet.createBill') }}</button>
    </form>
    <p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
    <div class="wallet-actions"><h2>{{ t('wallet.bills') }}</h2><button @click="run(refresh)">{{ t('wallet.refresh') }}</button></div>
    <p v-if="!rows.length">{{ t('wallet.empty') }}</p>
    <article v-for="b in rows" :key="b.id" class="wallet-record"><strong>{{ money(b.amountCents) }} · {{ t(`wallet.states.${b.status}`) }}</strong>
      <span v-if="b.closed">{{ t('wallet.states.closed') }} · {{ t(`wallet.states.${b.refundStatus}`) }}</span>
      <small>{{ dateTime(b.createdAt, context.store.timezone) }} · {{ b.id }} · {{ b.accountId.slice(0, 8) }}</small>
      <p>{{ t('wallet.walletPaid') }} {{ money(b.walletPaidCents) }} · {{ t('wallet.otherPaid') }} {{ money(b.externalPaidCents) }}</p>
      <strong v-if="!b.closed" :class="{ 'wallet-due': remaining(b) > 0 }">{{ remaining(b) > 0 ? `${t('wallet.stillDue')} ${money(remaining(b))}` : t('wallet.settled') }}</strong>
      <div v-if="!b.closed && remaining(b) > 0" class="wallet-actions">
        <button v-if="canCreate && !b.walletApplied && context.transactionsEnabled" :disabled="busy" @click="showCode(b)">{{ t('wallet.showBillCode') }}</button>
        <button v-if="canConfirm" :disabled="busy" @click="selected = b; code = null; paynow = ''">{{ t('wallet.confirmSupplement') }}</button>
        <button v-if="canConfirm && context.paynowConfigured && (context.transactionsEnabled || b.walletPaidCents + b.externalPaidCents > 0)" :disabled="busy" @click="showPaynow(b)">{{ t('wallet.showPaynow') }}</button>
        <button v-if="canCreate && b.walletPaidCents + b.externalPaidCents === 0" :disabled="busy" @click="cancel(b)">{{ t('wallet.cancel') }}</button>
      </div>
    </article>
    <section v-if="selected" class="wallet-card"><h3>{{ t('wallet.reference') }} {{ selected.id }}</h3>
      <p>{{ t('wallet.walletPaid') }} {{ money(selected.walletPaidCents) }} · <strong :class="{'wallet-due': remaining(selected) > 0}">{{ remaining(selected) > 0 ? `${t('wallet.stillDue')} ${money(remaining(selected))}` : t('wallet.settled') }}</strong></p>
      <template v-if="code && !selected.walletApplied"><WalletQrDisplay :value="customerUrl(code.code)" :expires-at="code.expiresAt" /><p>{{ t('wallet.customerConfirm') }}</p><a :href="customerUrl(code.code)" target="_blank" rel="noreferrer">{{ t('wallet.customerEntry') }}</a></template>
      <template v-if="paynow && remaining(selected) > 0"><WalletQrDisplay :value="paynow" /><p>{{ t('wallet.paynowHint') }}</p></template>
      <WalletCustomerSummary :store="store" :account-id="selected.accountId"><WalletCollectionForm v-if="canConfirm && !selected.closed && remaining(selected) > 0" :key="selected.id" :amount-cents="remaining(selected)" :channels="context.program?.channels ?? []" :paynow-configured="context.paynowConfigured" :busy="busy" @confirm="supplement" /></WalletCustomerSummary>
      <button @click="selected = null; code = null; paynow = ''">{{ t('wallet.close') }}</button>
    </section>
  </section>
</template>
