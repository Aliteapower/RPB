<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useStaffWallet} from '../wallet/useStaffWallet'
import {cardApi} from './api'
import {useCardText} from './text'
import {money,displayName} from './format'
import type {Purchase} from './types'
const props=defineProps<{store:string;orders:Purchase[];disabled:boolean;can:(p:string)=>boolean}>(),emit=defineEmits<{changed:[];error:[message:string]}>(),{t,locale}=useCardText(),selected=ref(''),busy=ref(false)
const {context}=useStaffWallet();const channels=computed(()=>context.value?.program?.channels??[])
const form=ref({channel:'cash',account:'',terminal:'T1',receipt:'',verified:false,reason:''})
function customerLabel(order:Purchase){return [...new Set([order.customerName?.trim(),order.customerPhone?.trim()].filter(Boolean))].join(' · ')||t('customerUnavailable')}
function toggle(order:Purchase){if(props.disabled||busy.value||(order.status!=='pending'&&order.refundable!==true))return;selected.value=selected.value===order.id?'':order.id}
watch(channels,list=>{if(!list.includes(form.value.channel))form.value.channel=list[0]??''},{immediate:true})
async function act(order:Purchase,refund=false){if(refund&&order.refundable!==true)return;busy.value=true;const store=props.store;try{await cardApi(store).command(`/purchases/${order.id}/${refund?'refund':'confirm'}`,refund?{reason:form.value.reason,receipt:form.value.receipt,verified:form.value.verified}:{...form.value,amountCents:order.amountCents},refund?'purchase.refund':'purchase.confirm');selected.value='';form.value.receipt='';form.value.verified=false;if(store===props.store)emit('changed')}catch(e){if(store===props.store)emit('error',String(e))}finally{busy.value=false}}
async function exception(order:Purchase){busy.value=true;try{await cardApi(props.store).command(`/purchases/${order.id}/exception`,{payment:{...form.value,amountCents:order.amountCents},reason:form.value.reason},'purchase.exception');selected.value='';emit('changed')}catch(e){emit('error',String(e))}finally{busy.value=false}}
</script>
<template>
  <section class="cards-panel cards-purchases">
    <header class="cards-section-heading"><h2>{{t('purchases')}}</h2></header>
    <p class="cards-notice cards-section-note">{{t('policy')}}</p>
    <p v-if="!orders.length" class="cards-empty">{{t('noOrders')}}</p>
    <div v-else class="cards-list">
      <article v-for="o in orders" :key="o.id" class="cards-list-item">
        <div class="cards-item-heading">
          <div class="cards-item-title"><strong>{{displayName(o.snapshot,locale)}}</strong><span class="cards-badge" :data-status="o.status">{{t(o.status)}}</span></div>
          <strong class="cards-amount">{{money(o.amountCents)}}</strong>
          <button v-if="(o.status==='pending'&&can('wallet.cards.sell'))||(['issued','exception'].includes(o.status)&&can('wallet.cards.refund'))" :class="o.status==='pending'?'primary':'cards-danger'" :disabled="disabled||busy||(o.status!=='pending'&&o.refundable!==true)" :title="o.status!=='pending'&&o.refundable!==true?t('refundUnavailable'):undefined" :aria-expanded="selected===o.id" @click="toggle(o)">{{t(o.status==='pending'?'payment':o.refundable===true?'refund':'refundBlocked')}}</button>
        </div>
        <div class="cards-item-meta"><span>{{t('orderNumber')}} <code>{{o.id}}</code></span><span>{{t('customer')}} {{customerLabel(o)}}</span></div>
        <form v-if="selected===o.id&&(o.status==='pending'||o.refundable===true)" class="cards-inline-form" @submit.prevent="act(o,o.status==='issued'||o.status==='exception')">
          <p v-if="o.status!=='pending'" class="cards-notice">{{t('refundHint')}}</p>
          <div class="cards-grid">
            <template v-if="o.status==='pending'">
              <label>{{t('channel')}}<select v-model="form.channel"><option v-for="channel in channels" :key="channel" :value="channel">{{context?.program?.channelLabels?.[channel]||channel.split(':')[0]}}</option></select></label>
              <label>{{t('account')}}<input v-model="form.account"></label><label>{{t('terminal')}}<input v-model="form.terminal" required></label>
            </template>
            <label>{{t('receipt')}}<input v-model="form.receipt" maxlength="100" required></label><label>{{t('reason')}}<input v-model="form.reason" :required="o.status!=='pending'"></label>
          </div>
          <div class="cards-form-footer"><label class="check"><input v-model="form.verified" type="checkbox" required>{{t('verified')}}</label><div class="cards-form-actions"><button class="primary" :disabled="busy||disabled||!form.verified">{{t('confirm')}}</button><button v-if="o.status==='pending'&&can('wallet.cards.refund')" class="cards-danger" type="button" :disabled="busy||disabled||!form.verified||!form.reason||!form.receipt" @click="exception(o)">{{t('recordException')}}</button><button type="button" :disabled="busy" @click="selected=''">{{t('cancel')}}</button></div></div>
          <p v-if="o.status==='pending'&&can('wallet.cards.refund')" class="cards-caption">{{t('exceptionHint')}}</p>
        </form>
      </article>
    </div>
  </section>
</template>
