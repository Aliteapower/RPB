<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { paymentResult, paymentTransition, type PaymentResult } from '../paymentResult'
import { money } from '../format'
import type { Bill } from '../types'
import WalletIcon from './WalletIcon.vue'
const props = defineProps<{ bill: Bill; customer?: boolean }>(), { t } = useI18n()
const result = computed(() => paymentResult(props.bill)), notice = ref<PaymentResult | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined
const title = (value: PaymentResult) => t(`wallet.feedback.${value.kind === 'paid' ? (props.customer ? 'paidCustomer' : 'paidStaff') : value.kind}`)
const detail = (value: PaymentResult) => t(`wallet.feedback.${value.kind === 'paid' ? 'settled' : value.kind === 'partial' ? 'split' : 'zero'}`, { amount:money(value.receivedCents), wallet:money(value.walletCents), due:money(value.dueCents) })
function dismiss() { clearTimeout(timer); notice.value = null }
watch(() => props.bill, (next, previous) => {
  if (!paymentResult(next) || previous?.id !== next.id || previous?.storeId !== next.storeId) { dismiss(); return }
  const changed = paymentTransition(previous ?? null, next)
  if (!changed) return
  dismiss(); notice.value = changed; timer = setTimeout(dismiss, 8000)
  if (document.visibilityState === 'visible') { try { navigator.vibrate?.(changed.kind === 'paid' ? [80,40,80] : 80) } catch { /* Optional feedback, including unsupported iPhones. */ } }
})
onUnmounted(dismiss)
</script>
<template>
  <section v-if="result" class="wallet-payment-result" :class="{'is-paid':result.kind==='paid'}" :aria-label="title(result)">
    <span class="wallet-result-icon"><WalletIcon :name="result.kind==='paid'?'check':'bill'" :size="32" /></span><div><h3>{{ title(result) }}</h3><p>{{ detail(result) }}</p></div>
  </section>
  <aside v-if="notice" class="wallet-payment-notice" :class="{'is-paid':notice.kind==='paid'}" role="status" aria-live="polite" aria-atomic="true">
    <WalletIcon :name="notice.kind==='paid'?'check':'bill'" /><div><strong>{{ title(notice) }}</strong><p>{{ detail(notice) }}</p></div><button type="button" :aria-label="t('wallet.feedback.dismiss')" @click="dismiss">×</button>
  </aside>
</template>
