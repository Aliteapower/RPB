<script setup lang="ts">
import CustomerSelfRegistrationForm from '../../customers/CustomerSelfRegistrationForm.vue'
import {useCustomerWallet} from '../useCustomerWallet'
import {customerWalletPath} from '../navigation'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import CustomerPasswordLogin from '../../../components/customer-auth/CustomerPasswordLogin.vue'
const {store,authenticated}=useCustomerWallet(),{t}=useI18n(),router=useRouter()
async function signedIn(){await authenticated();await router.push(customerWalletPath(store.value))}
</script>
<template><CustomerSelfRegistrationForm :store="store"><template #login="{registered}"><CustomerPasswordLogin v-if="registered" :store="store" @authenticated="signedIn" /><RouterLink v-else :to="customerWalletPath(store)">{{t('customerAuth.login')}}</RouterLink></template></CustomerSelfRegistrationForm></template>
