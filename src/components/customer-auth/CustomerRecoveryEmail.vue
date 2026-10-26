<script setup lang="ts">
import {ref,onUnmounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {CustomerPasswordApiError,requestRecoveryEmailCode,confirmRecoveryEmail} from '../../api/customerAuthApi'
const props=defineProps<{store:string;disabled?:boolean}>(),{t,te}=useI18n()
const open=ref(false),email=ref(''),code=ref(''),sent=ref(false),busy=ref(false),complete=ref(false),error=ref('')
let active=true
onUnmounted(()=>active=false)
async function submit(confirm:boolean){
 if(busy.value||props.disabled)return
 busy.value=true;error.value=''
 try{
  if(confirm){await confirmRecoveryEmail(props.store,email.value,code.value);if(active){complete.value=true;open.value=false;code.value='';sent.value=false}}
  else{await requestRecoveryEmailCode(props.store,email.value);if(active)sent.value=true}
 }catch(e){if(active){const key=`customerAuth.errors.${e instanceof CustomerPasswordApiError?e.code:'request_failed'}`;error.value=t(te(key)?key:'customerAuth.errors.request_failed')}}
 finally{if(active)busy.value=false}
}
</script>
<template>
 <section class="customer-recovery-email">
  <button v-if="!open" type="button" :disabled="disabled" @click="open=true;complete=false">{{t('customerAuth.bindRecoveryEmail')}}</button>
  <p v-if="complete" role="status">{{t('customerAuth.emailBound')}}</p>
  <form v-if="open" @submit.prevent="submit(sent)">
   <p>{{t('customerAuth.bindEmailHint')}}</p>
   <label>{{t('customerAuth.recoveryEmail')}}<input v-model="email" type="email" autocomplete="email" maxlength="254" :disabled="busy || sent || disabled" required></label>
   <label v-if="sent">{{t('customerAuth.emailCode')}}<input v-model="code" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" :disabled="busy || disabled" required></label>
   <p v-if="sent" role="status">{{t('customerAuth.bindingCodeSent')}}</p>
   <p v-if="error" role="alert" class="error">{{error}}</p>
   <button :disabled="busy || disabled">{{t(busy?'customerAuth.busy':sent?'customerAuth.verifyEmail':'customerAuth.sendCode')}}</button>
   <button v-if="sent" type="button" :disabled="busy || disabled" @click="submit(false)">{{t('customerAuth.resendCode')}}</button>
   <button type="button" :disabled="busy" @click="open=false;code='';sent=false;error=''">{{t('customerAuth.cancel')}}</button>
  </form>
 </section>
</template>
<style scoped>
.customer-recovery-email{margin-top:12px}form,label{display:grid;gap:10px}form{gap:14px}input,button{font:inherit;min-height:40px;padding:8px 12px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box}input{width:100%}button{cursor:pointer;color:#0f766e;background:#f8fafc}button:disabled{opacity:.6}.error{color:#991b1b}p{font-size:14px;line-height:1.6}
</style>
