<script setup lang="ts">
import {useI18n} from 'vue-i18n'
defineProps<{username?:string|null;name?:string|null;phone?:string|null;email?:string|null}>()
const {t}=useI18n()
</script>
<template><dl class="wallet-account-identity"><dt v-if="username">{{t('wallet.accountUi.username')}}</dt><dd v-if="username">{{username}}</dd><dt>{{t('wallet.accountUi.name')}}</dt><dd>{{name||'—'}}</dd><dt v-if="phone">{{t('wallet.accountUi.phone')}}</dt><dd v-if="phone">{{phone}}</dd><dt v-if="email">{{t('wallet.email')}}</dt><dd v-if="email">{{email}}</dd></dl></template>
<style scoped>.wallet-account-identity{display:grid;grid-template-columns:auto minmax(0,1fr);gap:10px 16px;padding:16px;margin:0 0 16px;background:#fff;border:1px solid var(--wallet-line);border-radius:12px}.wallet-account-identity dt{color:var(--wallet-muted)}.wallet-account-identity dd{margin:0;overflow-wrap:anywhere;font-weight:600}</style>
