<script setup lang="ts">
import {computed,ref,watch,onUnmounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useStaffWallet} from '../useStaffWallet'
import type {Customer} from '../types'
const props=defineProps<{disabled?:boolean;confirmedId:string|null;hintKey?:string;confirmHintKey?:string}>()
const emit=defineEmits<{confirmed:[id:string];clear:[]}>()
const {t}=useI18n(),workspace=useStaffWallet(),{store,customer,customers,busy,error,search,choose,clear}=workspace
const phone=ref(''),candidate=ref<Customer|null>(null),searched=ref(false),invalid=ref(false)
const digits=computed(()=>phone.value.replace(/[\s()\-]/g,''))
const results=computed(()=>customers.value.filter(c=>c.phone?.replace(/\D/g,'').endsWith(digits.value.replace(/^\+/,''))))
let generation=0
watch([store,phone],()=>{generation++;candidate.value=null;searched.value=false;invalid.value=false;customers.value=[]})
onUnmounted(()=>generation++)
async function lookup(){if(props.disabled||busy.value)return;candidate.value=null;if(!/^\+?\d{8,15}$/.test(digits.value)){invalid.value=true;return}const current=generation;await search(digits.value);if(current===generation)searched.value=true}
async function confirm(){if(props.disabled||busy.value||!candidate.value)return;const current=generation,value=candidate.value;await choose(value,false);if(current===generation&&customer.value?.id===value.id)emit('confirmed',value.id)}
function reset(){if(props.disabled||busy.value)return;generation++;clear();error.value='';phone.value='';candidate.value=null;searched.value=false;emit('clear')}
</script>
<template>
 <section v-if="customer&&customer.id===confirmedId" class="wallet-customer-chip"><div><strong>{{customer.displayName||customer.customerCode}}</strong><small>{{customer.phone}}</small><small>{{t('wallet.marketing.phoneConfirmed')}}</small></div><button :disabled="busy||disabled" @click="reset">{{t('wallet.ui.changeCustomer')}}</button></section>
 <section v-else class="wallet-card"><p>{{t(hintKey||'wallet.marketing.phoneHint')}}</p><form class="wallet-phone-search wallet-search" @submit.prevent="lookup"><input v-model.trim="phone" type="tel" inputmode="tel" autocomplete="off" maxlength="24" :aria-label="t('wallet.marketing.phoneLabel')" :placeholder="t('wallet.marketing.phoneLabel')" :disabled="busy||disabled"><button :disabled="busy||disabled||!phone">{{t('wallet.transactions.search')}}</button></form>
 <p v-if="invalid" class="wallet-error" role="alert">{{t('wallet.marketing.phoneInvalid')}}</p><p v-if="error" class="wallet-error" role="alert">{{error}}</p>
 <div class="wallet-customer-results"><button v-for="c in results" :key="c.id" type="button" :data-phone-candidate="c.id" :aria-pressed="candidate?.id===c.id" :disabled="busy||disabled" @click="candidate=c"><strong>{{c.displayName||c.customerCode}}</strong><small>{{c.phone}}</small></button></div>
 <section v-if="candidate" class="wallet-phone-confirm"><strong>{{candidate.displayName||candidate.customerCode}}</strong><p>{{candidate.phone}}</p><p>{{t(confirmHintKey||'wallet.marketing.phoneConfirmHint')}}</p><button type="button" class="wallet-primary wallet-block" :disabled="busy||disabled" @click="confirm">{{t('wallet.marketing.confirmCustomer')}}</button></section><p v-if="searched&&!busy&&!results.length" class="wallet-empty">{{t('wallet.empty')}}</p>
 </section>
</template>
