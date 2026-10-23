<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { WalletApiError, createWalletBill } from '../api'
import { staffWalletPath } from '../navigation'
import { useStaffWallet } from '../useStaffWallet'
import { useWalletTask } from '../useWalletTask'
import { useChargeComponents } from '../useChargeComponents'
import WalletAmountSplit from '../components/WalletAmountSplit.vue'
import WalletStaffCustomerPicker from '../components/WalletStaffCustomerPicker.vue'
import WalletChargePreview from '../components/WalletChargePreview.vue'
import { chargePreview, createScanChargeAttempt, isChargeCode } from '../scanCharge'
const { t } = useI18n(), router = useRouter(), workspace = useStaffWallet(), { store, account, context, customerCode, can } = workspace
const { busy, error, run } = useWalletTask(), amount = ref('')
const confirmCharge=createScanChargeAttempt()
const direct = computed(() => isChargeCode(customerCode.value))
const preview = computed(() => chargePreview(amount.value, account.value ? account.value.balanceCents-account.value.reservedCents : 0))
const allocation=useChargeComponents(store,account,amount,customerCode,direct),{components,loading:previewLoading,error:previewError}=allocation
async function create() {
  if (!account.value || (direct.value&&!components.value) || !preview.value || workspace.busy.value || (direct.value && !can('wallet.collection.confirm'))) return
  const id=store.value,selected={...account.value,version:direct.value?components.value!.accountVersion:account.value.version},code=customerCode.value,value=String(amount.value),scan=direct.value
  await run(async () => {
    try { const bill = scan
      ? (await confirmCharge(id,selected,value,code)).bill
      : await createWalletBill(id,selected.id,value,code)
    if(store.value!==id || account.value?.id!==selected.id)return
    customerCode.value = ''; amount.value = ''
    await router.push(staffWalletPath(id, `bills/${bill.id}`))
    }catch(e){if(e instanceof WalletApiError&&e.status>=400&&e.status<500)allocation.clear();throw e}
  })
}
</script>
<template><section class="wallet-page-heading"><h2>{{ t('wallet.ui.collect') }}</h2><p>{{ t('wallet.scanCharge.collectHint') }}</p></section>
  <template v-if="can('wallet.charge.create') && context?.transactionsEnabled && context.program"><WalletStaffCustomerPicker :disabled="busy" />
    <form v-if="account" class="wallet-amount-card" @submit.prevent="create"><label>{{ t('wallet.ui.receivable') }}<span class="wallet-amount-input"><span>S$</span><input v-model="amount" :disabled="busy" :aria-label="t('wallet.amount')" type="number" inputmode="decimal" min="0.01" step="0.01" placeholder="0.00" required></span></label><WalletChargePreview v-if="direct && preview && components" :debit-cents="components.principalCents+components.bonusCents" :due-cents="preview.totalCents-components.principalCents-components.bonusCents" /><WalletAmountSplit v-if="direct&&components" :principal="components.principalCents" :bonus="components.bonusCents" /><p v-if="previewLoading" role="status">{{t('wallet.loading')}}</p><p v-if="previewError" class="wallet-error" role="alert">{{previewError}}</p><button v-if="direct&&!components" type="button" :disabled="busy||previewLoading" @click="allocation.refresh">{{t('wallet.refresh')}}</button><small v-if="!direct||!components">{{ t(direct?'wallet.scanCharge.staffConsent':'wallet.ui.collectConsent') }}</small><p v-if="direct && !can('wallet.collection.confirm')" class="wallet-warning">{{ t('wallet.scanCharge.cashierRights') }}</p><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><button class="wallet-primary wallet-block" :disabled="busy || workspace.busy.value || !preview || (direct && (!can('wallet.collection.confirm')||!components||previewLoading))">{{ busy ? t('wallet.busy') : t(direct?'wallet.scanCharge.confirm':'wallet.ui.startCollection') }}</button></form>
  </template><p v-else class="wallet-empty">{{ t('wallet.noRights') }}</p>
</template>
