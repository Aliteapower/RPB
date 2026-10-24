<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { changeCustomerPassword, CustomerPasswordApiError } from '../../api/customerAuthApi'
const { t, te } = useI18n()
const open = ref(false), current = ref(''), password = ref(''), confirm = ref(''), busy = ref(false), error = ref(''), success = ref(false)
function close() { open.value=false;current.value='';password.value='';confirm.value='';error.value='' }
async function save() {
  if (busy.value) return
  error.value='';success.value=false
  if (password.value !== confirm.value) {error.value=t('customerAuth.mismatch');return}
  busy.value=true
  try { await changeCustomerPassword(current.value,password.value);close();success.value=true }
  catch (e) { const key=`customerAuth.errors.${e instanceof CustomerPasswordApiError ? e.code : 'request_failed'}`;error.value=t(te(key)?key:'customerAuth.errors.request_failed') }
  finally {busy.value=false}
}
</script>
<template>
  <section class="customer-password-change">
    <div v-if="!open" class="customer-account-actions"><button type="button" @click="open=true;success=false">{{ t('customerAuth.changePassword') }}</button><slot name="actions" /></div>
    <form v-else @submit.prevent="save">
      <strong>{{ t('customerAuth.changePassword') }}</strong><p>{{ t('customerAuth.passwordHint') }}</p>
      <label>{{ t('customerAuth.currentPassword') }}<input v-model="current" type="password" autocomplete="current-password" maxlength="72" :disabled="busy" required></label>
      <label>{{ t('customerAuth.newPassword') }}<input v-model="password" type="password" autocomplete="new-password" minlength="8" maxlength="72" :disabled="busy" required></label>
      <label>{{ t('customerAuth.confirmPassword') }}<input v-model="confirm" type="password" autocomplete="new-password" minlength="8" maxlength="72" :disabled="busy" required></label>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <button :disabled="busy">{{ t(busy?'customerAuth.busy':'customerAuth.save') }}</button>
      <button type="button" :disabled="busy" @click="close">{{ t('customerAuth.cancel') }}</button>
    </form>
    <p v-if="success" role="status">{{ t('customerAuth.changed') }}</p>
  </section>
</template>
<style scoped>
.customer-password-change{padding:16px;background:white;border:1px solid #dbe3ea;border-radius:10px;margin-top:16px;color:#102033}form,label{display:grid;gap:10px}form{gap:14px}
.customer-account-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
button,input{font:inherit;min-height:40px;padding:8px 12px;border:1px solid #cbd5e1;border-radius:6px;box-sizing:border-box}input{width:100%}button{cursor:pointer;background:#f8fafc;color:#0f766e;font-weight:700}button:disabled{opacity:.6}.error{color:#991b1b}p{font-size:14px;line-height:1.5}
</style>
