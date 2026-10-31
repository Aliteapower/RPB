<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {getCustomerMe} from '../../../api/publicBookingApi'
import type {CustomerAuthPrincipal} from '../../../types/publicBooking'
import CustomerPasswordChange from '../../../components/customer-auth/CustomerPasswordChange.vue'
import WalletAccountMenu from './WalletAccountMenu.vue'
import WalletAccountIdentity from './WalletAccountIdentity.vue'
const props=defineProps<{store:string;storeName:string;loggingOut:boolean;error:string}>()
const emit=defineEmits<{logout:[]}>(),{t}=useI18n()
const changingPassword=ref(false),identity=ref<CustomerAuthPrincipal|null>(null),loading=ref(false),identityError=ref('')
let generation=0
function clear(){generation++;identity.value=null;identityError.value='';loading.value=false;changingPassword.value=false}
async function load(){const current=++generation;identity.value=null;loading.value=true;identityError.value='';try{const result=await getCustomerMe(undefined,props.store);if(current===generation){identity.value=result.principal;if(!result.principal)identityError.value=t('wallet.accountUi.identityFailed')}}catch{if(current===generation)identityError.value=t('wallet.accountUi.identityFailed')}finally{if(current===generation)loading.value=false}}
watch(()=>props.store,clear);onUnmounted(()=>generation++)
</script>
<template><WalletAccountMenu :store="store" :caption="storeName" :title="t('wallet.customerWallet')" :busy="changingPassword||loggingOut" @opened="load" @closed="clear">
 <p v-if="loading" role="status">{{t('wallet.loading')}}</p><p v-if="identityError" class="wallet-error" role="alert">{{identityError}} <button type="button" @click="load">{{t('wallet.accountUi.retry')}}</button></p>
 <WalletAccountIdentity v-if="identity" :name="identity.displayName" :phone="identity.phoneE164" :email="identity.email" />
 <CustomerPasswordChange :key="store" :store="store" :disabled="loggingOut||loading||!identity" @busy-change="changingPassword=$event"><template #actions><button type="button" class="wallet-account-logout" :disabled="loggingOut" @click="emit('logout')">{{t(loggingOut?'wallet.operation.loggingOut':'wallet.operation.logout')}}</button></template></CustomerPasswordChange>
 <p v-if="error" class="wallet-error" role="alert">{{error}}</p>
</WalletAccountMenu></template>
<style scoped>.wallet-shell .wallet-account-logout{color:#b42318}</style>
