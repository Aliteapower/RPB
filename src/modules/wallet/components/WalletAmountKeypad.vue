<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {readQuickPayPresetAmounts,saveQuickPayPresetAmounts,parsePresetAmountText} from '../../../utils/paymentPresentBridge'
import {chargePreview} from '../scanCharge'
const props=defineProps<{store:string;disabled?:boolean}>()
const amount=defineModel<string>({required:true}),{t}=useI18n()
const keys=['7','8','9','backspace','4','5','6','clear','1','2','3','00','0','.']
const presets=ref<string[]>([]),editing=ref(false),draft=ref(''),invalid=ref(false)
watch(()=>props.store,store=>{presets.value=readQuickPayPresetAmounts(store).filter(value=>chargePreview(value,0));editing.value=false;invalid.value=false},{immediate:true})
const display=computed(()=>{const [whole,fraction='']=amount.value.split('.');return `${whole||'0'}.${fraction.padEnd(2,'0')}`})
function press(key:string){
 if(props.disabled)return
 if(key==='clear'){amount.value='';return}
 if(key==='backspace'){amount.value=amount.value.slice(0,-1);return}
 if(key==='.'&&amount.value.includes('.'))return
 let next=key==='.'?(amount.value||'0')+'.':(amount.value+key).replace(/^0+(?=\d)/,'')
 const [whole,fraction]=next.split('.');if(fraction!==undefined)next=whole+'.'+fraction.slice(0,2)
 if(/^\d{1,11}(\.\d{0,2})?$/.test(next))amount.value=next
}
function preset(value:string){if(!props.disabled&&chargePreview(value,0))amount.value=value}
function edit(){if(props.disabled)return;draft.value=presets.value.join(', ');invalid.value=false;editing.value=true}
function save(){if(props.disabled)return;const values=parsePresetAmountText(draft.value);if(!values.length||values.some(v=>!chargePreview(v,0))){invalid.value=true;return}presets.value=saveQuickPayPresetAmounts(props.store,values);editing.value=false}
</script>
<template>
 <section class="wallet-keypad" :aria-label="t('wallet.personal.amountKeypad')">
  <span>{{t('wallet.amount')}}</span><output class="wallet-keypad-display" aria-live="polite"><small>S$</small><strong>{{display}}</strong></output>
  <div class="wallet-keypad-grid"><button v-for="key in keys" :key="key" type="button" :data-amount-key="key" :class="{'is-zero':key==='0','is-function':key==='clear'||key==='backspace'}" :aria-label="key==='clear'?t('wallet.personal.clearAmount'):key==='backspace'?t('wallet.personal.backspace'):key" :disabled="disabled" @click="press(key)">{{key==='clear'?'C':key==='backspace'?'⌫':key}}</button></div>
  <section class="wallet-keypad-presets"><header><strong>{{t('wallet.personal.quickAmounts')}}</strong><button type="button" :disabled="disabled" @click="edit">{{t('wallet.marketing.edit')}}</button></header><div class="wallet-keypad-preset-grid"><button v-for="value in presets" :key="value" type="button" :data-amount-preset="value" :disabled="disabled" @click="preset(value)">{{value}}</button></div>
   <div v-if="editing" class="wallet-form"><label>{{t('wallet.personal.presetHint')}}<textarea v-model="draft" :disabled="disabled" maxlength="500" /></label><p v-if="invalid" class="wallet-error" role="alert">{{t('wallet.personal.presetInvalid')}}</p><div><button type="button" :disabled="disabled" @click="save">{{t('wallet.save')}}</button><button type="button" :disabled="disabled" @click="editing=false">{{t('wallet.cancel')}}</button></div></div>
  </section>
 </section>
</template>
<style scoped>
.wallet-keypad{display:grid;gap:12px;min-width:0}.wallet-keypad-display{display:flex;align-items:baseline;justify-content:space-between;gap:8px;padding:18px 16px;background:#f6fafa;border:1px solid #ced6de;border-radius:14px;font-variant-numeric:tabular-nums}.wallet-keypad-display strong{font-size:clamp(24px,6vw,48px);line-height:1.2;overflow-wrap:anywhere}.wallet-keypad-display small{font-size:16px;color:#64748b}.wallet-keypad-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}.wallet-keypad-grid button,.wallet-keypad-preset-grid button{min-height:56px;padding:12px 4px;font-size:24px;font-weight:700;border-radius:12px;touch-action:manipulation}.wallet-keypad-grid .is-function{background:#f1f5f9}.wallet-keypad-grid .is-zero{grid-column:span 2}.wallet-keypad-presets{border-top:1px solid #e2e8f0;padding-top:12px;display:grid;gap:10px}.wallet-keypad-presets header{display:flex;align-items:center;justify-content:space-between}.wallet-keypad-preset-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
</style>
