<script setup lang="ts">
import {computed} from 'vue'
import type {CpfProfile,PrDocument} from '../payrollTypes'
import {usePayrollText} from '../payrollText'
const props=defineProps<{cpf:CpfProfile;documents:PrDocument[];confirmed:boolean}>(),emit=defineEmits<{'update:confirmed':[confirmed:boolean]}>(),{r}=usePayrollText()
const selected=computed(()=>props.documents.find(d=>d.id===props.cpf.prDocumentId))
const confirmation=computed({get:()=>props.confirmed,set:(value:boolean)=>{if(value&&selected.value)props.cpf.prDate=selected.value.prEffectiveOn;emit('update:confirmed',value)}})
function sourceChanged(){emit('update:confirmed',false);props.cpf.prDate=selected.value?.prEffectiveOn||null}
</script>
<template><label>{{r('prDateSource')}}<select v-model="cpf.prDocumentId" :aria-label="r('prDateSource')" @change="sourceChanged"><option :value="null">{{r('prManual')}}</option><option v-if="cpf.prDocumentId&&!selected" :value="cpf.prDocumentId" disabled>{{r('prDocumentMissing')}}</option><option v-for="document in documents" :key="document.id" :value="document.id">{{document.typeName}} · {{document.number||document.holderName||document.id}} · {{document.prEffectiveOn}}</option></select></label><p class="wf-hint">{{r('prDocumentHint')}}</p><p v-if="cpf.prDocumentId&&(!selected||cpf.prDate!==selected.prEffectiveOn)" role="alert">{{r('prDocumentReview')}}</p><p v-if="selected" class="wf-hint">{{selected.holderName}} · {{r('prDocumentApproval')}}: {{selected.approvedOn||'—'}} · {{r('prDocumentDate')}}: {{selected.prEffectiveOn}}</p><label>{{r('prDate')}}<input v-model="cpf.prDate" :aria-label="r('prDate')" :readonly="!!cpf.prDocumentId" required type="date"></label><label v-if="selected" class="wf-inline"><input v-model="confirmation" :aria-label="r('prDocumentConfirm')" type="checkbox">{{r('prDocumentConfirm')}}</label></template>
