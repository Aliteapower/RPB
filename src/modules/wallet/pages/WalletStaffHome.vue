<script setup lang="ts">
import {computed,onUnmounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {useStaffWallet} from '../useStaffWallet'
import {cashierActions} from '../cashierNavigation'
const route=useRoute(),router=useRouter(),{t}=useI18n(),{store,context,can}=useStaffWallet()
const target=computed(()=>!context.value?undefined:cashierActions(store.value,can).find(action=>action.id==='points'||(context.value?.transactionsEnabled&&context.value.program))?.target)
// Context is refreshed every five seconds. Compare the destination, not each freshly allocated target object.
const targetKey=computed(()=>JSON.stringify(target.value??null)),failed=ref(false),opening=ref(false)
let revision=0,timer:ReturnType<typeof setTimeout>|undefined
function reset(){revision++;if(timer!==undefined)clearTimeout(timer);timer=undefined;opening.value=false;failed.value=false}
async function open(){
 if(!target.value||route.name!=='wallet-workbench'||opening.value)return
 const current=++revision,destination=target.value;failed.value=false;opening.value=true
 timer=setTimeout(()=>{if(current===revision&&route.name==='wallet-workbench'){failed.value=true;opening.value=false}},15000)
 try{await router.replace(destination);if(current===revision&&route.name==='wallet-workbench')failed.value=true}
 catch{if(current===revision&&route.name==='wallet-workbench')failed.value=true}
 finally{if(current===revision){if(timer!==undefined)clearTimeout(timer);timer=undefined;opening.value=false}}
}
watch(targetKey,()=>{reset();if(target.value&&route.name==='wallet-workbench')void open()},{immediate:true})
onUnmounted(reset)
</script>
<template><div v-if="failed&&target" class="wallet-empty"><p class="wallet-error" role="alert">{{t('wallet.navigationFailed')}}</p><button type="button" :disabled="opening" @click="open">{{t('wallet.navigationRetry')}}</button></div><p v-else class="wallet-empty" role="status">{{t(target?'wallet.loading':'wallet.noRights')}}</p></template>
