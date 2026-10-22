<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { money, dateTime } from '../format'
import { useWalletTask } from '../useWalletTask'
import type { Account, Channel, CollectionConfirm, Context, Customer, Topup } from '../types'
import WalletCollectionForm from './WalletCollectionForm.vue'
import WalletQrDisplay from './WalletQrDisplay.vue'
const props = defineProps<{ store: string; context: Context; account: Account | null; canCreate: boolean; canConfirm: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const { t } = useI18n(), { busy, error, run } = useWalletTask()
const amount = ref(''), channel = ref<Channel>('cash'), rows = ref<Topup[]>([]), selected = ref<Topup | null>(null), qr = ref('')
const selectedCustomer = ref<Customer | null>(null)
watch(() => props.context.program?.channels, channels => { if(channels?.length && !channels.includes(channel.value)) channel.value = channels[0]! }, {immediate:true})
async function select(row: Topup) { const a = await walletApi(props.store).get<Account>(`/accounts/${row.accountId}`); selectedCustomer.value = await walletApi(props.store).get<Customer>(`/customers/${a.customerId}`); selected.value = row; qr.value = '' }
async function refresh() { rows.value = await walletApi(props.store).get<Topup[]>('/topups') }
async function create() {
  const accountId = props.account?.id; if (!accountId) return
  await run(async () => { const row = await walletApi(props.store).command<Topup>('/topups', { accountId, amount: amount.value, channel: channel.value }); await select(row); await refresh(); emit('changed') })
}
async function confirm(value: CollectionConfirm) {
  const id = selected.value?.id; if (!id) return
  await run(async () => { await walletApi(props.store).command(`/topups/${id}/confirm`, value as unknown as Record<string, unknown>); selected.value = null; qr.value = ''; await refresh(); emit('changed') })
}
async function showQr(row: Topup) { await run(async () => { await select(row); const result = await walletApi(props.store).get<{ payload: string }>(`/topups/${row.id}/paynow`); qr.value = result.payload }) }
async function cancel(row: Topup) { await run(async () => { await walletApi(props.store).command(`/topups/${row.id}/cancel`); selected.value = null; await refresh(); emit('changed') }) }
watch(() => props.account?.id, () => { selected.value = null; qr.value = '' })
onMounted(() => { void run(refresh) })
</script>
<template>
  <section>
    <form v-if="canCreate && context.transactionsEnabled && account" class="wallet-form" @submit.prevent="create">
      <h2>{{ t('wallet.topup') }}</h2><p>{{ t('wallet.offline') }}</p>
      <label>{{ t('wallet.amount') }}<input v-model="amount" inputmode="decimal" type="number" min="0.01" step="0.01" required></label>
      <label>{{ t('wallet.channel') }}<select v-model="channel"><option v-for="c in context.program?.channels" :key="c" :value="c" :disabled="c === 'paynow' && !context.paynowConfigured">{{ t(`wallet.channels.${c}`) }}</option></select></label>
      <button :disabled="busy || (channel === 'paynow' && !context.paynowConfigured)">{{ t('wallet.createTopup') }}</button>
    </form>
    <p v-if="!context.paynowConfigured">{{ t('wallet.paynowMissing') }}</p>
    <p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
    <div class="wallet-actions"><h2>{{ t('wallet.pendingTopups') }}</h2><button @click="run(refresh)">{{ t('wallet.refresh') }}</button></div>
    <p v-if="!rows.length">{{ t('wallet.empty') }}</p>
    <article v-for="row in rows" :key="row.id" class="wallet-record">
      <strong>{{ money(row.amountCents) }} · {{ t(`wallet.channels.${row.channel}`) }}</strong><span>{{ t(`wallet.states.${row.status}`) }}</span>
      <small>{{ dateTime(row.createdAt, context.store.timezone) }} · {{ row.id }} · {{ t('wallet.customer') }} {{ row.accountId.slice(0, 8) }}</small>
      <div v-if="row.status === 'pending_collection'" class="wallet-actions">
        <button v-if="canConfirm" :disabled="busy" @click="run(() => select(row))">{{ t('wallet.confirmCollection') }}</button>
        <button v-if="row.channel === 'paynow' && context.transactionsEnabled" :disabled="busy" @click="showQr(row)">{{ t('wallet.showPaynow') }}</button>
        <button v-if="canCreate" :disabled="busy" @click="cancel(row)">{{ t('wallet.cancel') }}</button>
      </div>
    </article>
    <section v-if="selected" class="wallet-card"><h3>{{ t('wallet.reference') }} {{ selected.id }}</h3><p v-if="selectedCustomer">{{ t('wallet.customer') }}：{{ selectedCustomer.displayName || selectedCustomer.customerCode }} · {{ selectedCustomer.email || selectedCustomer.phone }}</p>
      <template v-if="qr"><WalletQrDisplay :value="qr" /><p>{{ t('wallet.paynowHint') }}</p></template>
      <WalletCollectionForm v-if="canConfirm && selected.status === 'pending_collection'" :key="selected.id" :amount-cents="selected.amountCents" :fixed-channel="selected.channel" :channels="[selected.channel]" :busy="busy" :paynow-configured="context.paynowConfigured" topup @confirm="confirm" />
      <button @click="selected = null; qr = ''">{{ t('wallet.close') }}</button>
    </section>
  </section>
</template>
