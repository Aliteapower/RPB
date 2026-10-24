<script setup lang="ts">
import { rechargeRecordsTarget } from '../pointsOperations'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import WalletStaffTopup from './WalletStaffTopup.vue'
import WalletPoints from './WalletPoints.vue'
import WalletCustomerIdentityPicker from '../components/WalletCustomerIdentityPicker.vue'
import WalletPhoneCustomerPicker from '../components/WalletPhoneCustomerPicker.vue'
import WalletCustomerManagementEntry from '../components/WalletCustomerManagementEntry.vue'
const { t } = useI18n(), route = useRoute(), router = useRouter()
const { store, can, busy, customer, identifiedCustomerId,phoneConfirmedCustomerId:confirmedPhoneId, choose, pointsContext } = useStaffWallet(), operationBusy = ref(false)
const phoneMode=computed(()=>route.query.entry==='phone')
const moneyAllowed = computed(() => can('wallet.view') && can('wallet.topup.create'))
const pointsAllowed = computed(() => can('wallet.points.view'))
const pointsMode = computed(() => pointsAllowed.value && (!moneyAllowed.value || route.query.kind === 'points'))
async function change(kind: 'money' | 'points') {
  if (busy.value || operationBusy.value) return
  const previous=pointsContext.value
  await router.replace({ query: { ...route.query, kind, scan: undefined } })
  if(kind==='money'&&!previous&&customer.value)await choose(customer.value)
}
async function prepareMoney(){if(route.query.kind==='money'&&customer.value&&!busy.value)await choose(customer.value)}
function confirmPhone(id:string){confirmedPhoneId.value=id;void prepareMoney()}
function resetOperation(){confirmedPhoneId.value=null;void router.replace({query:{...route.query,kind:undefined,scan:phoneMode.value?undefined:'1'}})}
const identified=computed(()=>!!customer.value&&customer.value.id===(phoneMode.value?confirmedPhoneId.value:identifiedCustomerId.value))
const selected=computed(()=>route.query.kind==='points'||route.query.kind==='money')
</script>
<template>
  <section class="wallet-page-heading"><h2>{{ t('wallet.marketing.rechargeEntry') }}</h2><WalletCustomerManagementEntry source="wallet-recharge" :disabled="busy||operationBusy" /><RouterLink :to="rechargeRecordsTarget(store,pointsMode?'points':'topup',customer)">{{t('wallet.operation.records')}}</RouterLink></section>
  <WalletPhoneCustomerPicker v-if="phoneMode&&(moneyAllowed||pointsAllowed)" :confirmed-id="confirmedPhoneId" :disabled="operationBusy" @confirmed="confirmPhone" @clear="resetOperation" />
  <WalletCustomerIdentityPicker v-else-if="moneyAllowed||pointsAllowed" :disabled="operationBusy" @identified="prepareMoney" @clear="resetOperation" />
  <div v-if="identified && (moneyAllowed || pointsAllowed)" class="wallet-recharge-options" :aria-label="t('wallet.marketing.operationType')">
    <button v-if="moneyAllowed" type="button" :aria-pressed="selected&&!pointsMode" :disabled="busy || operationBusy" @click="change('money')">{{ t('wallet.marketing.moneyTopup') }}</button>
    <button v-if="pointsAllowed" type="button" :aria-pressed="selected&&pointsMode" :disabled="busy || operationBusy" @click="change('points')">{{ t('wallet.marketing.pointsOperations') }}</button>
  </div>
  <WalletPoints v-if="identified&&selected&&pointsMode" embedded @busy-change="operationBusy=$event" />
  <WalletStaffTopup v-else-if="identified&&selected&&moneyAllowed" embedded @busy-change="operationBusy=$event" />
  <p v-else-if="!moneyAllowed&&!pointsAllowed" class="wallet-empty">{{ t('wallet.noRights') }}</p>
</template>
