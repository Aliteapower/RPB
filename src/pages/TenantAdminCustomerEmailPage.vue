<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import {useAuthSessionStore} from '../stores/authSession'
import TenantAdminPublicBookingPage from './TenantAdminPublicBookingPage.vue'
import {customerAccessReturnTarget} from '../modules/customers/accessNavigation'
import {useCustomerAccessText} from '../modules/customers/accessText'
const route=useRoute(),auth=useAuthSessionStore(),{t}=useCustomerAccessText(),store=computed(()=>String(route.params.storeId||''))
const allowed=computed(()=>auth.isTenantAdmin&&auth.user?.storeIds.includes(store.value)===true)
</script>
<template><section class="customer-email-workspace"><RouterLink :to="customerAccessReturnTarget(store,route.query.accessSource)">← {{t('back')}}</RouterLink><TenantAdminPublicBookingPage v-if="allowed" :key="store" email-only/><p v-else role="alert">{{t('noAccess')}}</p></section></template>
<style scoped>.customer-email-workspace{max-width:1040px;margin:auto;padding:16px;box-sizing:border-box}.customer-email-workspace>a{color:#155f52;display:inline-block;margin-bottom:10px}</style>
