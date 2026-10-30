<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {DocumentRecognition} from '../documentTypes'
import {useDocumentText} from '../documentText'
const props=defineProps<{suggestion:DocumentRecognition;disabled:boolean}>()
const emit=defineEmits<{apply:[values:Partial<Record<'holderName'|'number'|'approvedOn'|'expiresOn'|'prEffectiveOn',string>>];dismiss:[]}>()
const {d}=useDocumentText(),selected=ref<Record<string,boolean>>({})
const corrected=ref<Record<string,string>>({})
watch(()=>props.suggestion,value=>{corrected.value={holderName:value.name||'',number:value.number||'',approvedOn:value.approvedOn||'',expiresOn:value.expiresOn||'',prEffectiveOn:value.prDate||''};selected.value={}},{immediate:true})
const fields=computed(()=>[
 {key:'holderName' as const,label:d('holderName'),value:props.suggestion.name},
 {key:'number' as const,label:d('number'),value:props.suggestion.number},
 {key:'approvedOn' as const,label:d('approvedOn'),value:props.suggestion.approvedOn},
 {key:'expiresOn' as const,label:d('expiry'),value:props.suggestion.expiresOn},
 {key:'prEffectiveOn' as const,label:d('prEffectiveOn'),value:props.suggestion.prDate}
].filter(field=>field.value))
function warningText(warning:string){if(warning==='PDF_FIRST_TWO_PAGES_ONLY')return d('pdfFirstPages');if(/AMBIGUOUS|CONFLICT|多个|冲突/.test(warning))return d('recognitionAmbiguous');if(/MISSING|未识别/.test(warning))return d('recognitionMissing');return d('recognitionWarning')}
function apply(){emit('apply',Object.fromEntries(fields.value.filter(field=>selected.value[field.key]).map(field=>[field.key,corrected.value[field.key]])))}
</script>
<template><aside class="wf-panel" aria-live="polite"><h3>{{d('recognitionTitle')}}</h3><p class="wf-hint">{{d('recognitionHint')}}</p><p class="wf-hint">{{d('prHint')}}</p><p v-for="(warning,index) in suggestion.warnings" :key="index" class="wf-hint">{{warningText(warning)}}</p><fieldset :disabled="disabled"><div v-for="field in fields" :key="field.key" class="wf-recognition-field"><input v-model="selected[field.key]" type="checkbox" :aria-label="field.label"><span>{{field.label}}</span><input class="wf-recognition-value" v-model="corrected[field.key]" :aria-label="d('correctRecognition')+' · '+field.label" :type="['approvedOn','expiresOn','prEffectiveOn'].includes(field.key)?'date':'text'" :maxlength="100"></div></fieldset><p v-if="!fields.length">{{d('recognitionEmpty')}}</p><div class="wf-form-actions"><button type="button" :disabled="disabled" @click="emit('dismiss')">{{d('dismissRecognition')}}</button><button v-if="fields.length" type="button" :disabled="disabled||!Object.values(selected).some(Boolean)" @click="apply">{{d('applyRecognition')}}</button></div></aside></template>

<style scoped>.wf-recognition-field{display:grid;grid-template-columns:18px minmax(0,1fr);align-items:center;gap:5px 8px;margin:12px 0}.wf-recognition-field input[type=checkbox]{width:16px;height:16px;min-height:16px;margin:0;padding:0;justify-self:start}.wf-recognition-value{grid-column:2;min-width:0;width:100%}</style>
