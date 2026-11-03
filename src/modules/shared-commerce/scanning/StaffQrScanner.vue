<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useI18n} from 'vue-i18n'
import WalletQrScanner from '../../wallet/components/WalletQrScanner.vue'
import {normalizeWalletQr,type WalletQrPurpose} from '../../wallet/scanning/walletQr'
import {useScanDevice} from './useScanDevice'
import KeyboardScanInput from './KeyboardScanInput.vue'
const props=defineProps<{purpose:WalletQrPurpose;store?:string;autoStart?:boolean;disabled?:boolean;title?:string;privacyMessage?:string;manualLabel?:string;invalidMessage?:string}>()
const emit=defineEmits<{code:[string]}>(),{locale}=useI18n(),{mode,identity}=useScanDevice(()=>props.store),error=ref('')
const scope=computed(()=>`${identity.value}:${props.purpose}`)
watch(scope,()=>error.value='')
function found(value:string){if(props.disabled)return;const code=normalizeWalletQr(value,props.purpose,window.location.origin);if(!code){error.value=props.invalidMessage??(locale.value.startsWith('en')?'Invalid code for this operation.':'码无效或不适用于当前操作。');return}error.value='';emit('code',code)}
</script>
<template><KeyboardScanInput v-if="mode==='hardware'" :key="scope" :disabled="disabled" :label="manualLabel||title" :error="error" @code="found"/><WalletQrScanner v-else :key="scope" :purpose="purpose" :auto-start="autoStart" :disabled="disabled" :title="title" :privacy-message="privacyMessage" :manual-label="manualLabel" :invalid-message="invalidMessage" @code="emit('code',$event)"/></template>
