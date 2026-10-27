<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import type {TenantAdminCustomer} from '../api/tenantAdminApi'
import CustomerActivationIssue from '../modules/customers/CustomerActivationIssue.vue'
import {useCustomerManagement} from '../modules/customers/useCustomerManagement'
import {customerAccessReturnTarget} from '../modules/customers/accessNavigation'
import {useCustomerAccessText} from '../modules/customers/accessText'
const route=useRoute(),{t}=useCustomerAccessText(),{t:authText}=useI18n()
const store=computed(()=>String(route.params.storeId||''))
const {customers,canManage,loading,errorText,keyword,safePage,loadCustomers,searchCustomers}=useCustomerManagement(store,{directoryOnly:true})
const selected=ref<TenantAdminCustomer|null>(null)
watch([store,canManage],()=>selected.value=null)
</script>

<template>
 <main class="activation-workspace">
  <header><div><small>{{t('title')}}</small><h1>{{t('activation')}}</h1></div><RouterLink :to="customerAccessReturnTarget(store,route.query.accessSource)">{{t('back')}}</RouterLink></header>
  <p v-if="!canManage" role="alert">{{t('noAccess')}}</p>
  <template v-else>
   <p class="intro">{{t('activationHint')}} {{t('codeHint')}}</p>
   <CustomerActivationIssue v-if="selected" :key="store+selected.id" :store="store" :customer="selected" @close="selected=null" />
   <section v-else class="customer-directory" :aria-label="t('findCustomer')">
    <form class="search" @submit.prevent="searchCustomers"><label for="activation-customer-query">{{t('findCustomer')}}</label><div><input id="activation-customer-query" v-model="keyword" :placeholder="t('searchHint')" :disabled="loading" type="search"><button :disabled="loading">{{t('search')}}</button></div></form>
    <p v-if="errorText" role="alert" class="error">{{errorText}} <button type="button" :disabled="loading" @click="loadCustomers()">{{t('retry')}}</button></p>
    <p v-else-if="loading" role="status">{{authText('customerAuth.busy')}}</p>
    <p v-else-if="!customers.length" role="status">{{t('noCustomers')}}</p>
    <ul v-else><li v-for="customer in customers" :key="customer.id"><div><strong>{{customer.displayName||customer.nickname||customer.customerCode}}</strong><span>{{customer.phoneE164||'—'}}</span><small v-if="customer.status!=='active'">{{t('inactive')}}</small></div><button type="button" :disabled="!customer.phoneE164||customer.status!=='active'" @click="selected=customer">{{t('verify')}}</button></li></ul>
    <footer><span>{{t('total')}} {{safePage.total}}</span><div><button type="button" :disabled="loading||safePage.offset===0" @click="loadCustomers(Math.max(0,safePage.offset-safePage.limit))">{{t('previous')}}</button><button type="button" :disabled="loading||safePage.offset+safePage.limit>=safePage.total" @click="loadCustomers(safePage.offset+safePage.limit)">{{t('next')}}</button></div></footer>
   </section>
  </template>
 </main>
</template>

<style scoped>
.activation-workspace{max-width:800px;margin:auto;padding:24px 16px 48px;color:#163140;font-size:14px;box-sizing:border-box}header,footer,footer>div,.search>div,li{display:flex;align-items:center;justify-content:space-between;gap:10px}header{padding-bottom:16px;border-bottom:1px solid #dbe5e9}header small,.intro,li span,footer{color:#607580}h1{font-size:23px;margin:4px 0 0}header a{color:#17695f;text-decoration:none;flex-shrink:0}.intro{line-height:1.6;margin:14px 0}.customer-directory{background:white;border:1px solid #dbe5e9;border-radius:12px;padding:16px}.search label{display:block;font-weight:600;margin-bottom:8px}.search input{width:100%;min-width:0;flex:1}input,button{font:inherit;border:1px solid #cadcde;border-radius:8px;min-height:40px;padding:8px 12px;box-sizing:border-box}button{background:#eef6f4;color:#176459;cursor:pointer;flex-shrink:0}button:disabled{opacity:.45;cursor:default}ul{list-style:none;padding:0;margin:12px 0}li{padding:12px 0;border-bottom:1px solid #edf1f4}li>div{min-width:0;display:grid;gap:4px;overflow-wrap:anywhere}li strong{font-size:15px}li button{white-space:nowrap}footer{font-size:12px;margin-top:14px}footer button{font-size:12px;min-height:34px;padding:6px 10px}.error{color:#a33131}@media(max-width:480px){.activation-workspace{padding:16px 12px 32px}h1{font-size:21px}header a{font-size:12px}.customer-directory{padding:12px}.intro{font-size:13px}li button{padding:8px 10px}}
</style>
