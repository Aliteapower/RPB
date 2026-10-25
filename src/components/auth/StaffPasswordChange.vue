<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {changeStaffPassword,AuthApiError} from '../../api/authApi'
const props=defineProps<{disabled?:boolean}>(),emit=defineEmits<{busyChange:[busy:boolean]}>(),{t}=useI18n()
const open=ref(false),current=ref(''),password=ref(''),confirm=ref(''),busy=ref(false),error=ref(''),success=ref(false)
function close(){open.value=false;current.value='';password.value='';confirm.value='';error.value=''}
async function save(){
 if(busy.value||props.disabled)return
 error.value='';success.value=false
 if(password.value!==confirm.value){error.value=t('customerAuth.mismatch');return}
 if(!/^[A-Za-z0-9]{6}$/.test(password.value)||password.value.toLowerCase()===current.value.toLowerCase()){error.value=t('wallet.accountUi.passwordPolicy');return}
 busy.value=true;emit('busyChange',true)
 try{await changeStaffPassword(current.value,password.value);close();success.value=true}
 catch(e){const code=e instanceof AuthApiError?e.response.error.code:'';error.value=t(code==='INVALID_CREDENTIALS'?'wallet.accountUi.currentIncorrect':code==='PASSWORD_POLICY_VIOLATION'?'wallet.accountUi.passwordPolicy':code==='RATE_LIMITED'?'wallet.accountUi.rateLimited':code==='UNAUTHENTICATED'?'wallet.accountUi.sessionExpired':'wallet.accountUi.changeFailed')}
 finally{busy.value=false;emit('busyChange',false)}
}
</script>
<template><section class="staff-password-change">
 <div v-if="!open" class="customer-account-actions"><button type="button" :disabled="disabled" @click="open=true;success=false">{{t('customerAuth.changePassword')}}</button><slot name="actions" /></div>
 <form v-else @submit.prevent="save"><strong>{{t('customerAuth.changePassword')}}</strong><p>{{t('wallet.accountUi.passwordPolicy')}}</p>
 <label>{{t('customerAuth.currentPassword')}}<input v-model="current" type="password" autocomplete="current-password" maxlength="72" :disabled="busy||disabled" required></label>
 <label>{{t('customerAuth.newPassword')}}<input v-model="password" type="password" autocomplete="new-password" minlength="6" maxlength="6" pattern="[A-Za-z0-9]{6}" :disabled="busy||disabled" required></label>
 <label>{{t('customerAuth.confirmPassword')}}<input v-model="confirm" type="password" autocomplete="new-password" minlength="6" maxlength="6" :disabled="busy||disabled" required></label>
 <p v-if="error" class="wallet-error" role="alert">{{error}}</p><button :disabled="busy||disabled">{{t(busy?'customerAuth.busy':'customerAuth.save')}}</button><button type="button" :disabled="busy||disabled" @click="close">{{t('customerAuth.cancel')}}</button></form>
 <p v-if="success" role="status">{{t('wallet.accountUi.changed')}}</p>
</section></template>
<style scoped>.staff-password-change{padding:16px;background:white;border:1px solid #dbe3ea;border-radius:10px}form,label{display:grid;gap:10px}form{gap:14px}.customer-account-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}input{width:100%;box-sizing:border-box}p{font-size:14px;line-height:1.5}</style>
