<script setup lang="ts">
import {ref,watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ProductBarcodeScanner from '../barcode/ProductBarcodeScanner.vue'
import {normalizeGtin} from '../barcode/gtin'
import {useScanDevice} from './useScanDevice'
import KeyboardScanInput from './KeyboardScanInput.vue'
const props=defineProps<{store?:string;autoStart?:boolean;showManual?:boolean;disabled?:boolean}>()
const emit=defineEmits<{code:[string]}>(),{locale}=useI18n(),{mode,identity}=useScanDevice(()=>props.store),error=ref('')
watch(identity,()=>error.value='')
function found(value:string){if(props.disabled)return;if(!normalizeGtin(value)){error.value=locale.value.startsWith('en')?'Enter a valid product barcode.':'请输入有效的商品条码';return}error.value='';emit('code',value.trim())}
</script>
<template><KeyboardScanInput v-if="mode==='hardware'" :key="identity" :disabled="disabled" :label="locale.startsWith('en')?'Product barcode':'瓶身商品条码'" :error="error" @code="found"/><ProductBarcodeScanner v-else :key="identity" :auto-start="autoStart" :show-manual="showManual" :disabled="disabled" @code="emit('code',$event)"/></template>
