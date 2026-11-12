<script setup lang="ts">
import {ref,watch,onUnmounted,computed} from 'vue'
import {fetchCurrentStores} from '../../../api/authApi'
import {fetchMeApps} from '../../../api/meAppsApi'
import {useVisiblePageRefresh} from '../../../composables/useVisiblePageRefresh'
import {useAuthSessionStore} from '../../../stores/authSession'
import {useMerchantStorageText} from '../merchant/text'
import TenantNameQr from '../../tenant-entry-links/TenantNameQr.vue'
import {renderTenantEntryQr} from '../../tenant-entry-links/nameQr'
import {guideEntries,type GuideEntries} from './entries'
import guidePdf from './guide-zh.pdf'
import entryTemplate from './entry-template.pdf'
const props=withDefaults(defineProps<{store:string;enabled:boolean;compact?:boolean}>(),{compact:false})
const auth=useAuthSessionStore(),{locale}=useMerchantStorageText(),x=(zh:string,en:string)=>locale.value.startsWith('en')?en:zh
const entries=ref<GuideEntries|null>(null),loading=ref(false),error=ref(''),generating=ref(false),downloadUrl=ref(''),downloadName=ref('')
const actor=computed(()=>[auth.user?.accountId,auth.user?.tenantId].join('|'))
let generation=0,disposed=false
function clearDownload(){if(downloadUrl.value)URL.revokeObjectURL(downloadUrl.value);downloadUrl.value='';downloadName.value=''}
watch(()=>JSON.stringify([props.store,props.enabled,actor.value,entries.value?.staff,entries.value?.customer,entries.value?.storeName]),clearDownload,{flush:'sync'})
async function refresh(){
 const request=++generation,id=props.store,identity=actor.value;error.value=''
 if(!props.enabled){entries.value=null;loading.value=false;return null}loading.value=true
 try{const [directory,apps]=await Promise.all([fetchCurrentStores(),fetchMeApps(id,fetch,true)]);if(disposed||request!==generation||props.store!==id||actor.value!==identity)return null
  entries.value=guideEntries(directory.stores.find(s=>s.storeId===id),auth.user?.tenantId,props.enabled&&apps.productAccess?.some(p=>p.appKey==='wine_storage'&&p.businessAllowed)===true,window.location);return entries.value
 }catch{if(!disposed&&request===generation){entries.value=null;error.value=x('入口暂时无法校验，请重试。','Entry verification failed. Retry.')}return null}
 finally{if(!disposed&&request===generation)loading.value=false}
}
watch(()=>[props.store,props.enabled,actor.value],()=>{entries.value=null;void refresh()},{immediate:true,flush:'sync'})
useVisiblePageRefresh(refresh,()=>!loading.value&&!generating.value&&props.enabled)
onUnmounted(()=>{disposed=true;generation++;clearDownload()})
async function download(){
 if(generating.value)return;generating.value=true
 try{
  const current=await refresh(),request=generation;if(!current)return
  const [{appendGuideEntries},response,template,staff,customer]=await Promise.all([import('./entryPdf'),fetch(guidePdf),fetch(entryTemplate),renderTenantEntryQr(current.staff,current.storeName,'员工酒存入口 · 员工账号登录'),renderTenantEntryQr(current.customer,current.storeName,'顾客存酒入口 · 顾客本人登录')])
  if(!response.ok||!template.ok)throw new Error('pdf_unavailable')
  const bytes=await appendGuideEntries(new Uint8Array(await response.arrayBuffer()),current,{staff,customer},new Uint8Array(await template.arrayBuffer()))
  if(disposed||request!==generation||props.store!==current.storeId||!props.enabled)return
  clearDownload();downloadUrl.value=URL.createObjectURL(new Blob([new Uint8Array(bytes)],{type:'application/pdf'}));downloadName.value='酒水寄存操作指南-'+current.storeName.replace(/[\\/:*?"<>|\s]+/g,'-').slice(0,48)+'.pdf';const link=document.createElement('a');link.href=downloadUrl.value;link.download=downloadName.value;link.click()
 }catch{if(!disposed)error.value=x('PDF生成失败，请重试；未下载过期入口。','PDF generation failed. Retry. No stale entries were downloaded.')}
 finally{if(!disposed)generating.value=false}
}
</script>
<template><section class="storage-guide-access" :class="{compact}" :aria-label="x('本店员工及顾客入口','Store staff and customer entries')">
 <template v-if="!compact"><h3>{{x('本店入口网址与二维码','This store: URLs and QR codes')}}</h3><p class="cards-muted">{{x('员工码需员工登录，顾客码供顾客本人使用。请核对门店名称后分享。','Staff codes require staff sign-in; customer codes are for the customer. Check the store before sharing.')}}</p></template>
 <p v-if="loading" role="status">{{x('正在校验本店入口…','Verifying store entries…')}}</p><p v-else-if="error" role="alert" class="storage-error">{{error}}</p><p v-else-if="!entries" class="cards-muted">{{x('当前未开通业务或无访问权限，不提供业务入口。','Business is unavailable or access is denied. No business entries are provided.')}}</p>
 <div v-if="entries&&!compact" class="storage-guide-entry-codes"><article v-for="kind in ['staff','customer'] as const" :key="kind"><h4>{{kind==='staff'?x('员工酒存入口','Staff storage entry'):x('顾客存酒入口','Customer storage entry')}}</h4><strong>{{entries.storeName}}</strong><a :href="entries[kind]" target="_blank" rel="noopener">{{entries[kind]}}</a><TenantNameQr :value="entries[kind]" :tenant-name="entries.storeName" :caption="kind==='staff'?x('员工酒存入口','Staff storage entry'):x('顾客存酒入口','Customer storage entry')" :file-name="`storage-${kind}-${store}.png`"/></article></div>
 <button type="button" class="storage-guide-download" :disabled="generating||loading||!entries" @click="download">{{generating?x('正在生成…','Generating…'):x('下载本店指南 PDF（含入口码）','Download store guide PDF (with codes)')}}</button><a v-if="downloadUrl" :href="downloadUrl" :download="downloadName" class="storage-guide-download">{{x('PDF已生成，点击下载 / 保存','PDF ready: download / save')}}</a><button v-if="error&&!loading" type="button" :disabled="generating" @click="refresh">{{x('重试入口校验','Retry verification')}}</button>
</section></template>
