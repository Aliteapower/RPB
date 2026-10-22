<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { money, remaining } from '../format'
import { useWalletTask } from '../useWalletTask'
import type { Bill, Context, Quote } from '../types'
const props = defineProps<{ store: string; bill: Bill; context: Context }>()
const emit = defineEmits<{ changed: [bill: Bill] }>()
const { t } = useI18n(), { busy, error, run } = useWalletTask()
const quote = ref<Quote | null>(null), now = ref(Date.now()), done = ref(false)
const expired = computed(() => !!quote.value && Date.parse(quote.value.expiresAt) <= now.value)
const timer = window.setInterval(() => { now.value = Date.now() }, 1000)
onUnmounted(() => window.clearInterval(timer))
watch(() => props.bill.version, () => { quote.value = null })
async function review() { await run(async () => { quote.value = await walletApi(props.store, true).post<Quote>(`/bills/${props.bill.id}/quote`) }) }
async function pay() {
  const current = quote.value; if (!current || expired.value) return
  await run(async () => { const paid = await walletApi(props.store, true).command<{ bill: Bill }>(`/bills/${props.bill.id}/pay`, { quoteId: current.id }); quote.value = null; done.value = true; emit('changed', paid.bill) })
}
</script>
<template>
  <article class="wallet-card"><h3>{{ context.store.storeName }} · {{ money(bill.amountCents) }}</h3><small>{{ t('wallet.issuer') }} {{ context.store.operatingEntityName }} · {{ bill.id }}</small>
    <p>{{ t('wallet.walletPaid') }} {{ money(bill.walletPaidCents) }} · {{ t('wallet.otherPaid') }} {{ money(bill.externalPaidCents) }}</p>
    <strong :class="{ 'wallet-due': !bill.closed && remaining(bill) > 0 }">{{ bill.closed ? t('wallet.states.closed') : remaining(bill) > 0 ? `${t('wallet.stillDue')} ${money(remaining(bill))}` : t('wallet.settled') }}</strong><span v-if="bill.refundStatus !== 'none'"> · {{ t(`wallet.states.${bill.refundStatus}`) }}</span>
    <p v-if="done" role="status">{{ t('wallet.paymentDone') }}</p><p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
    <template v-if="!bill.walletApplied && !bill.closed && context.transactionsEnabled"><button :disabled="busy" @click="review">{{ t('wallet.quote') }}</button>
      <section v-if="quote" class="wallet-verification"><p>{{ t('wallet.debit') }} <strong>{{ money(quote.amountCents) }}</strong></p><p class="wallet-due">{{ t('wallet.stillDue') }} {{ money(quote.remainingCents) }}</p><p v-if="expired">{{ t('wallet.quoteExpired') }}</p><button :disabled="busy || expired" @click="pay">{{ t('wallet.confirmPay') }} {{ money(quote.amountCents) }}</button></section>
    </template>
  </article>
</template>
