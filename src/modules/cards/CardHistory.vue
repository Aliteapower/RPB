<script setup lang="ts">
import {ref} from 'vue'
import {cardApi} from './api'
import {useCardText} from './text'
import type {CardEntry,Holding} from './types'
const props=defineProps<{store:string;entries:CardEntry[];holdings:Holding[];customer?:boolean;canReverse?:boolean;disabled?:boolean}>(),emit=defineEmits<{changed:[];error:[message:string]}>(),{t}=useCardText(),selected=ref(''),reason=ref(''),busy=ref(false)
function customerLabel(entry:CardEntry){return [...new Set([entry.customerName?.trim(),entry.customerPhone?.trim()].filter(Boolean))].join(' · ')||t('customerUnavailable')}
async function reverse(e:CardEntry){busy.value=true;try{await cardApi(props.store).command(`/entries/${e.id}/${e.kind==='stamp'?'reverse-stamp':'reverse'}`,{reason:reason.value},e.kind==='stamp'?'stamp.reverse':'redeem.reverse');selected.value='';reason.value='';emit('changed')}catch(error){emit('error',String(error))}finally{busy.value=false}}
</script>
<template>
  <section v-if="customer" class="cards-panel cards-customer-history">
    <h2>{{t('usageRecords')}}</h2><p class="cards-customer-policy">{{t('historyHint')}}</p><p v-if="!entries.length" class="cards-customer-empty">{{t('noRecords')}}</p>
    <article v-for="e in entries" :key="e.id" class="cards-customer-list-item">
      <div class="cards-customer-card-heading"><strong>{{t(e.kind==='stamp'?'stampAction':e.kind)}}</strong><span class="cards-customer-record-quantity">× {{e.quantity}}</span></div>
      <small class="cards-customer-record-time">{{e.businessDay}}<template v-if="e.kind!=='daily_expire'"> · {{new Date(e.createdAt).toLocaleTimeString()}}</template></small>
      <dl class="cards-customer-record-values"><div><dt>{{t('totalUses')}}</dt><dd>{{e.beforeTotal??'—'}} → {{e.afterTotal??'—'}}</dd></div><div><dt>{{t('dailyAllowance')}}</dt><dd>{{e.beforeDaily}} → {{e.afterDaily}}</dd></div></dl>
      <p v-if="e.kind==='daily_expire'" class="cards-customer-record-note">{{t('dailyExpireHint')}}</p><p v-if="e.source&&e.kind!=='daily_expire'" class="cards-customer-record-note">{{t('recordSource')}} {{e.source}}</p><p v-if="e.originalId" class="cards-customer-record-note">{{t('linked')}} {{e.originalId}}</p><p v-if="e.reason&&e.kind!=='daily_expire'" class="cards-customer-record-note">{{t('reason')}}：{{e.reason}}</p>
    </article>
  </section>
  <section v-else class="cards-panel cards-history">
    <header class="cards-section-heading"><h2>{{t('records')}}</h2></header>
    <p class="cards-caption">{{t('historyHint')}}</p>
    <p v-if="!entries.length" class="cards-empty">{{t('noRecords')}}</p>
    <div v-else class="cards-list">
      <article v-for="e in entries" :key="e.id" class="cards-list-item">
        <div class="cards-item-heading"><div class="cards-item-title"><strong>{{t(e.kind==='stamp'?'stampAction':e.kind)}}</strong><span class="cards-quantity">× {{e.quantity}}</span></div><small class="cards-item-time">{{e.businessDay}}<template v-if="e.kind!=='daily_expire'"> · {{new Date(e.createdAt).toLocaleTimeString()}}</template></small><button v-if="canReverse&&['redeem','stamp'].includes(e.kind)&&!entries.some(x=>x.originalId===e.id)" class="cards-danger" :disabled="busy||disabled" :aria-expanded="selected===e.id" @click="selected=e.id">{{t('reverse')}}</button></div>
        <dl class="cards-entry-values"><div><dt>{{t('totalUses')}}</dt><dd>{{e.beforeTotal??'—'}} <span>→</span> {{e.afterTotal??'—'}}</dd></div><div><dt>{{t('dailyAllowance')}}</dt><dd>{{e.beforeDaily}} <span>→</span> {{e.afterDaily}}</dd></div></dl>
        <div class="cards-item-meta"><span>{{t('customer')}} {{customerLabel(e)}}</span><span v-if="e.source">{{t('recordSource')}} {{e.source}}</span><span v-if="e.originalId">{{t('linked')}} <code>{{e.originalId}}</code></span></div>
        <p v-if="e.reason" class="cards-record-reason">{{t('reason')}}：{{e.reason}}</p>
        <form v-if="selected===e.id" class="cards-inline-form cards-reverse-form" @submit.prevent="reverse(e)"><label>{{t('reason')}}<input v-model="reason" required></label><div class="cards-form-actions"><button class="cards-danger" :disabled="busy||disabled||!reason">{{t('confirm')}}</button><button type="button" :disabled="busy" @click="selected='';reason=''">{{t('cancel')}}</button></div></form>
      </article>
    </div>
  </section>
</template>
