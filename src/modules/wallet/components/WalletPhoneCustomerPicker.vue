<script setup lang="ts">
import {computed,nextTick,ref,shallowRef,watch,onUnmounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import {useStaffWallet} from '../useStaffWallet'
import type {Customer} from '../types'
import WalletCustomerManagementEntry from './WalletCustomerManagementEntry.vue'
const props=defineProps<{disabled?:boolean;confirmedId:string|null;hintKey?:string;confirmHintKey?:string}>()
const emit=defineEmits<{confirmed:[id:string];clear:[]}>()
const {t}=useI18n(),route=useRoute(),workspace=useStaffWallet(),{store,customer,customers,busy,error,search,choose,clear}=workspace
const phone=ref(''),searched=ref(false),invalid=ref(false),input=shallowRef<HTMLInputElement|null>(null)
const digits=computed(()=>phone.value.replace(/[\s()\-]/g,''))
const results=computed(()=>customers.value.filter(c=>c.phone?.replace(/\D/g,'').endsWith(digits.value.replace(/^\+/,''))))
let generation=0
watch(phone,()=>{generation++;searched.value=false;invalid.value=false;workspace.cancelSearch()},{flush:'sync'})
watch(store,()=>{generation++;phone.value='';searched.value=false;invalid.value=false;customers.value=[]},{flush:'sync'})
onUnmounted(()=>{generation++;workspace.cancelSearch()})
async function lookup(){if(props.disabled||busy.value)return;if(!/^\+?\d{8,15}$/.test(digits.value)){invalid.value=true;return}const current=generation,rows=await search(digits.value);if(current!==generation||!rows)return;searched.value=true;if(results.value.length===1)await select(results.value[0])}
async function select(value:Customer){if(props.disabled||busy.value)return;const current=generation;await choose(value,false,false);if(current===generation&&customer.value?.id===value.id)emit('confirmed',value.id)}
function reset(clearPhone=false){if(props.disabled||busy.value)return;generation++;clear();error.value='';if(clearPhone)phone.value='';searched.value=false;emit('clear');if(!clearPhone)void nextTick(()=>{input.value?.focus?.();input.value?.select?.()})}
</script>
<template>
 <section v-if="customer&&customer.id===confirmedId" class="wallet-customer-chip"><div><strong>{{customer.displayName||customer.customerCode}}</strong><small>{{customer.phone}}</small><small>{{t('wallet.marketing.phoneConfirmed')}}</small></div><button :disabled="busy||disabled" @click="reset()">{{t('wallet.ui.changeCustomer')}}</button><button :disabled="busy||disabled" @click="reset(true)">{{t('wallet.ui.clearCustomer')}}</button><button v-if="route.query.kind==='money'&&!workspace.account.value&&workspace.context.value?.transactionsEnabled&&workspace.can('wallet.topup.create')" :disabled="busy||disabled" @click="choose(customer,true)">{{t('wallet.ui.openWallet')}}</button></section>
 <section v-else class="wallet-card"><p>{{t(hintKey||'wallet.marketing.phoneHint')}}</p><form class="wallet-phone-search wallet-search" @submit.prevent="lookup"><input ref="input" v-model.trim="phone" type="tel" inputmode="tel" autocomplete="off" maxlength="24" :aria-label="t('wallet.marketing.phoneLabel')" :placeholder="t('wallet.marketing.phoneLabel')" :disabled="busy||disabled"><button :disabled="busy||disabled||!phone">{{t('wallet.transactions.search')}}</button></form>
 <p v-if="invalid" class="wallet-error" role="alert">{{t('wallet.marketing.phoneInvalid')}}</p><p v-if="error" class="wallet-error" role="alert">{{error}}</p>
 <div class="wallet-customer-results"><button v-for="c in results" :key="c.id" type="button" :data-phone-candidate="c.id" :disabled="busy||disabled" @click="select(c)"><strong>{{c.displayName||c.customerCode}}</strong><small>{{c.phone}}</small></button></div>
 <template v-if="searched&&!busy&&!error&&!results.length"><p class="wallet-empty">{{t('wallet.empty')}}</p><WalletCustomerManagementEntry source="wallet-recharge" :keyword="phone" create :disabled="disabled" /></template>
 </section>
</template>
