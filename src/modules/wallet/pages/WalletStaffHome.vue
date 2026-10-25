<script setup lang="ts">
import {computed,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {useStaffWallet} from '../useStaffWallet'
import {cashierActions} from '../cashierNavigation'
const route=useRoute(),router=useRouter(),{t}=useI18n(),{store,context,can}=useStaffWallet()
const target=computed(()=>!context.value?undefined:cashierActions(store.value,can).find(action=>action.id==='points'||(context.value?.transactionsEnabled&&context.value.program))?.target)
watch(target,value=>{if(value&&route.name==='wallet-workbench')void router.replace(value)},{immediate:true})
</script>
<template><p class="wallet-empty" role="status">{{t(target?'wallet.loading':'wallet.noRights')}}</p></template>
