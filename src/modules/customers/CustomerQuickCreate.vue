<script setup lang="ts">
import {computed,toRef,watch,onUnmounted} from 'vue'
import {useI18n} from 'vue-i18n'
import type {TenantAdminCustomer} from '../../api/tenantAdminApi'
import CustomerProfileFields from './CustomerProfileFields.vue'
import {useCustomerManagement} from './useCustomerManagement'
const props=defineProps<{store:string;keyword?:string;disabled?:boolean}>(),emit=defineEmits<{created:[TenantAdminCustomer];cancel:[];lookup:[string];'busy-change':[boolean]}>()
const {locale}=useI18n(),english=computed(()=>locale.value.startsWith('en'))
const {form,privacyAccepted,privacyNotice,noticeLoading,loadPrivacyNotice,canSubmit,canManage,saving,errorText,submitCustomer}=useCustomerManagement(toRef(props,'store'),{create:true,createOnly:true,keyword:props.keyword,surface:'tenant_admin',onCreated:customer=>emit('created',customer)})
watch([saving,noticeLoading],()=>emit('busy-change',saving.value||noticeLoading.value),{immediate:true,flush:'sync'})
onUnmounted(()=>emit('busy-change',false))
async function save(){if(!props.disabled)await submitCustomer()}
</script>
<template><section class="customer-quick-create"><h2>{{english?'Add customer':'新增顾客'}}</h2><p>{{english?'Create a shared tenant customer profile, then continue with this customer. A phone number is required.':'新增租户统一顾客档案，保存后直接选中继续办理。手机号必填，其他资料可选。'}}</p>
<p v-if="!canManage" role="alert">{{english?'Only a tenant administrator with access to this store can add customers.':'仅有本店权限的租户管理员可新增顾客。'}}</p>
<form v-else class="customer-quick-create-form" @submit.prevent="save"><fieldset :disabled="disabled||saving"><CustomerProfileFields :form="form" :disabled="disabled||saving" show-privacy v-model:privacy-accepted="privacyAccepted" :privacy-notice="privacyNotice" :notice-loading="noticeLoading" @reload-notice="loadPrivacyNotice"/>
<p v-if="errorText" class="customer-quick-create-error" role="alert">{{errorText}}</p><button v-if="errorText&&form.phoneE164" type="button" :disabled="saving||disabled" @click="emit('lookup',form.phoneE164)">{{english?'Find an existing customer with this phone':'按此手机号查找已有顾客'}}</button>
<div class="customer-quick-create-actions"><button type="button" :disabled="saving||disabled" @click="emit('cancel')">{{english?'Cancel':'取消'}}</button><button class="primary" :disabled="disabled||!canSubmit">{{english?(saving?'Saving…':'Save and select customer'):(saving?'保存中…':'保存并选用顾客')}}</button></div></fieldset></form></section></template>
<style scoped>.customer-quick-create{--customer-field-radius:12px;background:white;border:1px solid #d9e4e6;border-radius:16px;padding:18px;margin:16px 0;min-width:0;overflow-wrap:anywhere}.customer-quick-create-error{color:#9b3322;background:#fff0ed;border-radius:9px;padding:12px 14px}.customer-quick-create-actions{display:flex;flex-wrap:wrap;gap:10px}.customer-quick-create-actions{margin-top:18px}.customer-quick-create-actions button{flex:1;min-width:110px}</style>
