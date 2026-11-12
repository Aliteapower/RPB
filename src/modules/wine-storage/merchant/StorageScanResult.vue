<script setup lang="ts">
import type {Home,Intent} from '../types'
import StorageIntentPanel from '../StorageIntentPanel.vue'
import {useMerchantStorageText} from './text'
defineProps<{intent:Intent;home:Home;disabled:boolean}>()
const emit=defineEmits<{next:[]}>(),{t}=useMerchantStorageText()
</script>
<template><section class="storage-section"><h2>{{t('scanSuccess')}}</h2><p class="storage-notice">{{t('scanCompletedHint')}}</p><StorageIntentPanel :intent="intent" :items="home.items" :customer="false" :can-deliver="false" :timezone="home.timezone" :disabled="disabled"/><button class="primary" :disabled="disabled" @click="emit('next')">{{t('scanNext')}}</button></section></template>
