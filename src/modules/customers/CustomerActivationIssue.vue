<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {issueCustomerActivation,CustomerPasswordApiError} from '../../api/customerAuthApi'
const props=defineProps<{store:string;customer:{id:string;displayName:string|null;phoneE164:string|null}}>(),emit=defineEmits<{close:[]}>(),{t,te}=useI18n()
const confirmed=ref(false),busy=ref(false),code=ref(''),expires=ref(''),error=ref('')
let generation=0
let expiryTimer:ReturnType<typeof setTimeout>|undefined
function clear(){generation++;if(expiryTimer)clearTimeout(expiryTimer);confirmed.value=false;code.value='';expires.value='';error.value='';busy.value=false}
watch(()=>[props.store,props.customer.id],clear)
onUnmounted(clear)
async function issue(){
 if(busy.value||!confirmed.value)return
 const own=++generation;busy.value=true;if(expiryTimer)clearTimeout(expiryTimer);code.value='';expires.value='';error.value=''
 try{const result=await issueCustomerActivation(props.store,props.customer.id,true);if(own===generation){code.value=result.code;expires.value=result.expiresAt;expiryTimer=setTimeout(()=>{if(own===generation){code.value='';expires.value='';confirmed.value=false;error.value=t('customerAuth.errors.code_mismatch')}},Math.max(0,new Date(result.expiresAt).getTime()-Date.now()))}}
 catch(e){if(own===generation){const key=`customerAuth.errors.${e instanceof CustomerPasswordApiError?e.code:'request_failed'}`;error.value=t(te(key)?key:'customerAuth.errors.request_failed')}}
 finally{if(own===generation)busy.value=false}
}
</script>
<template><section class="activation-issue" :aria-label="t('customerAuth.issueActivation')">
 <h3>{{t('customerAuth.issueActivation')}}</h3><p>{{customer.displayName||customer.id}} · {{customer.phoneE164||'—'}}</p><p>{{t('customerAuth.issueActivationHint')}}</p>
 <form @submit.prevent="issue">
  <label><input v-model="confirmed" type="checkbox" :disabled="busy || !!code"> {{t('customerAuth.identityConfirmed')}}</label>
  <p v-if="error" role="alert" class="error">{{error}}</p>
  <template v-if="code"><p role="status">{{t('customerAuth.activationDeliver')}}</p><output class="activation-secret">{{code}}</output><p>{{t('customerAuth.activationExpires')}} {{new Date(expires).toLocaleTimeString()}}</p></template>
  <button :disabled="busy || !confirmed || !customer.phoneE164">{{t(busy?'customerAuth.busy':code?'customerAuth.reissueActivation':'customerAuth.issueActivation')}}</button>
  <button type="button" @click="emit('close')">{{t('customerAuth.closeActivation')}}</button>
 </form>
</section></template>
<style scoped>.activation-issue{padding:18px;border:1px solid #cbd5e1;background:#fff;color:#102033;border-radius:12px;overflow-wrap:anywhere}form{display:grid;gap:12px}.activation-secret{font-family:monospace;font-size:22px;letter-spacing:2px;padding:12px;background:#f1f5f9}button{font:inherit;min-height:42px;padding:8px 12px}.error{color:#991b1b}p{font-size:14px;line-height:1.6}</style>
