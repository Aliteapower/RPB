<script setup lang="ts">
import {ref,onUnmounted} from 'vue'
import {useI18n} from 'vue-i18n'
import CountryPhoneField from '../common/CountryPhoneField.vue'
import {activateCustomer,CustomerPasswordApiError} from '../../api/customerAuthApi'
const props=defineProps<{store:string}>(),emit=defineEmits<{done:[]}>(),{t,te}=useI18n()
const phone=ref(''),code=ref(''),password=ref(''),confirm=ref(''),busy=ref(false),complete=ref(false),error=ref('')
let active=true
onUnmounted(()=>{active=false;code.value='';password.value='';confirm.value=''})
async function save(){
 if(busy.value)return
 error.value=''
 if(password.value!==confirm.value){error.value=t('customerAuth.mismatch');return}
 busy.value=true
 try{await activateCustomer(props.store,phone.value,code.value,password.value);if(active){complete.value=true;code.value='';password.value='';confirm.value=''}}
 catch(e){if(active){const key=`customerAuth.errors.${e instanceof CustomerPasswordApiError?e.code:'request_failed'}`;error.value=t(te(key)?key:'customerAuth.errors.request_failed')}}
 finally{if(active)busy.value=false}
}
</script>
<template><section class="customer-activation">
 <h2>{{t('customerAuth.activate')}}</h2><p>{{t('customerAuth.activationHint')}}</p>
 <template v-if="complete"><p role="status">{{t('customerAuth.activationComplete')}}</p><button type="button" @click="emit('done')">{{t('customerAuth.login')}}</button></template>
 <form v-else @submit.prevent="save">
  <CountryPhoneField v-model="phone" model-format="e164" :label="t('customerAuth.phone')" :disabled="busy" required />
  <label>{{t('customerAuth.activationCode')}}<input v-model="code" autocomplete="off" autocapitalize="characters" maxlength="24" :disabled="busy" required></label>
  <label>{{t('customerAuth.newPassword')}}<input v-model="password" type="password" autocomplete="new-password" minlength="8" maxlength="72" :disabled="busy" required></label>
  <label>{{t('customerAuth.confirmPassword')}}<input v-model="confirm" type="password" autocomplete="new-password" minlength="8" maxlength="72" :disabled="busy" required></label>
  <p v-if="error" role="alert" class="error">{{error}}</p>
  <button :disabled="busy">{{t(busy?'customerAuth.busy':'customerAuth.activateSave')}}</button>
  <button type="button" :disabled="busy" @click="emit('done')">{{t('customerAuth.cancel')}}</button>
 </form>
</section></template>
<style scoped>form,label{display:grid;gap:10px}form{gap:14px}input,button{font:inherit;min-height:42px;padding:8px 12px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box}input{width:100%}button{cursor:pointer;color:#0f766e;background:#f8fafc}button:disabled{opacity:.6}.error{color:#991b1b}p{font-size:14px;line-height:1.6}</style>
