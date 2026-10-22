<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi, WalletApiError } from '../api'
import { money, remaining } from '../format'
import { useWalletTask } from '../useWalletTask'
import { billStage } from '../navigation'
import WalletPaymentResult from './WalletPaymentResult.vue'
import type { Bill, Context, Quote } from '../types'
const props = defineProps<{ store: string; bill: Bill; context: Context }>()
const emit = defineEmits<{ changed: [bill: Bill] }>()
const { t } = useI18n(), { busy, error, run } = useWalletTask()
const quote = ref<Quote | null>(null), now = ref(Date.now())
const expired = computed(() => !!quote.value && Date.parse(quote.value.expiresAt) <= now.value)
const timer = window.setInterval(() => { now.value = Date.now() }, 1000)
onUnmounted(() => window.clearInterval(timer))
watch(() => props.bill.version, () => { quote.value = null })
async function review() { await run(async () => { quote.value = await walletApi(props.store, true).post<Quote>(`/bills/${props.bill.id}/quote`) }) }
async function pay() {
  const current = quote.value; if (!current || expired.value) return
  await run(async () => {
    try {
      const paid = await walletApi(props.store, true).command<{ bill: Bill }>(`/bills/${props.bill.id}/pay`, { quoteId: current.id })
      quote.value = null; emit('changed', paid.bill)
    } catch(e) {
      if(e instanceof WalletApiError && ['WALLET_QUOTE_STALE','WALLET_AUTHORIZATION_EXPIRED'].includes(e.code))quote.value=null
      throw e
    }
  })
}
</script>
<template>
  <article><WalletPaymentResult :bill="bill" customer /><section class="wallet-payment-summary"><span class="wallet-status" :data-state="billStage(bill)">{{ t(`wallet.ui.status.${billStage(bill)}`) }}</span><h2>{{ money(bill.amountCents) }}</h2><h3>{{ context.store.storeName }}</h3><small>{{ t('wallet.issuer') }} · {{ context.store.operatingEntityName }}</small></section>
    <dl class="wallet-detail-fields"><dt>{{ t('wallet.walletPaid') }}</dt><dd>{{ money(bill.walletPaidCents) }}</dd><dt>{{ t('wallet.otherPaid') }}</dt><dd>{{ money(bill.externalPaidCents) }}</dd><dt>{{ t('wallet.stillDue') }}</dt><dd :class="{'wallet-due':!bill.closed && remaining(bill)>0}">{{ bill.closed ? '—' : money(remaining(bill)) }}</dd></dl>
    <p v-if="bill.refundStatus !== 'none'" class="wallet-warning">{{ t(`wallet.states.${bill.refundStatus}`) }}</p><p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
    <template v-if="!bill.walletApplied && !bill.closed && remaining(bill)>0 && context.transactionsEnabled"><button v-if="!quote || expired" class="wallet-primary wallet-block" :disabled="busy" @click="review">{{ t('wallet.quote') }}</button>
      <section v-if="quote" class="wallet-confirm-card"><h3>{{ t('wallet.ui.confirmAmount') }}</h3><dl class="wallet-detail-fields"><dt>{{ t('wallet.debit') }}</dt><dd>{{ money(quote.amountCents) }}</dd><dt>{{ t('wallet.stillDue') }}</dt><dd :class="{'wallet-due':quote.remainingCents>0}">{{ money(quote.remainingCents) }}</dd></dl><p v-if="quote.remainingCents>0" class="wallet-warning">{{ t('wallet.ui.supplementHint',{amount:money(quote.remainingCents)}) }}</p><p v-if="expired" class="wallet-error" role="status">{{ t('wallet.quoteExpired') }}</p><button class="wallet-primary wallet-block" :disabled="busy || expired" @click="pay">{{ t('wallet.confirmPay') }} {{ money(quote.amountCents) }}</button></section>
    </template>
  </article>
</template>
