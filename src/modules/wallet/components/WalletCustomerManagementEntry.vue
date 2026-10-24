<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthSessionStore } from '../../../stores/authSession'
import { customerManagementTarget, type CustomerEntrySource } from '../../customers/navigation'
import { useStaffWallet } from '../useStaffWallet'
import WalletIcon from './WalletIcon.vue'
const props=withDefaults(defineProps<{source?:CustomerEntrySource;keyword?:string;create?:boolean;shortcut?:boolean;disabled?:boolean}>(),{source:'wallet',keyword:'',create:false,shortcut:false,disabled:false})
const auth=useAuthSessionStore(),{store}=useStaffWallet(),{t}=useI18n()
const allowed=computed(()=>auth.isTenantAdmin && auth.user?.storeIds?.includes(store.value))
</script>
<template><RouterLink v-if="allowed&&!disabled" :to="customerManagementTarget(store,source,keyword,create)" :class="{'wallet-customer-management-shortcut':shortcut}">
  <span v-if="shortcut" class="wallet-shortcut-icon"><WalletIcon name="plus" /></span>
  <strong>{{t(create?'wallet.operation.createCustomer':'wallet.operation.customers')}}</strong>
  <small v-if="shortcut">{{t('wallet.operation.customersHint')}}</small>
</RouterLink></template>
