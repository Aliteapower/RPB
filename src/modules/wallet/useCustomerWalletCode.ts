import { onUnmounted, ref, watch, type Ref } from 'vue'
import { walletApi } from './api'
import type { Account, PaymentCode } from './types'

/** Identity codes may be shown immediately; debit consent still requires a deliberate action. */
export function useCustomerWalletCode(store: Ref<string>, account: Ref<Account | null>, purpose: Ref<'topup' | 'merchant_charge'>, enabled: Ref<boolean>) {
  const code = ref<PaymentCode | null>(null), busy = ref(false), error = ref('')
  let generation = 0
  async function issue() {
    const id = account.value?.id, current = generation
    if (!id || !enabled.value || busy.value) return
    busy.value = true; error.value = ''
    try {
      const result = await walletApi(store.value, true).post<PaymentCode>('/payment-codes', { accountId: id, purpose: purpose.value })
      if (current === generation) code.value = result
    } catch (e) { if (current === generation) error.value = e instanceof Error ? e.message : String(e) }
    finally { if (current === generation) busy.value = false }
  }
  watch([store, () => account.value?.id, purpose, enabled], () => {
    generation++; code.value = null; error.value = ''; busy.value = false
    if (purpose.value === 'topup') void issue()
  }, { immediate: true })
  onUnmounted(() => { generation++ })
  return { code, busy, error, issue }
}
