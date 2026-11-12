<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import {useGeneratedText} from '../../i18n/generatedText'
import type {CustomerPrivacyRecord,TenantAdminCustomer} from '../../api/tenantAdminApi'
defineProps<{customer:TenantAdminCustomer;records:CustomerPrivacyRecord[];loading:boolean}>()
defineEmits<{close:[]}>()
const {gt}=useGeneratedText(),{locale,t}=useI18n()
</script>
<template><section class="customer-privacy-records"><header><h3>{{gt('generated.tenant-admin-customers.040')}}</h3><button type="button" @click="$emit('close')">{{gt('generated.tenant-admin-customers.046')}}</button></header><p><strong>{{customer.displayName||customer.customerCode}}</strong> · {{customer.phoneE164}}</p><p v-if="loading" role="status">{{t('common.loading')}}</p><p v-else-if="!records.length">{{gt('generated.tenant-admin-customers.041')}}</p><article v-for="record in records" :key="record.id"><strong>{{gt('generated.tenant-admin-customers.049')}}</strong><p>{{gt(record.confirmationMethod==='merchant_attestation'?'generated.tenant-admin-customers.045':'generated.tenant-admin-customers.060')}}</p><dl><dt>{{gt('generated.tenant-admin-customers.042')}}</dt><dd>{{record.recordedByName||record.recordedBy}}</dd><dt>{{gt('generated.tenant-admin-customers.043')}}</dt><dd><time :datetime="record.recordedAt">{{record.recordedAt}}</time></dd><dt>{{gt('generated.tenant-admin-customers.044')}}</dt><dd>{{record.noticeVersion}}</dd></dl><p>{{locale==='en-SG'?record.noticeTextEn:record.noticeTextZh}}</p></article></section></template>
<style scoped>
.customer-privacy-records{padding:20px;border:1px solid #d7dfea;border-radius:var(--customer-field-radius,12px);background:white;overflow-wrap:anywhere}.customer-privacy-records header{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}h3{margin:0}article{margin-top:20px;border-top:1px solid #d7dfea;padding-top:16px}dl{display:grid;gap:6px;margin:16px 0}dt{color:#64748b;font-size:13px}dd{margin:0}button{font:inherit;padding:10px 14px;border-radius:8px;border:1px solid #d7dfea;background:white;color:inherit;cursor:pointer}
</style>
