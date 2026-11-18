<script setup lang="ts">
import {ref} from 'vue'
import type {StorageCustomer} from './scope'
import {useMerchantStorageText} from './text'
const props=defineProps<{customer:StorageCustomer|null;results:StorageCustomer[];query:string;busy:boolean;searching:boolean;searched:boolean;canCreate?:boolean}>()
const emit=defineEmits<{'update:query':[string];search:[];create:[];clear:[];choose:[StorageCustomer|null]}>()
const {t,locale}=useMerchantStorageText()
const keypad=ref(true),searchInput=ref<HTMLInputElement|null>(null)
const maxQueryLength=120
const keys=['7','8','9','4','5','6','1','2','3','0','C','<']
const label=(zh:string,en:string)=>locale.value.startsWith('en')?en:zh
const keyLabel=(key:string)=>key==='C'?label('清空输入','Clear input'):key==='<'?label('退格','Backspace'):key

function press(key:string){
 if(props.busy||props.searching)return
 if(key==='C'){emit('update:query','');return}
 if(key==='<'){emit('update:query',props.query.slice(0,-1));return}
 if(props.query.length<maxQueryLength)emit('update:query',props.query+key)
}

function toggleKeypad(){
 const input=searchInput.value
 input?.blur()
 keypad.value=!keypad.value
 if(keypad.value||!input)return
 // Re-enter text input during the tap, even if the phone field was already focused.
 input.setAttribute('inputmode','text')
 input.focus()
}
</script>
<template><section class="storage-panel">
 <template v-if="customer"><div class="storage-inline"><div><small>{{t('selected')}}</small><h2>{{customer.displayName}}</h2><span>{{customer.phone}}</span></div><button :disabled="busy" @click="emit('choose',null)">{{t('change')}}</button><button type="button" :disabled="busy" @click="emit('clear')">{{t('clearCustomer')}}</button></div></template>
 <template v-else>
  <div class="storage-picker-heading"><h2>{{t('identify')}}</h2><button v-if="canCreate" type="button" :disabled="busy||searching" @click="emit('create')">＋ {{t('addCustomer')}}</button></div>
  <form class="storage-inline storage-merchant-customer-search" @submit.prevent="emit('search')">
   <label>{{t('searchCustomer')}}<input ref="searchInput" :value="query" :inputmode="keypad?'none':'text'" :disabled="busy||searching" @input="emit('update:query',($event.target as HTMLInputElement).value)" :maxlength="maxQueryLength" autocomplete="off"></label>
   <button class="primary" type="submit" :disabled="busy||searching||!query.trim()">{{t(searching?'loading':'search')}}</button>
   <div class="storage-phone-entry">
    <div v-if="keypad" class="storage-phone-keypad" role="group" :aria-label="label('数字键盘','Number keypad')">
     <button v-for="key in keys" :key="key" type="button" :data-phone-key="key" :aria-label="keyLabel(key)" :disabled="busy||searching" @click="press(key)">{{key}}</button>
    </div>
    <button type="button" :disabled="busy||searching" @click="toggleKeypad">{{keypad?label('输入姓名','Enter name'):label('数字键盘','Number keypad')}}</button>
   </div>
  </form>
  <p v-if="searched&&!searching&&!results.length" role="status">{{t('noMatches')}}</p>
  <div class="storage-product-options"><button v-for="entry in results" :key="entry.id" :disabled="busy" @click="emit('choose',entry)"><strong>{{entry.displayName}}</strong><small>{{entry.phone}}</small><span>{{t('choose')}}</span></button></div>
 </template>
</section></template>

<style scoped>
.storage-phone-entry{grid-column:1/-1;display:flex;flex-direction:column;align-items:flex-start;gap:8px}
.storage-phone-keypad{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:6px;width:min(100%,20rem)}
.storage-phone-keypad button{min-width:0;min-height:44px;font-size:1.125rem;font-weight:600;touch-action:manipulation}
</style>
