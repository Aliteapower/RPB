<script setup lang="ts">
import CustomerSelfRegistrationForm from '../../customers/CustomerSelfRegistrationForm.vue'
import {useCustomerWallet} from '../useCustomerWallet'
import {customerRegistrationReturn} from '../navigation'
import {useI18n} from 'vue-i18n'
import {useRouter,useRoute} from 'vue-router'
import {computed} from 'vue'
import CustomerPasswordLogin from '../../../components/customer-auth/CustomerPasswordLogin.vue'
const {store,authenticated}=useCustomerWallet(),{t}=useI18n(),router=useRouter(),route=useRoute()
const returnTo=computed(()=>customerRegistrationReturn(store.value,route.query.returnTo))
async function signedIn(){await authenticated();await router.push(returnTo.value)}
</script>
<template><CustomerSelfRegistrationForm :store="store"><template #login="{registered}"><CustomerPasswordLogin v-if="registered" :store="store" @authenticated="signedIn" /><RouterLink v-else :to="returnTo">{{t('customerAuth.login')}}</RouterLink></template></CustomerSelfRegistrationForm></template>
