<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import WalletStaffTopup from './WalletStaffTopup.vue'
import WalletPoints from './WalletPoints.vue'
import WalletCustomerIdentityPicker from '../components/WalletCustomerIdentityPicker.vue'
const { t } = useI18n(), route = useRoute(), router = useRouter()
const { can, busy, customer, identifiedCustomerId, choose, pointsContext } = useStaffWallet(), operationBusy = ref(false)
const moneyAllowed = computed(() => can('wallet.view') && can('wallet.topup.create'))
const pointsAllowed = computed(() => can('wallet.points.view'))
const pointsMode = computed(() => pointsAllowed.value && (!moneyAllowed.value || route.query.kind === 'points'))
async function change(kind: 'money' | 'points') {
  if (busy.value || operationBusy.value) return
  const previous=pointsContext.value
  await router.replace({ query: { ...route.query, kind, scan: undefined } })
  if(kind==='money'&&!previous&&customer.value)await choose(customer.value)
}
function resetOperation(){void router.replace({query:{...route.query,kind:undefined,scan:'1'}})}
const identified=computed(()=>!!customer.value&&customer.value.id===identifiedCustomerId.value)
const selected=computed(()=>route.query.kind==='points'||route.query.kind==='money')
</script>
<template>
  <section class="wallet-page-heading"><h2>{{ t('wallet.marketing.rechargeEntry') }}</h2></section>
  <WalletCustomerIdentityPicker v-if="moneyAllowed||pointsAllowed" :disabled="operationBusy" @clear="resetOperation" />
  <div v-if="identified && (moneyAllowed || pointsAllowed)" class="wallet-recharge-options" :aria-label="t('wallet.marketing.operationType')">
    <button v-if="moneyAllowed" type="button" :aria-pressed="selected&&!pointsMode" :disabled="busy || operationBusy" @click="change('money')">{{ t('wallet.marketing.moneyTopup') }}</button>
    <button v-if="pointsAllowed" type="button" :aria-pressed="selected&&pointsMode" :disabled="busy || operationBusy" @click="change('points')">{{ t('wallet.marketing.pointsOperations') }}</button>
  </div>
  <WalletPoints v-if="identified&&selected&&pointsMode" embedded @busy-change="operationBusy=$event" />
  <WalletStaffTopup v-else-if="identified&&selected&&moneyAllowed" embedded @busy-change="operationBusy=$event" />
  <p v-else-if="!moneyAllowed&&!pointsAllowed" class="wallet-empty">{{ t('wallet.noRights') }}</p>
</template>
