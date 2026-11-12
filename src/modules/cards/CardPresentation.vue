<script setup lang="ts">
import {onMounted,onUnmounted,ref} from 'vue'
import {cardApi} from './api'
import {businessDay,dateTime,displayName} from './format'
import {useCardText} from './text'
import type {Holding,CardRequest,QrCode} from './types'
import WalletQrDisplay from '../wallet/components/WalletQrDisplay.vue'
const props=defineProps<{store:string;holding:Holding}>(),emit=defineEmits<{close:[];completed:[];pending:[]}>(),{t,locale}=useCardText()
const dialog=ref<HTMLDialogElement|null>(null)
const quantity=ref(1),code=ref<(QrCode&{requestId:string})|null>(null),busy=ref(false),error=ref(''),now=ref(Date.now()),done=ref(false)
let active=true,polling=false
async function generate(){
 if(busy.value||code.value&&now.value<Date.parse(code.value.expiresAt))return
 busy.value=true;error.value=''
 try{const result=await cardApi(props.store,true).command<QrCode&{requestId:string}>(`/holdings/${props.holding.id}/presentation`,{quantity:quantity.value,businessDay:businessDay(props.holding.timezone)},'request.present');if(active)code.value=result}
 catch(e){if(active)error.value=e instanceof Error?e.message:String(e)}finally{if(active){busy.value=false;emit('pending')}}
}
async function tick(){
 now.value=Date.now();const current=code.value
 if(!current||done.value||polling)return
 polling=true
 try{const result=await cardApi(props.store,true).get<CardRequest>(`/requests/${current.requestId}`);if(active&&code.value===current&&result.status==='completed'){done.value=true;code.value=null;emit('completed')}}
 catch{/* The code expires locally; polling resumes after connection recovery. */}finally{polling=false}
}
let timer:number
onMounted(()=>{dialog.value?.showModal();timer=window.setInterval(()=>void tick(),2000)})
onUnmounted(()=>{active=false;window.clearInterval(timer)})
</script>
<template>
 <dialog ref="dialog" class="cards-presentation" aria-labelledby="card-presentation-title" @cancel.prevent="emit('close')">
  <div class="cards-card-heading"><h2 id="card-presentation-title">{{t('showCard')}}</h2><button type="button" @click="emit('close')">{{t('close')}}</button></div>
  <h3>{{displayName(holding.snapshot,locale)}}</h3><p>{{displayName(holding.snapshot.product,locale)}} / {{holding.snapshot.product.unit}}</p>
  <p v-if="done" role="status">{{t('completed')}}</p>
  <template v-else>
   <p class="cards-notice">{{t(holding.kind==='stamp'?'stampCodeHint':'cardCodeHint')}}</p>
   <label>{{t('quantity')}}<input v-model.number="quantity" type="number" min="1" :max="holding.kind==='stamp'?1:Math.min(holding.available,1000000)" :disabled="busy||holding.kind==='stamp'||!!code" /></label>
   <p v-if="error" class="cards-error" role="alert">{{error}}</p>
   <div v-if="code" class="cards-qr"><WalletQrDisplay :value="code.code" :expires-at="code.expiresAt"/><p>{{t('expires')}} {{dateTime(code.expiresAt,holding.timezone)}}</p></div>
   <button type="button" class="primary" :disabled="busy||!!code&&now<Date.parse(code.expiresAt)||!Number.isInteger(quantity)||quantity<1||quantity>holding.available" @click="generate">{{t(busy?'loading':code?'refreshCode':'authorize')}}</button>
  </template>
 </dialog>
</template>
