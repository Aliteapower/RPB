<script setup lang="ts">
import CustomerPasswordLogin from '../../../components/customer-auth/CustomerPasswordLogin.vue'
import {computed} from 'vue'
import {customerRegistrationReturn} from '../navigation'
const props=defineProps<{ store: string; returnTo?: string }>()
const registrationTo=computed(()=>`/wallet/${encodeURIComponent(props.store)}/register${props.returnTo ? '?'+new URLSearchParams({returnTo:customerRegistrationReturn(props.store,props.returnTo)}) : ''}`)
const emit = defineEmits<{ authenticated: [] }>()
</script>
<template><CustomerPasswordLogin :store="store" :registration-to="registrationTo" @authenticated="emit('authenticated')" /></template>
