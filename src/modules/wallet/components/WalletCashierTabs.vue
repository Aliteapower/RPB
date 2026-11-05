<script setup lang="ts">
import {computed} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {useStaffWallet} from '../useStaffWallet'
import {cashierActions,type CashierAction} from '../cashierNavigation'
const props=defineProps<{disabled?:boolean}>(),route=useRoute(),router=useRouter(),{t}=useI18n(),workspace=useStaffWallet()
const actions=computed(()=>cashierActions(workspace.store.value,workspace.can))
const active=computed(()=>route.name==='wallet-collect'?'collect':route.name==='wallet-topup'?route.query.kind:undefined)
async function change(action:CashierAction){if(props.disabled||workspace.busy.value||active.value===action.id)return;workspace.clear();workspace.error.value='';await router.push(action.target)}
</script>
<template><nav class="wallet-workbench-tabs" :aria-label="t('wallet.marketing.operationType')"><button v-for="action in actions" :key="action.id" type="button" :data-cashier-tab="action.id" :aria-pressed="active===action.id" :disabled="disabled||workspace.busy.value" @click="change(action)">{{t(action.label)}}</button></nav></template>
<style scoped>.wallet-workbench-tabs{display:flex;gap:6px;margin:0 0 20px;padding:6px;border:1px solid var(--wallet-line);border-radius:16px;background:#fff}.wallet-workbench-tabs button{flex:1;min-width:0;border:0;background:transparent;padding:12px 6px;color:var(--wallet-muted)}.wallet-workbench-tabs button[aria-pressed="true"]{background:#edf5ff;color:var(--wallet-blue);font-weight:700}</style>
