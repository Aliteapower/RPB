<script setup lang="ts">
import {ref} from 'vue'
import type {Home,Application} from '../types'
import type {StorageApi} from '../api'
import StorageReceiveForm from '../StorageReceiveForm.vue'
import {displayName} from '../../shared-commerce/format'
import {dateTime} from '../format'
import {useMerchantStorageText} from './text'
const props=defineProps<{home:Home;api:StorageApi;store:string;customerId:string;disabled:boolean;canReceive:boolean}>()
const emit=defineEmits<{saved:[];error:[unknown];'busy-change':[boolean]}>(),{t,locale}=useMerchantStorageText(),selected=ref<Application|null>(null),direct=ref(false),formBusy=ref(false)
function register(){direct.value=true;selected.value={id:'',customerId:props.customerId,status:'pending',lines:[{id:crypto.randomUUID(),productId:null,nameZh:'',nameEn:'',quantity:1,opened:false,source:'customer',photos:[],note:''}],confirmation:null,reason:'',createdAt:new Date().toISOString()}}
</script>
<template><section class="storage-section">
 <template v-if="selected"><button :disabled="disabled||formBusy" @click="selected=null">← {{t(direct?'backDeposit':'backApplications')}}</button><StorageReceiveForm :direct="direct" :application="selected" :home="home" :api="api" :disabled="disabled||!canReceive||!home.newBusinessEnabled" @saved="selected=null;emit('saved')" @error="emit('error',$event)" @busy-change="formBusy=$event;emit('busy-change',$event)"/></template>
 <section v-else class="storage-panel"><h2>{{t('directRegister')}}</h2><p>{{t('directHint')}}</p><button class="primary" :disabled="disabled||!canReceive||!home.newBusinessEnabled" @click="register">{{t('directRegister')}}</button><h2>{{t('pendingApplications')}}</h2><p v-if="!home.newBusinessEnabled" class="storage-notice">{{t('closed')}}</p>
  <template v-if="!home.applications.some(a=>a.status==='pending')"><p>{{t('noPending')}}</p></template>
  <article v-for="application in home.applications.filter(a=>a.status==='pending')" :key="application.id" class="storage-panel"><span class="cards-badge">{{t('pending')}}</span><p>{{dateTime(application.createdAt,home.timezone)}}</p><p v-for="line in application.lines" :key="line.id"><strong>{{displayName(line,locale)}}</strong> × {{line.quantity}} {{t('unit')}} · {{t(line.opened?'opened':'whole')}}</p><details><summary>{{t('original')}}</summary><div v-for="line in application.lines" :key="line.id"><p>{{line.note}}</p><div class="storage-photos"><img v-for="photo in line.photos" :key="photo" :src="`${api.base}/photos/${photo}`" :alt="t('photos')"></div></div></details><button class="primary" :disabled="disabled||!canReceive||!home.newBusinessEnabled" @click="direct=false;selected=application">{{t('reviewReceive')}}</button></article>
 </section>
</section></template>
