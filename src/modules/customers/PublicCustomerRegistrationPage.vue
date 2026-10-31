<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import CustomerSelfRegistrationForm from './CustomerSelfRegistrationForm.vue'
const route=useRoute(),store=computed(()=>typeof route.query.storeId==='string'?route.query.storeId:'')
</script>
<template><main class="public-customer-registration"><CustomerSelfRegistrationForm :key="store" :store="store" /></main></template>
<style scoped>.public-customer-registration{min-height:100dvh;background:#f4f6fa;padding:24px 16px;box-sizing:border-box}.public-customer-registration>:first-child{max-width:620px;margin:0 auto}</style>
