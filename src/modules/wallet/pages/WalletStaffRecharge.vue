<script setup lang="ts">
import { rechargeRecordsTarget } from '../pointsOperations'
import { staffWalletPath } from '../navigation'
import { computed, ref, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import WalletStaffTopup from './WalletStaffTopup.vue'
import WalletPoints from './WalletPoints.vue'
import WalletCustomerIdentityPicker from '../components/WalletCustomerIdentityPicker.vue'
import WalletPhoneCustomerPicker from '../components/WalletPhoneCustomerPicker.vue'
import WalletCustomerManagementEntry from '../components/WalletCustomerManagementEntry.vue'
import type { PointsEntry } from '../marketingTypes'
const emit=defineEmits<{'busy-change':[busy:boolean]}>()
const { t } = useI18n(), route = useRoute(), router = useRouter()
const workspace=useStaffWallet(),{ store, can, busy, customer, identifiedCustomerId,phoneConfirmedCustomerId:confirmedPhoneId, choose, pointsContext } = workspace, operationBusy = ref(false)
watch([busy,operationBusy],()=>emit('busy-change',busy.value||operationBusy.value),{immediate:true,flush:'sync'})
onUnmounted(()=>emit('busy-change',false))
const pickerGeneration=ref(0),completed=ref<PointsEntry|null>(null)
function finish(entry:PointsEntry){completed.value=entry;workspace.clear();pickerGeneration.value++;operationBusy.value=false}
watch(store,()=>{completed.value=null})
watch(()=>customer.value?.id,id=>{if(id)completed.value=null},{flush:'sync'})
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
function resetOperation(){confirmedPhoneId.value=null;void router.replace({query:{...route.query,kind:route.query.kind==='money'||route.query.kind==='points'?route.query.kind:undefined,scan:phoneMode.value?undefined:'1'}})}
const identified=computed(()=>!!customer.value&&customer.value.id===(phoneMode.value?confirmedPhoneId.value:identifiedCustomerId.value))
const selected=computed(()=>route.query.kind==='points'||route.query.kind==='money')
</script>
<template>
  <section class="wallet-page-heading"><h2>{{ t('wallet.marketing.rechargeEntry') }}</h2><WalletCustomerManagementEntry source="wallet-recharge" :disabled="busy||operationBusy" /><RouterLink :to="rechargeRecordsTarget(store,pointsMode?'points':'topup',customer)">{{t('wallet.operation.records')}}</RouterLink></section>
  <section v-if="completed" class="wallet-success" role="status"><strong>{{t('wallet.operation.done')}}</strong><p>{{t(`wallet.marketing.${completed.kind}`)}} · {{completed.quantity}} {{t('wallet.transactions.points')}}</p><p>{{t('wallet.marketing.balance')}} {{completed.beforeBalance}} → {{completed.afterBalance}}</p><p>{{t('wallet.operation.selectNext')}}</p><RouterLink :to="staffWalletPath(store,`transactions/points/${completed.id}`)">{{t('wallet.operation.detail')}}</RouterLink></section>
  <WalletPhoneCustomerPicker v-if="phoneMode&&(moneyAllowed||pointsAllowed)" :key="pickerGeneration" :confirmed-id="confirmedPhoneId" :disabled="operationBusy" @confirmed="confirmPhone" @clear="resetOperation" />
  <WalletCustomerIdentityPicker v-else-if="moneyAllowed||pointsAllowed" :key="pickerGeneration" :disabled="operationBusy" @identified="prepareMoney" @clear="resetOperation" />
  <div v-if="identified && (moneyAllowed || pointsAllowed) && (phoneMode || !selected)" class="wallet-recharge-options" :aria-label="t('wallet.marketing.operationType')">
    <button v-if="moneyAllowed" type="button" :aria-pressed="selected&&!pointsMode" :disabled="busy || operationBusy" @click="change('money')">{{ t('wallet.marketing.moneyTopup') }}</button>
    <button v-if="pointsAllowed" type="button" :aria-pressed="selected&&pointsMode" :disabled="busy || operationBusy" @click="change('points')">{{ t('wallet.marketing.pointsOperations') }}</button>
  </div>
  <WalletPoints v-if="identified&&selected&&pointsMode" embedded @busy-change="operationBusy=$event" @completed="finish" />
  <WalletStaffTopup v-else-if="identified&&selected&&moneyAllowed" embedded @busy-change="operationBusy=$event" />
  <p v-else-if="!moneyAllowed&&!pointsAllowed" class="wallet-empty">{{ t('wallet.noRights') }}</p>
</template>
