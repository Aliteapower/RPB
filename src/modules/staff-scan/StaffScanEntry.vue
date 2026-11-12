<script setup lang="ts">
import {useAuthSessionStore} from '../../stores/authSession'
import {useStaffScanText} from './text'
import WalletIcon from '../wallet/components/WalletIcon.vue'
defineProps<{store:string;disabled?:boolean}>()
const auth=useAuthSessionStore(),{t}=useStaffScanText()
const allowed=()=>auth.user?.permissions.some(p=>['wallet.view','wallet.cards.view','wallet.charge.create','wallet.topup.create','wallet.points.view','wine_storage.view'].includes(p))
</script>
<template><RouterLink v-if="allowed()" class="staff-scan-entry" :to="`/stores/${store}/scan`" :inert="disabled" :aria-disabled="disabled||undefined"><WalletIcon name="scan" /><strong>{{t('title')}}</strong></RouterLink></template>
<style scoped>.staff-scan-entry{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:44px;padding:8px 14px;border:1px solid #cbdde9;border-radius:12px;background:#edf6ff;color:#1761c5;text-decoration:none;flex-shrink:0}.staff-scan-entry[inert]{opacity:.5}</style>
