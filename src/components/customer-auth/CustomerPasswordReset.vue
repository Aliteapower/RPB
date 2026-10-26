<script setup lang="ts">
import {ref,onUnmounted} from 'vue'
import {useI18n} from 'vue-i18n'
import CountryPhoneField from '../common/CountryPhoneField.vue'
import {CustomerPasswordApiError,requestPasswordResetCode,resetCustomerPassword} from '../../api/customerAuthApi'
const props=defineProps<{store:string}>(),emit=defineEmits<{done:[]}>(),{t,te}=useI18n()
const phone=ref(''),email=ref(''),code=ref(''),password=ref(''),confirm=ref(''),busy=ref(false),sent=ref(false),complete=ref(false),error=ref('')
let active=true
onUnmounted(()=>active=false)
async function run(reset:boolean){
 if(busy.value)return
 error.value=''
 if(reset&&password.value!==confirm.value){error.value=t('customerAuth.mismatch');return}
 busy.value=true
 try{
  if(reset){await resetCustomerPassword(props.store,phone.value,email.value,code.value,password.value);if(active){complete.value=true;password.value='';confirm.value='';code.value=''}}
  else{await requestPasswordResetCode(props.store,phone.value,email.value);if(active)sent.value=true}
 }catch(e){if(active){const key=`customerAuth.errors.${e instanceof CustomerPasswordApiError?e.code:'request_failed'}`;error.value=t(te(key)?key:'customerAuth.errors.request_failed')}}
 finally{if(active)busy.value=false}
}
</script>
<template>
 <section class="customer-recovery">
  <h2>{{t('customerAuth.forgotPassword')}}</h2>
  <template v-if="complete"><p role="status">{{t('customerAuth.resetComplete')}}</p><button type="button" @click="emit('done')">{{t('customerAuth.login')}}</button></template>
  <form v-else @submit.prevent="run(sent)">
   <p>{{t('customerAuth.recoveryHint')}}</p>
   <CountryPhoneField v-model="phone" model-format="e164" :label="t('customerAuth.phone')" :disabled="busy || sent" required />
   <label>{{t('customerAuth.recoveryEmail')}}<input v-model="email" type="email" autocomplete="email" maxlength="254" :disabled="busy || sent" required></label>
   <p v-if="sent" role="status">{{t('customerAuth.resetCodeSent')}}</p>
   <template v-if="sent">
    <label>{{t('customerAuth.emailCode')}}<input v-model="code" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" :disabled="busy" required></label>
    <label>{{t('customerAuth.newPassword')}}<input v-model="password" type="password" autocomplete="new-password" minlength="8" maxlength="72" :disabled="busy" required></label>
    <label>{{t('customerAuth.confirmPassword')}}<input v-model="confirm" type="password" autocomplete="new-password" minlength="8" maxlength="72" :disabled="busy" required></label>
   </template>
   <p v-if="error" role="alert" class="error">{{error}}</p>
   <button :disabled="busy">{{t(busy?'customerAuth.busy':sent?'customerAuth.save':'customerAuth.sendCode')}}</button>
   <button v-if="sent" type="button" :disabled="busy" @click="run(false)">{{t('customerAuth.resendCode')}}</button>
   <button type="button" :disabled="busy" @click="emit('done')">{{t('customerAuth.cancel')}}</button>
  </form>
 </section>
</template>
<style scoped>
form,label{display:grid;gap:10px}form{gap:14px}input,button{font:inherit;min-height:42px;padding:8px 12px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box}input{width:100%}button{cursor:pointer;color:#0f766e;background:#f8fafc}button:disabled{opacity:.6}.error{color:#991b1b}p{line-height:1.6;font-size:14px}
</style>
