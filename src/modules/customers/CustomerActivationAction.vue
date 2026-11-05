<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {TenantAdminCustomer} from '../../api/tenantAdminApi'
const props=defineProps<{customer:Pick<TenantAdminCustomer,'activationState'|'phoneE164'|'status'>;disabled?:boolean;label?:string}>()
const emit=defineEmits<{select:[]}>(),{t}=useI18n()
const available=computed(()=>!props.disabled&&!!props.customer.phoneE164&&props.customer.status==='active')
const state=computed(()=>props.customer.activationState??'unknown')
const labelKey=computed(()=>({verified_pending:'alreadyVerified',activated:'alreadyVerified',email_verified:'alreadyEmailVerified',unavailable:'activationUnavailable',unknown:'activationUnknown',required:'issueActivation'}[state.value]))
function select(reissue=false){if(available.value&&state.value===(reissue?'verified_pending':'required'))emit('select')}
</script>
<template><span class="activation-actions"><button type="button" :disabled="!available||state!=='required'" @click="select()">{{state==='required'&&label?label:t('customerAuth.'+labelKey)}}</button><button v-if="state==='verified_pending'" type="button" :disabled="!available" @click="select(true)">{{t('customerAuth.reissueActivationShort')}}</button></span></template>
<style scoped>.activation-actions{display:inline-flex;align-items:center;gap:6px;flex-wrap:wrap;max-width:100%}button{font:inherit;min-height:38px;padding:7px 10px;border:1px solid #cadcde;border-radius:7px;background:#eef6f4;color:#176459;cursor:pointer;line-height:1.4}button:disabled{background:#f1f3f5;border-color:#e1e5e8;color:#7b8790;cursor:default;opacity:1}</style>
