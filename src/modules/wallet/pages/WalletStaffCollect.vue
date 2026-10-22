<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { createWalletBill } from '../api'
import { staffWalletPath } from '../navigation'
import { useStaffWallet } from '../useStaffWallet'
import { useWalletTask } from '../useWalletTask'
import WalletStaffCustomerPicker from '../components/WalletStaffCustomerPicker.vue'
const { t } = useI18n(), router = useRouter(), { store, account, context, customerCode, can } = useStaffWallet()
const { busy, error, run } = useWalletTask(), amount = ref('')
async function create() {
  if (!account.value) return
  await run(async () => {
    const bill = await createWalletBill(store.value, account.value!.id, amount.value, customerCode.value)
    customerCode.value = ''; amount.value = ''
    await router.push(staffWalletPath(store.value, `bills/${bill.id}`))
  })
}
</script>
<template><section class="wallet-page-heading"><h2>{{ t('wallet.ui.collect') }}</h2><p>{{ t('wallet.customerConfirm') }}</p></section>
  <template v-if="can('wallet.charge.create') && context?.transactionsEnabled && context.program"><WalletStaffCustomerPicker />
    <form v-if="account" class="wallet-amount-card" @submit.prevent="create"><label>{{ t('wallet.ui.receivable') }}<span class="wallet-amount-input"><span>S$</span><input v-model="amount" :aria-label="t('wallet.amount')" type="number" inputmode="decimal" min="0.01" step="0.01" placeholder="0.00" required></span></label><small>{{ t('wallet.ui.collectConsent') }}</small><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><button class="wallet-primary wallet-block" :disabled="busy">{{ busy ? t('wallet.busy') : t('wallet.ui.startCollection') }}</button></form>
  </template><p v-else class="wallet-empty">{{ t('wallet.noRights') }}</p>
</template>
