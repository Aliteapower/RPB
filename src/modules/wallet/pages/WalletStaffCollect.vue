<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { createWalletBill } from '../api'
import { staffWalletPath } from '../navigation'
import { useStaffWallet } from '../useStaffWallet'
import { useWalletTask } from '../useWalletTask'
import WalletStaffCustomerPicker from '../components/WalletStaffCustomerPicker.vue'
import WalletChargePreview from '../components/WalletChargePreview.vue'
import { chargePreview, createScanChargeAttempt, isChargeCode } from '../scanCharge'
const { t } = useI18n(), router = useRouter(), workspace = useStaffWallet(), { store, account, context, customerCode, can } = workspace
const { busy, error, run } = useWalletTask(), amount = ref('')
const confirmCharge=createScanChargeAttempt()
const direct = computed(() => isChargeCode(customerCode.value))
const preview = computed(() => chargePreview(amount.value, account.value ? account.value.balanceCents-account.value.reservedCents : 0))
async function create() {
  if (!account.value || !preview.value || workspace.busy.value || (direct.value && !can('wallet.collection.confirm'))) return
  const id=store.value,selected=account.value,code=customerCode.value,value=amount.value,scan=direct.value
  await run(async () => {
    const bill = scan
      ? (await confirmCharge(id,selected,value,code)).bill
      : await createWalletBill(id,selected.id,value,code)
    if(store.value!==id || account.value?.id!==selected.id)return
    customerCode.value = ''; amount.value = ''
    await router.push(staffWalletPath(id, `bills/${bill.id}`))
  })
}
</script>
<template><section class="wallet-page-heading"><h2>{{ t('wallet.ui.collect') }}</h2><p>{{ t('wallet.scanCharge.collectHint') }}</p></section>
  <template v-if="can('wallet.charge.create') && context?.transactionsEnabled && context.program"><WalletStaffCustomerPicker :disabled="busy" />
    <form v-if="account" class="wallet-amount-card" @submit.prevent="create"><label>{{ t('wallet.ui.receivable') }}<span class="wallet-amount-input"><span>S$</span><input v-model="amount" :disabled="busy" :aria-label="t('wallet.amount')" type="number" inputmode="decimal" min="0.01" step="0.01" placeholder="0.00" required></span></label><WalletChargePreview v-if="direct && preview" :debit-cents="preview.debitCents" :due-cents="preview.dueCents" /><small v-else>{{ t(direct?'wallet.scanCharge.staffConsent':'wallet.ui.collectConsent') }}</small><p v-if="direct && !can('wallet.collection.confirm')" class="wallet-warning">{{ t('wallet.scanCharge.cashierRights') }}</p><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><button class="wallet-primary wallet-block" :disabled="busy || workspace.busy.value || !preview || (direct && !can('wallet.collection.confirm'))">{{ busy ? t('wallet.busy') : t(direct?'wallet.scanCharge.confirm':'wallet.ui.startCollection') }}</button></form>
  </template><p v-else class="wallet-empty">{{ t('wallet.noRights') }}</p>
</template>
