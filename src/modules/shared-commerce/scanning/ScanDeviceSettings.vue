<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useScanDevice} from './useScanDevice'
import type {ScanDevice} from './devicePreference'
const props=defineProps<{store?:string;disabled?:boolean}>()
const {locale}=useI18n(),en=computed(()=>locale.value.startsWith('en'))
const {mode,setMode,available,identity}=useScanDevice(()=>props.store),saved=ref<boolean|null>(null)
watch(identity,()=>saved.value=null)
function change(event:Event){saved.value=setMode((event.target as HTMLSelectElement).value as ScanDevice)}
</script>
<template><section class="scan-device-settings">
  <h3>{{en?'Scanning device':'扫码方式'}}</h3>
  <p>{{en?'Saved in this browser for the current tenant and store. Applies to staff customer codes and product barcodes.':'仅用于本机浏览器、当前租户和门店，顾客码与商品条码共用。'}}</p>
  <label>{{en?'Default device':'默认设备'}}<select :value="mode" :disabled="disabled||!available" @change="change"><option value="camera">{{en?'Camera':'相机扫码'}}</option><option value="hardware">{{en?'Barcode scanner':'扫码枪扫码'}}</option></select></label>
  <p v-if="mode==='hardware'">{{en?'Use keyboard (HID) mode with an Enter suffix. The scanner must support the barcode types and phone screens you use.':'扫码枪需使用键盘（HID）模式，并设置扫码后回车；设备需支持所用一维条码、二维码及手机屏幕识读。'}}</p>
  <p v-if="saved!==null" role="status">{{saved?(en?'Saved on this device.':'已保存到本机。'):(en?'Could not save. Applied for this session only.':'未能保存到本机，仅本次会话生效。')}}</p>
</section></template>
<style scoped>.scan-device-settings label{display:grid;gap:6px}.scan-device-settings select{width:100%;max-width:100%}.scan-device-settings p{overflow-wrap:anywhere}</style>
