<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { useWalletTask } from '../useWalletTask'
import type { Context,Program } from '../types'
import {channelLabel,channelType,type CollectionMethod,type CollectionMethodSettings,type CollectionType} from '../collectionMethods'
const props = defineProps<{ store: string; context: Context }>()
const emit = defineEmits<{ changed: [] }>()
const { t } = useI18n(), { busy, error, run } = useWalletTask()
const version=ref<number|null>(props.context.program?.version??null)
const initial=()=> (props.context.program?.channels??['cash','paynow','credit_card']).map(code=>({code,name:channelLabel(t,code,props.context.program?.channelLabels),type:channelType(code)}))
const methods=ref<CollectionMethod[]>(initial()),saved=ref(false),configured=ref(!!props.context.program)
const types:CollectionType[]=['cash','paynow','credit_card','other']
const valid=computed(()=>methods.value.length>0&&methods.value.length<=30&&methods.value.every(m=>m.name.trim().length>0&&m.name.trim().length<=60)&&new Set(methods.value.map(m=>m.name.trim().toLowerCase())).size===methods.value.length)
function move(index:number,offset:number){if(busy.value)return;const target=index+offset;if(target<0||target>=methods.value.length)return;[methods.value[index],methods.value[target]]=[methods.value[target]!,methods.value[index]!];saved.value=false}
async function reload(){if(busy.value)return;await run(async()=>{if(configured.value){const result=await walletApi(props.store).get<CollectionMethodSettings>('/collection-methods');version.value=result.version;methods.value=result.methods.map(m=>({...m,name:m.name||channelLabel(t,m.code!)}))}else{version.value=null;methods.value=initial()}saved.value=false})}
async function save() {
  if (busy.value || !valid.value || props.context.denialReason || !props.context.store.operatingEntityId) return
  await run(async () => {
    if(!configured.value){const program=await walletApi(props.store).command<Program>('/program',{version:null,channels:['cash','paynow','credit_card']},'PUT');version.value=program.version;methods.value=program.channels.map(code=>({code,name:channelLabel(t,code,program.channelLabels),type:channelType(code)}));configured.value=true;emit('changed');return}
    const result=await walletApi(props.store).command<CollectionMethodSettings>('/collection-methods',{version:version.value,methods:methods.value},'PUT')
    version.value=result.version;methods.value=result.methods;saved.value=true
    emit('changed')
  })
}
</script>
<template>
  <section class="wallet-card">
    <p>{{ t('wallet.operation.methodsHint') }}</p>
    <p class="wallet-muted">{{ t('wallet.issuer') }}：{{ context.store.operatingEntityName || '-' }}</p>
    <p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
    <form class="wallet-form" @submit.prevent="save">
      <p v-if="saved" role="status">{{t('wallet.operation.methodsSaved')}}</p>
      <fieldset v-for="(method,index) in (configured?methods:[])" :key="method.code??index" :disabled="busy" class="wallet-method-row"><legend>{{index+1}}</legend><label>{{t('wallet.operation.methodName')}}<input v-model.trim="method.name" required maxlength="60" @input="saved=false"></label><label>{{t('wallet.operation.methodType')}}<select v-model="method.type" :disabled="!!method.code"><option v-for="type in types" :key="type" :value="type">{{channelLabel(t,type)}}</option></select></label><div class="wallet-actions"><button type="button" :disabled="index===0||busy" @click="move(index,-1)">{{t('wallet.operation.moveUp')}}</button><button type="button" :disabled="index===methods.length-1||busy" @click="move(index,1)">{{t('wallet.operation.moveDown')}}</button><button type="button" :disabled="busy||methods.length===1" @click="methods.splice(index,1);saved=false">{{t('wallet.operation.removeMethod')}}</button></div></fieldset>
      <button v-if="configured" type="button" :disabled="busy||methods.length>=30" @click="methods.push({name:'',type:'other'});saved=false">{{t('wallet.operation.addMethod')}}</button><p v-if="!valid" class="wallet-warning">{{t('wallet.operation.methodsInvalid')}}</p>
      <button class="wallet-primary" :disabled="busy || !valid || !!context.denialReason || !context.store.operatingEntityId">{{ configured ? t('wallet.save') : t('wallet.configure') }}</button><button type="button" :disabled="busy" @click="reload">{{t('wallet.refresh')}}</button>
    </form>
  </section>
</template>

<style scoped>.wallet-method-row{min-width:0;margin:0;padding:12px;border:1px solid #dbe3ef;border-radius:14px;display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:10px}.wallet-method-row legend{padding:0 6px;color:#63738c}.wallet-method-row label{min-width:0;margin:0}.wallet-method-row input,.wallet-method-row select{min-height:42px;width:100%}.wallet-method-row .wallet-actions{grid-column:1/-1;margin:0;gap:8px}.wallet-method-row button{padding:8px 12px;min-height:38px}</style>
