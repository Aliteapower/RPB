<script setup lang="ts">
import {ref} from 'vue'
import type {Home,Application} from '../types'
import type {StorageApi} from '../api'
import StorageReceiveForm from '../StorageReceiveForm.vue'
import {useMerchantStorageText} from './text'
const props=defineProps<{home:Home;api:StorageApi;store:string;customerId:string;disabled:boolean;canReceive:boolean}>()
const emit=defineEmits<{saved:[];error:[unknown];'busy-change':[boolean]}>(),{t}=useMerchantStorageText()
function draft():Application{return{id:'',customerId:props.customerId,status:'pending',lines:[{id:crypto.randomUUID(),productId:null,nameZh:'',nameEn:'',quantity:1,opened:false,source:'customer',photos:[],note:''}],confirmation:null,reason:'',createdAt:new Date().toISOString()}}
const application=ref(draft()),formKey=ref(0)
function saved(){application.value=draft();formKey.value++;emit('saved')}
</script>
<template><section class="storage-section"><p class="cards-muted">{{t('directHint')}}</p><StorageReceiveForm :key="formKey" direct :application="application" :home="home" :api="api" :disabled="disabled||!canReceive||!home.newBusinessEnabled" @saved="saved" @error="emit('error',$event)" @busy-change="emit('busy-change',$event)"/></section></template>
