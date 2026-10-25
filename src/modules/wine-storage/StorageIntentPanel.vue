<script setup lang="ts">
import {useStorageText} from './text'
import {dateTime} from './format'
import WalletQrDisplay from '../wallet/components/WalletQrDisplay.vue'
import type {Intent,Item} from './types'
defineProps<{intent:Intent;items:Item[];customer:boolean;timezone:string;disabled:boolean;canDeliver:boolean;authorizationCode?:string}>()
const emit=defineEmits<{authorize:[];deliver:[];cancel:[];again:[{item:Item;intentId:string;quantity:number}]}>(),{t,locale}=useStorageText()
function name(item:Item|undefined){return item?(locale.value.startsWith('en')?item.snapshot.nameEn||item.snapshot.nameZh:item.snapshot.nameZh||item.snapshot.nameEn):'—'}
</script>
<template><article class="storage-panel"><strong>{{t(intent.status)}}</strong><small>{{intent.id}}</small><p v-if="intent.status!=='completed'">{{t('expiresAt')}} {{dateTime(intent.expiresAt,timezone)}} ({{timezone}})</p><div v-for="s in intent.selections" :key="s.itemId"><p>{{name(items.find(i=>i.id===s.itemId))}} × {{s.quantity}}</p><small>{{s.itemId}}</small><button v-if="customer&&intent.status==='completed'&&items.find(i=>i.id===s.itemId)" :disabled="disabled" @click="emit('again',{item:items.find(i=>i.id===s.itemId)!,intentId:intent.id,quantity:s.quantity})">{{t('again')}}</button></div><template v-if="intent.code"><WalletQrDisplay :value="intent.code" :expires-at="intent.expiresAt"/><p>{{t('codeHint')}}</p></template><button v-if="customer&&intent.status==='awaiting_authorization'&&authorizationCode" :disabled="disabled" @click="emit('authorize')">{{t('authorize')}}</button><button v-if="!customer&&canDeliver&&intent.status==='authorized'" :disabled="disabled" @click="emit('deliver')">{{t('deliver')}}</button><button v-if="['authorized','awaiting_authorization'].includes(intent.status)&&(customer||canDeliver)" :disabled="disabled" @click="emit('cancel')">{{t('cancel')}}</button><p v-if="intent.completedAt">{{dateTime(intent.completedAt,timezone)}}</p></article></template>
