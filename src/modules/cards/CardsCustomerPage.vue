<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useCardsWorkspace} from './useCardsWorkspace'
import {useCardText} from './text'
import {displayName,money,businessDay,cardCode,dateTime} from './format'
import type {CardRequest,QrCode} from './types'
import CardHistory from './CardHistory.vue'
import CardPresentation from './CardPresentation.vue'
import WalletIcon from '../wallet/components/WalletIcon.vue'
import type {Holding} from './types'
import WalletQrScanner from '../wallet/components/WalletQrScanner.vue'
import WalletQrDisplay from '../wallet/components/WalletQrDisplay.vue'
import './cards.css'
import './cards-customer.css'
const {t,locale}=useCardText(),route=useRoute(),router=useRouter(),{store,busy,error,notice,plans,holdings,entries,purchases,pending,api,run,load,recover,loadMore,moreHoldings,moreEntries,morePurchases}=useCardsWorkspace(true),tab=ref('holdings'),scanner=ref(false),request=ref<CardRequest|null>(null),selected=ref(''),authorization=ref<QrCode|null>(null)
const presented=ref<Holding|null>(null)
watch(store,()=>{presented.value=null})
const choices=computed(()=>holdings.value.filter(h=>request.value&&h.planId===request.value.planId&&(!request.value.cardId||request.value.cardId===h.id)&&h.status==='active'))
const chosen=computed(()=>choices.value.find(h=>h.id===selected.value)),currentPlan=computed(()=>plans.value.find(p=>p.id===request.value?.planId)??chosen.value?.snapshot)
async function scan(value:string){const code=cardCode(value,window.location.origin);if(!code)throw new Error(t('noCard'));if(value.startsWith('http')){const url=new URL(value),parts=url.pathname.split('/');if(parts[2]!==store.value){await router.push(url.pathname+url.hash);return}}
 request.value=await api().post<CardRequest>('/scan',{code});await load();selected.value=choices.value[0]?.id??'';authorization.value=null;scanner.value=false
}
watch(()=>route.hash,hash=>{const code=new URLSearchParams(hash.slice(1)).get('card');if(code)void run(()=>scan(code))},{immediate:true})
watch(store,()=>{request.value=null;authorization.value=null;selected.value='';scanner.value=false})
async function confirm(authorize=false){const h=chosen.value,r=request.value;if(!h||!r)return;await run(async()=>{const body={cardId:h.id,businessDay:businessDay(h.timezone)};if(authorize){authorization.value=await api().post<QrCode>(`/requests/${r.id}/authorize`,body)}else{request.value=await api().command<CardRequest>(`/requests/${r.id}/confirm`,body,'request.confirm');authorization.value=null;await load()}})}
</script>
<template><div class="cards-page cards-customer-page"><div class="cards-customer-heading cards-page-tools" role="group" :aria-label="t('title')"><div class="cards-customer-tools"><button class="primary" :disabled="busy||pending" :aria-expanded="scanner" @click="scanner=!scanner">{{t('scan')}}</button></div></div><p v-if="error" class="cards-error" role="alert">{{error}}</p><p v-if="notice" role="status">{{t('recovered')}}</p><button v-if="pending" :disabled="busy" @click="run(recover)">{{t('recover')}}</button><p v-if="busy" role="status">{{t('loading')}}</p><p class="cards-customer-policy">{{t('dailyHint')}}</p><WalletQrScanner v-if="scanner" purpose="cardMerchant" auto-start :disabled="busy||pending" @code="v=>run(()=>scan(v))"/>
<section v-if="request" class="cards-panel"><h2>{{t('request')}} · {{t(request.kind==='stamp'?'stamp':'redeem')}}</h2><p v-if="currentPlan"><strong>{{displayName(currentPlan,locale)}}</strong> · {{displayName(currentPlan.product,locale)}} × {{request.quantity}} {{currentPlan.product.unit}}</p><p>{{t(request.status)}} · {{t('expires')}} {{dateTime(request.expiresAt,chosen?.timezone)}}</p><template v-if="request.status==='pending'"><label>{{t('holdings')}}<select v-model="selected"><option value="">{{t('select')}}</option><option v-for="h in choices" :key="h.id" :value="h.id">{{displayName(h.snapshot,locale)}} · {{t(h.kind)}} · {{h.available}}</option></select></label><p v-if="!choices.length">{{t('noCard')}}</p><div v-if="chosen" class="cards-notice">{{t('product')}}: {{displayName(chosen.snapshot.product,locale)}} × {{request.quantity}} {{chosen.snapshot.product.unit}}<br>{{t('remaining')}}: {{chosen.remaining??t('unlimited')}} → {{chosen.remaining===null?t('unlimited'):chosen.remaining-request.quantity}}<br>{{t('today')}}: {{chosen.available}}</div><div class="cards-actions"><button class="primary" :disabled="busy||pending||!chosen" @click="confirm()">{{t('confirm')}}</button><button v-if="request.kind==='redeem'" :disabled="busy||pending||!chosen" @click="confirm(true)">{{t('authorize')}}</button></div><div v-if="authorization" class="cards-qr"><p>{{t('authorizeHint')}}</p><WalletQrDisplay :value="authorization.code" :expires-at="authorization.expiresAt"/><button v-if="(tab==='records'&&moreEntries)||(tab==='purchases'&&morePurchases)||(tab==='holdings'&&moreHoldings)" :disabled="busy" @click="run(()=>loadMore(tab==='records'?'entries':tab==='purchases'?'purchases':'holdings'))">{{t('loadMore')}}</button></div></template></section>
<CardPresentation v-if="presented" :key="`${store}:${presented.id}`" :store="store" :holding="presented" @close="presented=null" @completed="run(load)" @pending="pending=!!api().pending()"/><nav class="cards-customer-tabs" :aria-label="t('title')"><button v-for="v in ['holdings','plans','purchases','records']" :key="v" :class="{primary:tab===v}" :aria-pressed="tab===v" @click="tab=v">{{t(v==='holdings'?'myCards':v==='plans'?'getCards':v==='records'?'usageRecords':v)}}</button></nav>
<section v-if="tab==='holdings'" :aria-label="t('myCards')">
  <p v-if="!holdings.length" class="cards-customer-empty">{{t('noHoldings')}}</p>
  <div v-else class="cards-customer-grid">
    <article v-for="h in holdings" :key="h.id" class="cards-panel cards-customer-card">
      <div class="cards-customer-card-heading"><h2>{{displayName(h.snapshot,locale)}}</h2><span class="cards-badge" :data-status="h.status==='active'?(Date.now()>=Date.parse(h.expiresAt)?'expired':'valid'):h.status">{{t(h.status==='active'?(Date.now()>=Date.parse(h.expiresAt)?'expired':'valid'):h.status)}}</span></div>
      <p class="cards-customer-meta">{{t(h.kind)}} · {{displayName(h.snapshot.product,locale)}} / {{h.snapshot.product.unit}}</p>
      <dl class="cards-customer-balances"><div class="cards-customer-today"><dt>{{t('today')}}</dt><dd>{{h.available}}</dd></div><div><dt>{{t('remaining')}}</dt><dd>{{h.remaining??t('unlimited')}}</dd></div></dl>
      <div v-if="h.kind==='stamp'" class="cards-customer-progress"><div><span>{{t('progress')}}</span><strong>{{h.progress}} / {{h.snapshot.stampTarget}}</strong></div><progress :aria-label="t('progress')" :value="h.progress" :max="h.snapshot.stampTarget" /></div>
      <div class="cards-customer-card-footer"><small>{{t('expires')}} {{dateTime(h.expiresAt,h.timezone)}}</small><button type="button" class="cards-customer-code" :aria-label="`${t('showCard')} · ${displayName(h.snapshot,locale)}`" :disabled="busy||pending||h.status!=='active'||Date.now()>=Date.parse(h.expiresAt)||Date.now()<Date.parse(h.startsAt)||h.available<1" @click="presented=h"><WalletIcon name="qr" :size="18"/><span>{{t('showCard')}}</span></button></div>
    </article>
  </div>
</section>
<section v-if="tab==='plans'" :aria-label="t('getCards')">
  <p class="cards-customer-policy">{{t('buyHint')}}</p><p v-if="!plans.length" class="cards-customer-empty">{{t('noPlans')}}</p>
  <div v-else class="cards-customer-grid">
    <article v-for="p in plans" :key="p.id" class="cards-panel cards-customer-card">
      <div class="cards-customer-card-heading"><h2>{{displayName(p,locale)}}</h2><span class="cards-badge">{{t(p.kind)}}</span></div>
      <p class="cards-customer-meta">{{displayName(p.product,locale)}} / {{p.product.unit}}</p>
      <div class="cards-customer-price"><strong>{{money(p.priceCents)}}</strong><small>{{t(p.taxMode)}}</small></div>
      <dl class="cards-customer-plan-rules"><div><dt>{{t('validity')}}</dt><dd>{{p.validDays}} {{t('daysUnit')}}</dd></div><div><dt>{{t('dailyAllowance')}}</dt><dd>{{p.dailyLimit??t('unlimited')}}</dd></div></dl>
      <p v-if="p.kind==='stamp'" class="cards-customer-reward">{{t('target')}} {{p.stampTarget}} → {{displayName(p.product,locale)}} × 1</p>
      <div class="cards-customer-plan-footer"><button class="primary" :disabled="busy||pending" @click="run(async()=>{await api().command(p.kind==='stamp'?'/enroll':'/purchases',{planId:p.id},p.kind==='stamp'?'enroll':'purchase.create');await load();tab=p.kind==='stamp'?'holdings':'purchases'})">{{t(p.kind==='stamp'?'claim':'buy')}}</button></div>
    </article>
  </div>
</section>
<section v-if="tab==='purchases'" class="cards-panel cards-customer-orders" :aria-label="t('purchases')">
  <p class="cards-customer-policy">{{t('buyHint')}}</p><p v-if="!purchases.length" class="cards-customer-empty">{{t('noOrders')}}</p>
  <article v-for="p in purchases" :key="p.id" class="cards-customer-list-item"><div class="cards-customer-card-heading"><h2>{{displayName(p.snapshot,locale)}}</h2><span class="cards-badge" :data-status="p.status">{{t(p.status)}}</span></div><div class="cards-customer-order-amount"><span>{{t('amount')}}</span><strong>{{money(p.amountCents)}}</strong></div><div class="cards-customer-order-meta"><span>{{t('expires')}} {{dateTime(p.expiresAt)}}</span><span>{{t('orderNumber')}} {{p.id}}</span></div></article>
</section>
<CardHistory v-if="tab==='records'" :store="store" :entries="entries" :holdings="holdings" customer/>
<button v-if="(tab==='records'&&moreEntries)||(tab==='purchases'&&morePurchases)||(tab==='holdings'&&moreHoldings)" class="cards-customer-load-more" :disabled="busy" @click="run(()=>loadMore(tab==='records'?'entries':tab==='purchases'?'purchases':'holdings'))">{{t('loadMore')}}</button>
</div></template>
