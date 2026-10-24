import { onUnmounted, ref, watch } from 'vue'
import { useStaffWallet } from './useStaffWallet'
import { useWalletTask } from './useWalletTask'
import { walletApi } from './api'
import { campaignLocalTime, campaignInstant } from './marketingTime'
import { rows, type Campaign, type MarketingSettings, type TopupOffer } from './marketingTypes'
export function useWalletMarketing(){
const {store,can,context}=useStaffWallet(),{busy,error,run}=useWalletTask(),settings=ref<MarketingSettings|null>(null),campaigns=ref<Campaign[]>([]),ready=ref(false),status=ref(''),reason=ref(''),editing=ref<Campaign|null>(null),form=ref({name:'',startsAt:'',endsAt:'',priority:0,terms:'',offers:[] as TopupOffer[]}),more=ref(false),nextCursor=ref('')
let generation=0
const permitted=()=>can('wallet.marketing.view')||can('wallet.marketing.manage')
async function load(append=false){if(!permitted())return;const current=generation;await run(async()=>{const api=walletApi(store.value);const [config,list]=await Promise.all([api.get<MarketingSettings>('/marketing/settings'),api.get<{items:Campaign[];nextCursor:string}>(`/marketing/campaigns?limit=50&cursor=${encodeURIComponent(append?nextCursor.value:'')}&status=${encodeURIComponent(status.value)}`)]);if(current!==generation)return;settings.value=config;const result=rows(list);campaigns.value=append?[...campaigns.value,...result]:result;nextCursor.value=list.nextCursor??'';more.value=!!nextCursor.value;ready.value=true})}
async function saveSettings(){if(!settings.value)return;await run(async()=>{settings.value=await walletApi(store.value).command<MarketingSettings>('/marketing/settings',{expectedVersion:settings.value!.version,baseOffers:settings.value!.baseOffers.map(o=>({amount:(o.principalCents/100).toFixed(2),bonus:(o.bonusCents/100).toFixed(2)})),deductionPolicy:settings.value!.deductionPolicy,campaignsEnabled:settings.value!.campaignsEnabled??true,selfServiceEnabled:settings.value!.selfServiceEnabled,pointsEnabled:settings.value!.pointsEnabled},'PUT')})}
function local(value:string){return campaignLocalTime(value,context.value?.store.timezone??'Asia/Singapore')}
async function edit(c?:Campaign){if(c){await run(async()=>{const detail=await walletApi(store.value).get<Campaign>(`/marketing/campaigns/${c.id}`);editing.value=detail;form.value={name:detail.name,startsAt:local(detail.startsAt),endsAt:local(detail.endsAt),priority:detail.priority,terms:detail.terms??'',offers:structuredClone(detail.offers)}})}else{editing.value=null;form.value={name:'',startsAt:'',endsAt:'',priority:0,terms:'',offers:[]}}}
async function save(){await run(async()=>{const body={...form.value,offers:form.value.offers.map(o=>({amount:(o.principalCents/100).toFixed(2),bonus:(o.bonusCents/100).toFixed(2)})),startsAt:campaignInstant(form.value.startsAt,context.value?.store.timezone??'Asia/Singapore'),endsAt:campaignInstant(form.value.endsAt,context.value?.store.timezone??'Asia/Singapore'),expectedVersion:editing.value?.version??0};const result=await walletApi(store.value).command<Campaign>(editing.value?`/marketing/campaigns/${editing.value.id}/draft`:'/marketing/campaigns',body,editing.value?'PUT':'POST');editing.value=result;campaigns.value=[result,...campaigns.value.filter(c=>c.id!==result.id)]})}
async function action(c:Campaign,kind:'publish'|'disable'|'archive'){if(kind!=='publish'&&!reason.value.trim())return;await run(async()=>{const result=await walletApi(store.value).command<Campaign>(`/marketing/campaigns/${c.id}/${kind}`,{expectedVersion:c.version,...(kind==='publish'?{draftVersion:c.draftVersion}:{reason:reason.value})});campaigns.value=campaigns.value.map(old=>old.id===result.id?result:old);if(editing.value?.id===result.id)editing.value=result;reason.value=''})}
watch(store,()=>{generation++;settings.value=null;campaigns.value=[];ready.value=false;editing.value=null;void load()},{immediate:true})

onUnmounted(()=>{generation++})
return {store,can,context,busy,error,settings,campaigns,ready,status,reason,editing,form,more,permitted,load,saveSettings,edit,save,action}
}
