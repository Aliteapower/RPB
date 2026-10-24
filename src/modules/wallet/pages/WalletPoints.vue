<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePoints } from '../usePoints'
import { useStaffWallet } from '../useStaffWallet'
import { staffWalletPath } from '../navigation'
import { rechargeRecordsTarget } from '../pointsOperations'
import type { PointsEntry } from '../marketingTypes'
import WalletStaffCustomerPicker from '../components/WalletStaffCustomerPicker.vue'
defineProps<{embedded?:boolean}>()
const emit=defineEmits<{'busy-change':[busy:boolean];completed:[entry:PointsEntry]}>()
const {t}=useI18n(),{store,customer,can,context}=useStaffWallet()
const points=usePoints(store,false,computed(()=>customer.value?.id),()=>can('wallet.points.view')),{account,busy,error,ready}=points
const kind=ref<'grants'|'deductions'|'redemptions'>(can('wallet.points.grant')?'grants':can('wallet.points.adjust')?'deductions':'redemptions')
const quantity=ref(''),reason=ref(''),note=ref(''),externalReference=ref(''),redemptionDescription=ref('')
const reviewing=ref(false),result=ref<PointsEntry|null>(null)
const permission=computed(()=>kind.value==='grants'?'wallet.points.grant':kind.value==='deductions'?'wallet.points.adjust':'wallet.points.redeem')
const allowed=computed(()=>can(permission.value)),canMutate=computed(()=>['wallet.points.grant','wallet.points.adjust','wallet.points.redeem'].some(can))
const actionLabel=computed(()=>t(kind.value==='grants'?'wallet.operation.grant':kind.value==='deductions'?'wallet.operation.deduct':'wallet.operation.redeem'))
const valid=computed(()=>allowed.value&&/^\d+$/.test(quantity.value)&&Number(quantity.value)>=1&&Number(quantity.value)<=1e9&&!!(kind.value==='redemptions'?redemptionDescription.value.trim():reason.value.trim()))
const recordTarget=computed(()=>rechargeRecordsTarget(store.value,'points',customer.value))
const recordReturn=computed(()=>staffWalletPath(store.value,'transactions')+'?category=points'+(customer.value?.phone?'&customerQuery='+encodeURIComponent(customer.value.phone):''))
function reset(){quantity.value='';reason.value='';note.value='';externalReference.value='';redemptionDescription.value='';reviewing.value=false;result.value=null}
watch(busy,value=>emit('busy-change',value),{immediate:true,flush:'sync'})
watch([store,()=>customer.value?.id,kind],reset,{flush:'sync'})
function review(){if(!busy.value&&valid.value&&!points.stale.value)reviewing.value=true}
async function submit(){
  if(busy.value||!reviewing.value||!valid.value||points.stale.value)return
  const id=customer.value?.id,storeId=store.value
  await points.command(kind.value,{quantity:Number(quantity.value),note:note.value,...(kind.value==='redemptions'?{redemptionDescription:redemptionDescription.value,externalReference:externalReference.value}:kind.value==='grants'?{reason:reason.value,externalReference:externalReference.value}:{reason:reason.value})})
  if(storeId===store.value&&id===customer.value?.id&&!error.value&&points.entries.value[0]){result.value=points.entries.value[0];reviewing.value=false;emit('completed',result.value)}
}
</script>
<template>
  <section v-if="!embedded" class="wallet-page-heading"><h2>{{t('wallet.marketing.points')}}</h2></section>
  <p class="wallet-warning">{{t('wallet.marketing.pointsHint')}}</p>
  <p v-if="!can('wallet.points.view')" class="wallet-empty">{{t('wallet.noRights')}}</p>
  <template v-else>
    <WalletStaffCustomerPicker v-if="!embedded" :allow-scan="false" :open-account="false" :disabled="busy" />
    <p v-if="error" class="wallet-error" role="alert">{{error}}</p>
    <p v-if="busy&&!ready" role="status">{{t('wallet.loading')}}</p>
    <p v-if="points.stale.value" class="wallet-warning">{{t('wallet.marketing.stale')}} <button :disabled="busy" @click="points.refresh()">{{t('wallet.refresh')}}</button></p>
    <template v-if="account">
      <section class="wallet-card"><h3>{{t('wallet.marketing.balance')}}: {{account.balance}}</h3><RouterLink v-if="!embedded" :to="recordTarget" class="wallet-secondary-link">{{t('wallet.operation.records')}}</RouterLink></section>
      <section v-if="result" class="wallet-card" aria-live="polite"><h3>{{t('wallet.operation.done')}}</h3><p>{{t(`wallet.marketing.${result.kind}`)}} · {{result.quantity}} {{t('wallet.transactions.points')}}</p><p>{{t('wallet.marketing.balance')}} {{result.beforeBalance}} → {{result.afterBalance}}</p><RouterLink class="wallet-secondary-link" :to="{path:staffWalletPath(store,`transactions/points/${result.id}`),query:{returnTo:recordReturn}}">{{t('wallet.operation.detail')}}</RouterLink><button class="wallet-primary wallet-block" @click="reset">{{t('wallet.operation.again')}}</button></section>
      <template v-else-if="context?.transactionsEnabled&&canMutate">
        <section v-if="reviewing" class="wallet-card"><h3>{{t('wallet.operation.review')}}</h3><p>{{t('wallet.operation.reviewHint')}}</p><p>{{t('wallet.operation.selected')}}：{{customer?.displayName || customer?.customerCode}} {{customer?.phone}}</p><p><strong>{{actionLabel}} · {{quantity}} {{t('wallet.transactions.points')}}</strong></p><p>{{kind==='redemptions'?redemptionDescription:reason}}</p><p v-if="note">{{note}}</p><button class="wallet-primary wallet-block" :disabled="busy||!valid||points.stale.value" @click="submit">{{actionLabel}}</button><button :disabled="busy" @click="reviewing=false">{{t('wallet.operation.edit')}}</button></section>
        <form v-else class="wallet-form" @submit.prevent="review">
          <label>{{t('wallet.marketing.operationType')}}<select v-model="kind" :disabled="busy"><option v-if="can('wallet.points.grant')" value="grants">{{t('wallet.marketing.grant')}}</option><option v-if="can('wallet.points.adjust')" value="deductions">{{t('wallet.marketing.deduction')}}</option><option v-if="can('wallet.points.redeem')" value="redemptions">{{t('wallet.marketing.redemption')}}</option></select></label>
          <label>{{t('wallet.marketing.quantity')}}<input v-model="quantity" type="number" min="1" max="1000000000" step="1" inputmode="numeric" required></label>
          <label v-if="kind!=='redemptions'">{{t('wallet.marketing.reason')}}<input v-model.trim="reason" required maxlength="200"></label>
          <label v-else>{{t('wallet.marketing.description')}}<input v-model.trim="redemptionDescription" required maxlength="500"></label>
          <label>{{t('wallet.marketing.note')}}<textarea v-model.trim="note" maxlength="500" /></label>
          <label v-if="kind!=='deductions'">{{t('wallet.marketing.reference')}}<input v-model.trim="externalReference" maxlength="160"></label>
          <button class="wallet-primary" :disabled="busy||!valid||points.stale.value">{{t('wallet.operation.review')}}</button>
        </form>
      </template>
    </template>
  </template>
</template>
