<script setup lang="ts">
import CustomerPasswordLogin from '../../../components/customer-auth/CustomerPasswordLogin.vue'
defineProps<{ store: string }>()
const emit = defineEmits<{ authenticated: [] }>()
</script>
<template><CustomerPasswordLogin :store="store" @authenticated="emit('authenticated')" /></template>
