<script setup lang="ts">
import {ref,watch} from 'vue'
import ProductBarcodeScanner from '../../shared-commerce/barcode/ProductBarcodeScanner.vue'
import {useCatalogText} from '../text'
const props=defineProps<{modelValue:string[];disabled:boolean;readOnly:boolean}>(),emit=defineEmits<{'update:modelValue':[string[]]}>(),{t}=useCatalogText(),scanIndex=ref<number|null>(null)
function update(index:number,value:string){const next=[...props.modelValue];next[index]=value;emit('update:modelValue',next)}
function scanNew(){const index=props.modelValue.length;emit('update:modelValue',[...props.modelValue,'']);scanIndex.value=index}
function remove(index:number){scanIndex.value=null;emit('update:modelValue',props.modelValue.filter((_,i)=>i!==index))}
watch(()=>props.disabled,v=>{if(v)scanIndex.value=null})
</script>
<template><section class="catalog-barcode-fields" :aria-label="t('barcodes')"><h3>{{t('barcodes')}}</h3><p class="cards-muted">{{t(readOnly?'onlyAdmin':'barcodeHint')}}</p><div v-for="(value,index) in modelValue" :key="index" class="barcode-field-row"><label>{{t('barcodes')}} {{index+1}}<input :value="value" inputmode="numeric" maxlength="14" :disabled="disabled||readOnly" @input="update(index,($event.target as HTMLInputElement).value)"></label><div v-if="!readOnly" class="cards-actions"><button type="button" :disabled="disabled" @click="scanIndex=index">{{t('scanInto')}}</button><button type="button" :disabled="disabled" @click="remove(index)">{{t('remove')}}</button></div></div><button v-if="!readOnly" type="button" :disabled="disabled||modelValue.length>=50" @click="emit('update:modelValue',[...modelValue,''])">＋ {{t('addBarcode')}}</button><button v-if="!readOnly" type="button" :disabled="disabled||modelValue.length>=50" @click="scanNew">{{t('scanAddBarcode')}}</button><ProductBarcodeScanner v-if="scanIndex!==null" :key="scanIndex" :auto-start="true" :show-manual="false" :disabled="disabled" @code="update(scanIndex!,$event);scanIndex=null"/></section></template>
<style scoped>.catalog-barcode-fields{border-top:1px solid #d8e5e8;margin:20px 0;padding-top:16px}.barcode-field-row{padding:10px 0;border-bottom:1px solid #edf2f3}.barcode-field-row input{font-variant-numeric:tabular-nums}</style>
