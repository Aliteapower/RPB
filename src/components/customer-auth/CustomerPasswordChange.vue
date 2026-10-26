<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { changeCustomerPassword, CustomerPasswordApiError } from '../../api/customerAuthApi'
import type { CustomerAuthPrincipal } from '../../types/publicBooking'
import CustomerRecoveryEmail from './CustomerRecoveryEmail.vue'
const props = defineProps<{ disabled?: boolean; required?: boolean; store?: string }>()
const { t, te } = useI18n()
const emit = defineEmits<{ busyChange: [busy: boolean]; changed: [principal: CustomerAuthPrincipal]; expired: [] }>()
const open = ref(false), current = ref(''), password = ref(''), confirm = ref(''), busy = ref(false), error = ref(''), success = ref(false)
function close() { open.value=false;current.value='';password.value='';confirm.value='';error.value='' }
async function save() {
  if (busy.value || props.disabled) return
  error.value='';success.value=false
  if (password.value !== confirm.value) {error.value=t('customerAuth.mismatch');return}
  busy.value=true;emit('busyChange',true)
  try { const result=await changeCustomerPassword(current.value,password.value);close();success.value=true;emit('changed',result.principal) }
  catch (e) { if(props.required && e instanceof CustomerPasswordApiError && e.code==='unauthenticated'){emit('expired');return}const key=`customerAuth.errors.${e instanceof CustomerPasswordApiError ? e.code : 'request_failed'}`;error.value=t(te(key)?key:'customerAuth.errors.request_failed') }
  finally {busy.value=false;emit('busyChange',false)}
}
</script>
<template>
  <section class="customer-password-change">
    <div v-if="!open && !required" class="customer-account-actions"><button type="button" :disabled="disabled" @click="open=true;success=false">{{ t('customerAuth.changePassword') }}</button><slot name="actions" /></div>
    <form v-else @submit.prevent="save">
      <strong>{{ t('customerAuth.changePassword') }}</strong><p>{{ t('customerAuth.passwordHint') }}</p>
      <p v-if="required" role="status">{{ t('customerAuth.firstChangeRequired') }}</p>
      <label>{{ t('customerAuth.currentPassword') }}<input v-model="current" type="password" autocomplete="current-password" maxlength="72" :disabled="busy || disabled" required></label>
      <label>{{ t('customerAuth.newPassword') }}<input v-model="password" type="password" autocomplete="new-password" minlength="8" maxlength="72" :disabled="busy || disabled" required></label>
      <label>{{ t('customerAuth.confirmPassword') }}<input v-model="confirm" type="password" autocomplete="new-password" minlength="8" maxlength="72" :disabled="busy || disabled" required></label>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <button :disabled="busy || disabled">{{ t(busy?'customerAuth.busy':'customerAuth.save') }}</button>
      <button v-if="!required" type="button" :disabled="busy || disabled" @click="close">{{ t('customerAuth.cancel') }}</button>
    </form>
    <p v-if="success" role="status">{{ t('customerAuth.changed') }}</p>
    <CustomerRecoveryEmail v-if="store && !required && !open" :key="store" :store="store" :disabled="disabled || busy" />
  </section>
</template>
<style scoped>
.customer-password-change{padding:16px;background:white;border:1px solid #dbe3ea;border-radius:10px;margin-top:16px;color:#102033}form,label{display:grid;gap:10px}form{gap:14px}
.customer-account-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
button,input{font:inherit;min-height:40px;padding:8px 12px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box}input{width:100%}button{cursor:pointer;background:#f8fafc;color:#0f766e;font-weight:700}button:disabled{opacity:.6}.error{color:#991b1b}p{font-size:14px;line-height:1.5}
</style>
