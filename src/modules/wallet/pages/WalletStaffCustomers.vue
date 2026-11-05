<script setup lang="ts">
import CustomerActivationAction from '../../customers/CustomerActivationAction.vue'
import {computed,ref,watch} from 'vue'
import CustomerActivationIssue from '../../customers/CustomerActivationIssue.vue'
import type {TenantAdminCustomer} from '../../../api/tenantAdminApi'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {useGeneratedText} from '../../../i18n/generatedText'
import {useCustomerManagement} from '../../customers/useCustomerManagement'
import {customerManagementReturnTarget} from '../customerManagementNavigation'
import CustomerProfileFields from '../../customers/CustomerProfileFields.vue'
import CustomerRegistrationEntry from '../../customers/CustomerRegistrationEntry.vue'
import CustomerPrivacyRecords from '../../customers/CustomerPrivacyRecords.vue'
import {useStaffWallet} from '../useStaffWallet'
const route=useRoute(),{store,clear}=useStaffWallet(),{t}=useI18n(),{gt}=useGeneratedText()
const {activationIssued,privacyAccepted,privacyNotice,noticeLoading,privacyCustomer,privacyRecords,privacyLoading,loadPrivacyNotice,openPrivacyRecords,canSubmit,customers,loading,saving,archivingId,errorText,successText,keyword,safePage,editorOpen,editingCustomer,form,canManage,actionDisabled,loadCustomers,searchCustomers,resetFilters,openCreateEditor,openEditEditor,closeEditor,submitCustomer,archiveCustomerRecord}=useCustomerManagement(store,{keyword:typeof route.query.keyword==='string'?route.query.keyword:'',create:route.query.create==='1',surface:'wallet'})
const returnTarget=computed(()=>customerManagementReturnTarget(store.value,route.query.from))
const activationCustomer=ref<TenantAdminCustomer|null>(null)
watch(store,()=>activationCustomer.value=null)
watch([editorOpen,privacyCustomer,canManage],()=>{if(editorOpen.value||privacyCustomer.value||!canManage.value)activationCustomer.value=null})
// A profile change must never leave an earlier verified cashier selection in use.
clear()
</script>
<template><section class="wallet-customers">
 <div class="wallet-page-heading"><h2>{{editorOpen?gt(editingCustomer?'generated.tenant-admin-customers.022':'generated.tenant-admin-customers.021'):t('wallet.operation.customers')}}</h2><RouterLink v-if="returnTarget" :to="returnTarget">{{t(route.query.from==='wallet-recharge'?'wallet.operation.backRecharge':'wallet.operation.backWallet')}}</RouterLink></div>
 <p v-if="!canManage" class="wallet-empty">{{t('wallet.noRights')}}</p>
 <template v-else><p class="wallet-customer-hint">{{t('customerAuth.sharedHint')}}</p><p v-if="errorText" class="wallet-error" role="alert">{{errorText}}</p><p v-if="successText" class="wallet-success" role="status">{{successText}}</p>
 <CustomerActivationIssue v-if="activationCustomer" :key="activationCustomer.id" :store="store" :customer="activationCustomer" @close="activationCustomer=null" @issued="activationIssued" /><CustomerPrivacyRecords v-if="privacyCustomer" :customer="privacyCustomer" :records="privacyRecords" :loading="privacyLoading" @close="privacyCustomer=null" /><form v-else-if="editorOpen" class="wallet-form" @submit.prevent="submitCustomer"><CustomerProfileFields :form="form" :disabled="saving" :show-privacy="!editingCustomer" v-model:privacy-accepted="privacyAccepted" :privacy-notice="privacyNotice" :notice-loading="noticeLoading" @reload-notice="loadPrivacyNotice" /><div class="wallet-customer-actions"><button type="button" :disabled="saving" @click="closeEditor">{{gt('generated.tenant-admin-customers.028')}}</button><button class="wallet-primary" :disabled="!canSubmit">{{gt(saving?'generated.tenant-admin-customers.026':'generated.tenant-admin-customers.027')}}</button></div></form>
 <template v-else><CustomerRegistrationEntry :store="store" :registration-path="`/wallet/${encodeURIComponent(store)}/register`" /><section class="wallet-card"><form class="wallet-search" @submit.prevent="searchCustomers"><input v-model.trim="keyword" :aria-label="gt('generated.tenant-admin-customers.003')" :placeholder="gt('generated.tenant-admin-customers.003')" :disabled="actionDisabled"><button :disabled="actionDisabled">{{t('wallet.transactions.search')}}</button></form><div class="wallet-customer-actions"><button type="button" :disabled="actionDisabled" @click="resetFilters">{{t('common.actions.reset')}}</button><button type="button" :disabled="actionDisabled" @click="loadCustomers()">{{t('wallet.refresh')}}</button><button type="button" class="wallet-primary" :disabled="actionDisabled" @click="openCreateEditor">{{t('wallet.operation.createCustomer')}}</button></div></section>
 <p v-if="loading" class="wallet-empty" role="status">{{t('wallet.loading')}}</p><p v-else-if="!customers.length" class="wallet-empty">{{gt('generated.tenant-admin-customers.016')}}</p>
 <template v-else><article v-for="item in customers" :key="item.id" class="wallet-card wallet-customer-profile"><strong>{{item.displayName||item.customerCode}}</strong><span>{{item.phoneE164||'—'}}</span><small>{{item.customerCode}} · {{item.nickname||'—'}}</small><small v-if="item.email">{{item.email}}</small><div class="wallet-customer-actions"><CustomerActivationAction :customer="item" :disabled="actionDisabled" @select="activationCustomer=item" /><button type="button" :disabled="actionDisabled" @click="openEditEditor(item)">{{gt('generated.tenant-admin-customers.017')}}</button><button type="button" :disabled="actionDisabled" @click="openPrivacyRecords(item)">{{gt('generated.tenant-admin-customers.040')}}</button><button type="button" class="wallet-danger" :disabled="actionDisabled" @click="archiveCustomerRecord(item)">{{gt(archivingId===item.id?'generated.tenant-admin-customers.026':'generated.tenant-admin-customers.018')}}</button></div></article></template>
 <nav class="wallet-customer-pagination" :aria-label="t('wallet.operation.customerPages')"><button :disabled="actionDisabled||safePage.offset===0" @click="loadCustomers(Math.max(0,safePage.offset-safePage.limit))">{{t('common.actions.previousPage')}}</button><span>{{Math.floor(safePage.offset/safePage.limit)+1}} / {{Math.max(1,Math.ceil(safePage.total/safePage.limit))}} · {{safePage.total}}</span><button :disabled="actionDisabled||safePage.offset+safePage.limit>=safePage.total" @click="loadCustomers(safePage.offset+safePage.limit)">{{t('common.actions.nextPage')}}</button></nav>
 </template></template>
</section></template>
<style scoped>
.wallet-customers{--customer-field-radius:12px}.wallet-customer-hint{color:var(--wallet-muted);font-size:13px}.wallet-customer-actions{display:flex;flex-wrap:wrap;gap:10px;margin-top:16px}.wallet-customer-actions button{flex:1;min-width:80px}.wallet-customer-profile{display:grid;gap:8px;overflow-wrap:anywhere}.wallet-customer-profile small{color:var(--wallet-muted)}.wallet-customer-pagination{display:flex;align-items:center;justify-content:space-between;gap:8px;font-size:13px}.wallet-customer-pagination button{padding:10px}.wallet-success{padding:12px;border-radius:12px;background:#e9f8ef;color:#17623c}
</style>
