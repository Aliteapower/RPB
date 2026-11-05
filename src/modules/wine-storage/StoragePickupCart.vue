<script setup lang="ts">
import {computed,onUnmounted,ref,watch} from 'vue'
import {useStorageText} from './text'
import type {Item,Event,Selection} from './types'
import type {StorageApi} from './api'
import {collectibleItems} from './customer/selection'
import {cartSelection,cartTotals} from './pickupCart'
import {displayName} from '../shared-commerce/format'
import {storageExpiryText,storageLocationText} from './format'
import StorageItemPhotos from './StorageItemPhotos.vue'
import {itemPhotos} from './itemPhotos'
const props=defineProps<{items:Item[];events?:Event[];api:StorageApi;scope:string;disabled:boolean;submitLabel:string;submitHint:string;resetKey?:number}>()
const emit=defineEmits<{submit:[Selection[]]}>(),{t,locale}=useStorageText(),x=(zh:string,en:string)=>locale.value.startsWith('en')?en:zh
const quantities=ref<Record<string,number>>({}),snapshots=ref<Record<string,Item>>({}),inCart=ref(false),notice=ref(''),now=ref(Date.now())
const timer=setInterval(()=>{now.value=Date.now()},1000)
onUnmounted(()=>clearInterval(timer))
function clear(){quantities.value={};snapshots.value={};inCart.value=false;notice.value=''}
watch(()=>[props.scope,props.resetKey],clear,{flush:'sync'})
const totals=computed(()=>cartTotals(quantities.value)),selection=computed(()=>cartSelection(props.items,quantities.value,now.value))
const rows=computed(()=>inCart.value?Object.keys(quantities.value).filter(id=>quantities.value[id]>0).map(id=>props.items.find(i=>i.id===id)??snapshots.value[id]).filter((i):i is Item=>!!i):collectibleItems(props.items,now.value))
function quantity(id:string){return quantities.value[id]??0}
function maximum(item:Item){return item.opened?Math.min(1,item.remaining):item.remaining}
function change(item:Item,value:number){
 if(props.disabled)return
 const next=Math.min(Math.max(0,Number.isFinite(value)?Math.trunc(value):0),Math.max(0,maximum(item)))
 if(next>0&&!quantity(item.id)&&totals.value.items>=50){notice.value=x('一次最多选择50款酒。','Choose up to 50 holdings per cart.');return}
 notice.value='';snapshots.value[item.id]=item;quantities.value={...quantities.value,[item.id]:next}
}
function submit(){if(!props.disabled&&selection.value)emit('submit',selection.value)}
</script>
<template>
 <section class="storage-pickup-cart">
  <header class="pickup-toolbar"><h2>{{inCart?x('取酒车','Pickup cart'):x('选择要取的酒','Choose bottles')}}</h2><button v-if="inCart" type="button" :disabled="disabled" @click="inCart=false">{{x('继续选酒','Continue choosing')}}</button><button v-else type="button" :disabled="disabled||!totals.items" @click="inCart=true">{{x('取酒车','Pickup cart')}} · {{totals.bottles}}</button></header>
  <p v-if="notice" role="alert" class="storage-error">{{notice}}</p>
  <p v-if="!rows.length" class="storage-empty">{{inCart?x('取酒车为空，请先选择酒品。','Your cart is empty. Choose bottles first.'):x('暂无可领取的存酒。','No collectible bottles.')}}</p>
  <div class="pickup-rows"><article v-for="item in rows" :key="item.id" class="pickup-row">
   <div class="pickup-product">
    <div class="pickup-photo"><StorageItemPhotos v-if="itemPhotos(item,events).length" :item="item" :events="events" :api="api"/><span v-else class="pickup-no-photo" :aria-label="x('暂无照片','No photo')"><svg width="28" height="42" viewBox="0 0 28 42" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M10 2h8v12l4 5v20H6V19l4-5V2Z"/><path d="M6 25h16M10 6h8"/></svg></span><small v-if="itemPhotos(item,events).length>1">{{itemPhotos(item,events).length}}{{x('张照片',' photos')}}</small></div>
    <div class="pickup-copy"><h3>{{displayName(item.snapshot,locale)}}</h3><small>{{t('remaining')}} {{item.remaining}} {{x('瓶','bottles')}}</small>
     <div class="pickup-quantity" role="group" :aria-label="x('取酒数量','Pickup quantity')"><button type="button" :aria-label="x('减少瓶数','Decrease bottles')" :disabled="disabled||quantity(item.id)<=0" @click="change(item,quantity(item.id)-1)">−</button><input type="number" inputmode="numeric" :aria-label="x('取酒数量','Pickup quantity')" :value="quantity(item.id)" min="0" :max="maximum(item)" step="1" :disabled="disabled" @input="change(item,Number(($event.target as HTMLInputElement).value))"><button type="button" :aria-label="x('增加瓶数','Increase bottles')" :disabled="disabled||quantity(item.id)>=maximum(item)" @click="change(item,quantity(item.id)+1)">+</button></div>
    </div>
   </div>
   <p class="pickup-description">{{t('location')}} {{storageLocationText(item)||'—'}}<template v-if="item.label"> · {{t('label')}} {{item.label}}</template> · {{t('expiresAt')}} {{storageExpiryText(item.expiresAt,item.snapshot.timezone,t('unlimited'))}}</p>
   <button v-if="inCart" type="button" class="pickup-remove" :disabled="disabled" @click="change(item,0)">{{x('移出取酒车','Remove')}}</button>
  </article></div>
  <p v-if="totals.items&&!selection" class="storage-error" role="alert">{{x('存酒数量或状态已变化，请调整取酒车后再提交。','Stock or eligibility changed. Adjust your cart before submitting.')}}</p>
  <footer class="pickup-footer"><strong>{{x('已选','Selected')}} {{totals.items}} {{x('款','holdings')}} · {{totals.bottles}} {{x('瓶','bottles')}}</strong><template v-if="inCart"><p>{{submitHint}}</p><button type="button" class="primary" :disabled="disabled||!selection" @click="submit">{{submitLabel}}</button></template><button v-else type="button" class="primary" :disabled="disabled||!totals.items" @click="inCart=true">{{x('查看取酒车','View pickup cart')}}</button></footer>
 </section>
</template>
<style scoped>
.pickup-toolbar{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px}.pickup-toolbar h2{margin:0}.pickup-rows{display:grid;gap:8px}.pickup-row{padding:10px;border:1px solid var(--wallet-line,#d9e4e6);border-radius:10px;background:#fff;min-width:0}.pickup-product{display:grid;grid-template-columns:72px minmax(0,1fr);gap:12px}.pickup-photo{min-width:0}.pickup-photo :deep(.storage-photos){flex-wrap:nowrap;overflow-x:auto;margin:0;width:72px;gap:6px;scroll-snap-type:x mandatory}.pickup-photo :deep(.storage-photo-button){flex:none;scroll-snap-align:start}.pickup-photo :deep(.storage-photos img){width:66px;height:76px;object-fit:contain}.pickup-photo small{display:block;font-size:11px;color:#61777b}.pickup-no-photo{height:76px;border:1px solid #d9e4e6;border-radius:8px;background:#f1f7f6;display:grid;place-items:center;font-size:30px}.pickup-copy{min-width:0}.pickup-copy h3{margin:0 0 3px;font-size:15px;overflow-wrap:anywhere}.pickup-copy>small{font-size:12px;color:#61777b}.pickup-quantity{display:grid;grid-template-columns:36px 48px 36px;gap:3px;justify-content:end;margin-top:6px}.pickup-quantity button,.pickup-quantity input{min-width:0;min-height:36px;height:36px;padding:4px!important;text-align:center;font-size:15px!important;margin:0}.pickup-description{margin:8px 0 0;font-size:12px;line-height:1.55;color:#567277;overflow-wrap:anywhere}.pickup-footer{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap;margin-top:12px;padding-top:12px;border-top:1px solid #d9e4e6}.pickup-footer p{flex-basis:100%;margin:0;font-size:12px}.pickup-footer .primary{min-height:42px}.pickup-remove{margin-top:6px;font-size:12px!important;padding:4px 8px!important}.pickup-photo :deep(.storage-lightbox img){width:auto;height:auto}.pickup-photo :deep(.storage-lightbox-image.is-zoomed img){width:180%;height:auto}
</style>
