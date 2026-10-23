<script setup lang="ts">
import {useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {useStaffWallet} from '../useStaffWallet'
import {staffWalletPath} from '../navigation'
const props=defineProps<{disabled?:boolean;collect?:boolean}>()
const router=useRouter(),{t}=useI18n(),workspace=useStaffWallet()
function next(){if(props.disabled||workspace.busy.value)return;workspace.clear();workspace.error.value='';void router.push({path:staffWalletPath(workspace.store.value,props.collect?'collect':'topup'),query:props.collect?{}:{scan:'1'}})}
</script>
<template><button class="wallet-primary wallet-block" :disabled="disabled||workspace.busy.value" @click="next">{{t('wallet.personal.nextCustomer')}}</button></template>
