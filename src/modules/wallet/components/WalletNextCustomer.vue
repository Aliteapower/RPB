<script setup lang="ts">
import {useRouter,useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {useStaffWallet} from '../useStaffWallet'
import {staffWalletPath} from '../navigation'
const props=defineProps<{disabled?:boolean;collect?:boolean}>()
const router=useRouter(),route=useRoute(),{t}=useI18n(),workspace=useStaffWallet()
function next(){if(props.disabled||workspace.busy.value)return;workspace.clear();workspace.error.value='';void router.push({path:staffWalletPath(workspace.store.value,props.collect?'collect':'topup'),query:props.collect?{}:route.query.entry==='phone'?{entry:'phone',...(route.query.kind==='money'||route.query.kind==='points'?{kind:route.query.kind}:{})}:{scan:'1',kind:route.query.kind==='points'?'points':'money'}})}
</script>
<template><button class="wallet-primary wallet-block" :disabled="disabled||workspace.busy.value" @click="next">{{t('wallet.personal.nextCustomer')}}</button></template>
