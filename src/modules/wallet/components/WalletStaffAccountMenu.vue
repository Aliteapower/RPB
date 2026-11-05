<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import {useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {logout,fetchCurrentUser} from '../../../api/authApi'
import type {AuthUser} from '../../../types/auth'
import {useAuthSessionStore} from '../../../stores/authSession'
import StaffPasswordChange from '../../../components/auth/StaffPasswordChange.vue'
import WalletAccountMenu from './WalletAccountMenu.vue'
import WalletAccountIdentity from './WalletAccountIdentity.vue'
const props=defineProps<{store:string;storeName:string}>()
const auth=useAuthSessionStore(),router=useRouter(),{t}=useI18n(),loggingOut=ref(false),changingPassword=ref(false),error=ref('')
const identity=ref<AuthUser|null>(null),loading=ref(false),identityError=ref('')
let generation=0
function clearIdentity(){generation++;identity.value=null;loading.value=false;identityError.value='';changingPassword.value=false;error.value=''}
async function load(){const current=++generation;identity.value=null;loading.value=true;identityError.value='';const expected=auth.user;try{const result=await fetchCurrentUser();if(current===generation){if(result.user&&result.user.accountId===expected?.accountId&&result.user.tenantId===expected?.tenantId)identity.value=result.user;else identityError.value=t('wallet.accountUi.sessionExpired')}}catch{if(current===generation)identityError.value=t('wallet.accountUi.identityFailed')}finally{if(current===generation)loading.value=false}}
watch(()=>props.store,clearIdentity);onUnmounted(()=>generation++)
async function signOut(){if(loggingOut.value||changingPassword.value)return;loggingOut.value=true;error.value='';try{await logout();auth.clear();await router.replace({name:'login'})}catch{error.value=t('wallet.operation.logoutFailed')}finally{loggingOut.value=false}}
</script>
<template><WalletAccountMenu :key="auth.user?.accountId" :store="store" :caption="storeName" :title="t('wallet.ui.staffWallet')" :busy="loggingOut||changingPassword" @opened="load" @closed="clearIdentity">
 <p v-if="loading" role="status">{{t('wallet.loading')}}</p><p v-if="identityError" class="wallet-error" role="alert">{{identityError}} <button type="button" @click="load">{{t('wallet.accountUi.retry')}}</button></p>
 <WalletAccountIdentity v-if="identity" :username="identity.username" :name="identity.displayName" />
 <StaffPasswordChange :disabled="loggingOut||loading||!identity" @busy-change="changingPassword=$event"><template #actions><button type="button" class="wallet-account-logout" :disabled="loggingOut" @click="signOut">{{t(loggingOut?'wallet.operation.loggingOut':'wallet.operation.logout')}}</button></template></StaffPasswordChange>
 <p v-if="error" class="wallet-error" role="alert">{{error}}</p>
</WalletAccountMenu></template>
<style scoped>.wallet-shell .wallet-account-logout{color:#b42318}</style>
