<script setup lang="ts">
import { ref, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import CountryPhoneField from '../common/CountryPhoneField.vue'
import { CustomerPasswordApiError, loginCustomerWithPassword } from '../../api/customerAuthApi'
import type { CustomerAuthPrincipal } from '../../types/publicBooking'
import CustomerActivation from './CustomerActivation.vue'
import CustomerPasswordReset from './CustomerPasswordReset.vue'
const props = defineProps<{ store: string; registrationTo?:string }>()
const emit = defineEmits<{ authenticated: [principal: CustomerAuthPrincipal] }>()
const { t, te } = useI18n()
const phone = ref(''), password = ref(''), busy = ref(false), error = ref('')
const activating=ref(false), recovering=ref(false)
let generation=0
function authenticated(principal:CustomerAuthPrincipal){
  if(principal.passwordChangeRequired){activating.value=true;recovering.value=false;return}
  emit('authenticated',principal)
}
watch(()=>props.store,()=>{generation++;activating.value=false;recovering.value=false;password.value='';phone.value='';error.value='';busy.value=false})
onUnmounted(()=>generation++)
async function login() {
  if (busy.value) return
  busy.value = true; error.value = ''
  const own=++generation
  try {
    const result = await loginCustomerWithPassword(props.store, phone.value, password.value)
    if(own!==generation)return
    password.value = ''; authenticated(result.principal)
  } catch (e) {
    if(own!==generation)return
    const key = `customerAuth.errors.${e instanceof CustomerPasswordApiError ? e.code : 'request_failed'}`
    error.value = t(te(key) ? key : 'customerAuth.errors.request_failed')
  } finally { if(own===generation)busy.value = false }
}
</script>
<template>
  <section class="customer-auth-panel">
    <CustomerActivation v-if="activating" :key="store" :store="store" @done="activating=false" />
    <CustomerPasswordReset v-else-if="recovering" :key="store" :store="store" @done="recovering=false" />
    <template v-else>
    <h2>{{ t('customerAuth.login') }}</h2><p>{{ t('customerAuth.loginHint') }}</p>
    <form @submit.prevent="login">
      <CountryPhoneField v-model="phone" model-format="e164" :label="t('customerAuth.phone')" :disabled="busy" required />
      <label>{{ t('customerAuth.password') }}<input v-model="password" type="password" autocomplete="current-password" maxlength="72" :disabled="busy" required></label>
      <button :disabled="busy">{{ t(busy ? 'customerAuth.busy' : 'customerAuth.login') }}</button>
    </form>
    <p v-if="error" class="customer-auth-error" role="alert">{{ error }}</p>
    <RouterLink :to="registrationTo??('/customers/register?storeId='+encodeURIComponent(store))">{{t('customerAuth.register')}}</RouterLink>
    <button type="button" :disabled="busy" @click="recovering=true">{{t('customerAuth.forgotPassword')}}</button>
    <button type="button" :disabled="busy" @click="activating=true">{{t('customerAuth.activate')}}</button>
    </template>
  </section>
</template>
<style scoped>
.customer-auth-panel{padding:20px;background:white;border:1px solid #dbe3ea;border-radius:12px;color:#102033}
h2{margin:0 0 8px;font-size:20px}p{font-size:14px;line-height:1.6}form,label{display:grid;gap:10px}form{gap:16px}
input,button{font:inherit;border-radius:6px;min-height:42px;padding:8px 12px}input{width:100%;box-sizing:border-box;border:1px solid #cbd5e1}
button{border:0;background:#0f766e;color:white;font-weight:700;cursor:pointer}button:disabled{opacity:.6;cursor:default}.customer-auth-error{color:#991b1b}
</style>
