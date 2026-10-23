<script setup lang="ts">
import { computed,ref,watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import { staffWalletPath } from '../navigation'
import { createPersonalChargeAttempt,type PersonalChargeResult } from '../personalCharge'
import { chargePreview } from '../scanCharge'
import WalletQrScanner from '../components/WalletQrScanner.vue'
import WalletAmountKeypad from '../components/WalletAmountKeypad.vue'
const {t}=useI18n(),router=useRouter(),workspace=useStaffWallet(),{store,actorId,context,can}=workspace
const amount=ref(''),description=ref(''),scanning=ref(false),busy=ref(false),error=ref(''),pending=ref(false),ownerMismatch=ref(false)
let attempt=createPersonalChargeAttempt(store.value,actorId.value,()=>actorId.value),generation=0
const valid=computed(()=>!!chargePreview(amount.value,0))
const allowed=computed(()=>can('wallet.charge.create')&&can('wallet.collection.confirm')&&context.value?.transactionsEnabled&&!!context.value?.program)
watch([store,actorId],()=>{generation++;busy.value=false;attempt=createPersonalChargeAttempt(store.value,actorId.value,()=>actorId.value);pending.value=attempt.pending;ownerMismatch.value=attempt.ownerMismatch;amount.value=attempt.intent?.amount??'';description.value=attempt.intent?.description??'';scanning.value=false;error.value='';workspace.clear()},{immediate:true,flush:'sync'})
async function finish(result:PersonalChargeResult,id:string){if(store.value!==id)return;pending.value=false;amount.value='';description.value='';scanning.value=false;workspace.clear();if(result.status==='not_submitted'){error.value=t('wallet.personal.notSubmitted');return}await router.push(staffWalletPath(id,`bills/${result.bill.id}`))}
async function scanned(code:string){if(busy.value||pending.value||!allowed.value||!valid.value)return;const id=store.value,current=attempt,own=generation;busy.value=true;scanning.value=false;error.value=''
 try{const result=await current.scan(code,amount.value,description.value);if(own===generation)await finish(result,id)}catch(e){if(own===generation)error.value=e instanceof Error?e.message:String(e)}finally{if(own===generation){pending.value=current.pending;busy.value=false}}
}
async function recover(){if(busy.value||!pending.value||ownerMismatch.value)return;const id=store.value,current=attempt,own=generation;busy.value=true;error.value=''
 try{const result=await current.retry();if(own===generation)await finish(result,id)}catch(e){if(own===generation)error.value=e instanceof Error?e.message:String(e)}finally{if(own===generation){pending.value=current.pending;busy.value=false}}
}
</script>
<template><section class="wallet-page-heading"><h2>{{t('wallet.ui.collect')}}</h2><p>{{t('wallet.personal.collectHint')}}</p></section>
 <template v-if="allowed"><form class="wallet-amount-card" @submit.prevent="scanning=true">
  <WalletAmountKeypad v-model="amount" :store="store" :disabled="busy||pending||scanning" />
  <label>{{t('wallet.personal.description')}}<input v-model.trim="description" :disabled="busy||pending||scanning" maxlength="200"></label>
  <button v-if="!scanning&&!pending" class="wallet-primary wallet-block" :disabled="busy||!valid">{{t('wallet.personal.scanPay')}}</button>
  <WalletQrScanner v-if="scanning" purpose="personal" auto-start :disabled="busy||pending" @code="scanned" />
  <button v-if="scanning" type="button" :disabled="busy" @click="scanning=false">{{t('wallet.close')}}</button>
 </form></template><p v-else class="wallet-empty">{{t('wallet.noRights')}}</p>
 <section v-if="pending" class="wallet-card"><p role="status">{{t(ownerMismatch?'wallet.personal.ownerMismatch':'wallet.personal.unknown')}}</p><button :disabled="busy||ownerMismatch" @click="recover">{{t('wallet.personal.checkResult')}}</button></section>
 <p v-if="error" class="wallet-error" role="alert">{{error}}</p>
</template>
