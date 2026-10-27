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
 <h3>{{t('customerAuth.issueActivation')}}</h3><p class="customer-identity">{{customer.displayName||customer.id}} · {{customer.phoneE164||'—'}}</p><details><summary>{{t('customerAuth.activationRules')}}</summary><p>{{t('customerAuth.issueActivationHint')}}</p></details>
 <form @submit.prevent="issue">
  <label class="identity-confirmation"><input v-model="confirmed" type="checkbox" :disabled="busy || !!code"> {{t('customerAuth.identityConfirmed')}}</label>
  <p v-if="error" role="alert" class="error">{{error}}</p>
  <template v-if="code"><p role="status">{{t('customerAuth.activationDeliver')}}</p><output class="activation-secret">{{code}}</output><p>{{t('customerAuth.activationExpires')}} {{new Date(expires).toLocaleTimeString()}}</p></template>
  <div class="activation-actions"><button :disabled="busy || !confirmed || !customer.phoneE164">{{t(busy?'customerAuth.busy':code?'customerAuth.reissueActivation':'customerAuth.issueActivation')}}</button>
  <button type="button" @click="emit('close')">{{t('customerAuth.closeActivation')}}</button></div>
 </form>
</section></template>
<style scoped>
.activation-issue{padding:16px;border:1px solid #d5e4e5;background:#fff;color:#163140;border-radius:12px;overflow-wrap:anywhere}h3{font-size:17px;margin:0 0 8px}.customer-identity{font-weight:600;margin:0 0 12px}form{display:grid;gap:12px;margin-top:12px}.identity-confirmation{display:flex;align-items:flex-start;gap:8px;font-size:14px;line-height:1.6}.identity-confirmation input{margin:4px 0 0;flex-shrink:0;width:17px;height:17px;accent-color:#17695f}.activation-secret{display:block;text-align:center;font-family:monospace;font-size:36px;font-weight:700;letter-spacing:8px;padding:14px 8px;background:#edf6f3;border:1px solid #d3e9df;border-radius:8px;color:#125e50}.activation-actions{display:flex;gap:8px;flex-wrap:wrap}.activation-actions button{flex:1;font:inherit;min-height:40px;padding:8px 12px;border:1px solid #cadcde;border-radius:8px;background:#f4f8f8;color:#176459;cursor:pointer}.activation-actions button:first-child{background:#176459;color:white;border-color:#176459}button:disabled{opacity:.45;cursor:default}.error{color:#991b1b}p{font-size:13px;line-height:1.6;margin:0}details{font-size:13px;color:#607580}summary{cursor:pointer}details p{margin-top:8px}@media(max-width:480px){.activation-issue{padding:14px}.activation-actions button{flex-basis:100%}}
</style>
