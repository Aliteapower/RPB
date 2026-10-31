<script setup lang="ts">
import {computed,onUnmounted,ref,watch} from 'vue'
import {useRouter} from 'vue-router'
import {useAuthSessionStore} from '../../stores/authSession'
import {walletApi} from '../wallet/api'
import {chargePreview} from '../wallet/scanCharge'
import {createPersonalChargeAttempt} from '../wallet/personalCharge'
import type {Context} from '../wallet/types'
import WalletAmountKeypad from '../wallet/components/WalletAmountKeypad.vue'
import {useStaffScanText} from './text'
const props=defineProps<{store:string;code:string}>(),emit=defineEmits<{'busy-change':[boolean]}>()
const auth=useAuthSessionStore(),router=useRouter(),{t}=useStaffScanText(),actor=auth.user?.accountId??'',context=ref<Context|null>(null),amount=ref(''),description=ref(''),busy=ref(false),pending=ref(false),error=ref('')
const personal=createPersonalChargeAttempt(props.store,actor,()=>auth.user?.accountId??'')
let active=true
const owner=computed(()=>auth.user?.accountId===actor),allowed=computed(()=>owner.value&&auth.user?.permissions.includes('wallet.charge.create')&&auth.user?.permissions.includes('wallet.collection.confirm')&&context.value?.transactionsEnabled&&!!context.value.program)
pending.value=personal.pending
if(personal.intent){amount.value=personal.intent.amount;description.value=personal.intent.description}
watch([busy,pending],()=>emit('busy-change',busy.value||pending.value),{immediate:true,flush:'sync'})
void walletApi(props.store).get<Context>('/context').then(value=>{if(active)context.value=value}).catch(e=>{if(active)error.value=String(e.message??e)})
async function submit(){
 if(busy.value||!owner.value||(!pending.value&&(!allowed.value||!chargePreview(amount.value,0))))return
 busy.value=true;error.value=''
 try{
  const result=pending.value?await personal.retry():await personal.scan(props.code,amount.value,description.value)
  pending.value=personal.pending
  if(active&&result.status==='not_submitted')error.value=t('notSubmitted')
  if(active&&owner.value&&result.status!=='not_submitted')await router.push(`/stores/${props.store}/wallet/bills/${result.bill.id}`)
 }catch(e){if(active){error.value=e instanceof Error?e.message:String(e);pending.value=personal.pending}}
 finally{if(active)busy.value=false}
}
onUnmounted(()=>{active=false;emit('busy-change',false)})
</script>
<template><section class="wallet-card"><h2>{{t('charge')}}</h2><p v-if="error" class="wallet-error" role="alert">{{error}}</p><p v-if="!allowed&&!pending" role="status">{{t('expired')}}</p><form @submit.prevent="submit"><WalletAmountKeypad v-model="amount" :store="store" :disabled="busy||pending||!owner"/><label v-if="code.startsWith('WI.')">{{t('description')}}<input v-model.trim="description" maxlength="200" :disabled="busy||pending||!owner"></label><p v-if="pending" role="status">{{t('pending')}}</p><button class="wallet-primary wallet-block" :disabled="busy||!owner||(pending?personal.ownerMismatch:!allowed||!chargePreview(amount,0))">{{t(pending?'retry':'submit')}}</button></form></section></template>
