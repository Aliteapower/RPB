<script setup lang="ts">
import {computed,onUnmounted,reactive,ref,watch} from 'vue'
import {useGeneratedText} from '../../i18n/generatedText'
import {getRegistrationNotice,registerCustomer,CustomerRegistrationApiError} from '../../api/customerRegistrationApi'
import type {CustomerPrivacyNotice} from '../../api/tenantAdminApi'
import CustomerProfileFields from './CustomerProfileFields.vue'
const props=defineProps<{store:string}>(),emit=defineEmits<{registered:[customerId:string]}>(),{gt}=useGeneratedText()
const form=reactive({displayName:'',nickname:'',phoneE164:'',email:''}),notice=ref<CustomerPrivacyNotice|null>(null),accepted=ref(true),loading=ref(false),busy=ref(false),done=ref(false),error=ref('')
let generation=0
const canSubmit=computed(()=>!busy.value&&!loading.value&&!!notice.value&&accepted.value&&!!form.phoneE164)
async function load(){if(loading.value)return;const version=generation;loading.value=true;error.value='';try{const result=await getRegistrationNotice(props.store);if(version===generation)notice.value=result}catch{if(version===generation)error.value=gt('generated.tenant-admin-customers.048')}finally{if(version===generation)loading.value=false}}
async function submit(){if(!canSubmit.value)return;const version=generation,id=props.store;busy.value=true;error.value='';try{const result=await registerCustomer(id,{...form},notice.value!.version,accepted.value);if(version!==generation)return;done.value=true;emit('registered',result.customerId)}catch(e){if(version===generation)error.value=gt(e instanceof CustomerRegistrationApiError&&e.code==='phone_already_registered'?'generated.tenant-admin-customers.053':e instanceof CustomerRegistrationApiError&&e.code==='rate_limited'?'generated.tenant-admin-customers.054':'generated.tenant-admin-customers.055')}finally{if(version===generation)busy.value=false}}
watch(()=>props.store,()=>{generation++;notice.value=null;accepted.value=true;loading.value=false;busy.value=false;done.value=false;Object.assign(form,{displayName:'',nickname:'',phoneE164:'',email:''});void load()},{immediate:true})
onUnmounted(()=>generation++)
</script>
<template><section class="customer-registration"><h2>{{gt('generated.tenant-admin-customers.050')}}</h2><template v-if="done"><p role="status">{{gt('generated.tenant-admin-customers.051')}}</p><p>{{gt('generated.tenant-admin-customers.052')}}</p><slot name="login" :registered="true" /></template><form v-else @submit.prevent="submit"><p v-if="error" class="registration-error" role="alert">{{error}}</p><CustomerProfileFields :form="form" :disabled="busy" show-privacy self-registration v-model:privacy-accepted="accepted" :privacy-notice="notice" :notice-loading="loading" @reload-notice="load" /><button :disabled="!canSubmit">{{gt('generated.tenant-admin-customers.050')}}</button><slot name="login" :registered="false" /></form></section></template>
<style scoped>
.customer-registration{--customer-field-radius:12px;padding:22px;background:white;border:1px solid #d7dfea;border-radius:20px;color:#102033}h2{margin:0 0 16px}form{display:grid;gap:20px}p{line-height:1.6;overflow-wrap:anywhere}button{min-height:46px;border:0;border-radius:12px;background:var(--wallet-blue,#1677ff);color:white;font:inherit;font-weight:600;cursor:pointer}button:disabled{opacity:.45;cursor:default}.registration-error{color:#9b2929}
</style>
