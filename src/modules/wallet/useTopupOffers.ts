import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import { walletApi } from './api'
import { normalizeOffers, type OfferSet, type TopupQuote } from './marketingTypes'
import { useWalletTask } from './useWalletTask'
export function useTopupOffers(store:Ref<string>, customer=false, accountId?:Ref<string|undefined>){
 const task=useWalletTask(), offers=ref<OfferSet|null>(null), selected=ref(''), channel=ref('cash'),quote=ref<TopupQuote|null>(null),now=ref(Date.now()), loaded=ref(false)
 let generation=0
 const expired=computed(()=>!quote.value||Date.parse(quote.value.expiresAt)<=now.value)
 const timer=window.setInterval(()=>{now.value=Date.now()},1000)
 onUnmounted(()=>{generation++;window.clearInterval(timer)})
 async function refresh(){const current=++generation;offers.value=null;quote.value=null;selected.value='';loaded.value=false;task.busy.value=true;task.error.value='';try{const result=normalizeOffers(await walletApi(store.value,customer).get<OfferSet>('/topup-offers'));if(current!==generation)return;offers.value=result;loaded.value=true;if(!result.channels.includes(channel.value as never))channel.value=result.channels[0]??'cash'}catch(e){if(current===generation)task.error.value=e instanceof Error?e.message:String(e)}finally{if(current===generation)task.busy.value=false}}
 async function getQuote(){if(!selected.value)return;const offer=offers.value?.offers.find(o=>o.id===selected.value);if(!offer)return;const current=generation,choice=selected.value,chosenChannel=channel.value,chosenAccount=accountId?.value;quote.value=null;await task.run(async()=>{const result=await walletApi(store.value,customer).post<TopupQuote>('/topup-quotes',{offerId:offer.id,offerVersion:offer.version,channel:channel.value,...(!customer?{accountId:accountId?.value}:{})});if(current===generation&&choice===selected.value&&chosenChannel===channel.value&&chosenAccount===accountId?.value)quote.value=result})}
 watch([selected,channel,()=>accountId?.value],()=>{quote.value=null})
 watch(store,()=>{void refresh()},{immediate:true})
 return {...task,offers,selected,channel,quote,expired,loaded,refresh,getQuote}
}
