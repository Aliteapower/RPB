<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
const props=withDefaults(defineProps<{modelValue:File|null;disabled?:boolean;captureLabel:string;albumLabel:string;emptyLabel:string;removeLabel?:string;invalidLabel:string;maxBytes?:number;accept?:string}>(),{maxBytes:80*1024*1024,accept:'image/png,image/jpeg,image/webp'})
const emit=defineEmits<{'update:modelValue':[file:File|null]}>()
const camera=ref<HTMLInputElement|null>(null),album=ref<HTMLInputElement|null>(null),preview=ref(''),error=ref('')
function release(){if(preview.value)URL.revokeObjectURL(preview.value);preview.value=''}
watch(()=>props.modelValue,file=>{release();if(file)preview.value=URL.createObjectURL(file)},{immediate:true})
onBeforeUnmount(release)
function select(event:Event){const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file)return;error.value='';if(!props.accept.split(',').includes(file.type)||file.size>props.maxBytes){error.value=props.invalidLabel;return}emit('update:modelValue',file)}
</script>
<template><div class="payment-proof-upload"><div class="upload-actions"><button type="button" class="secondary-button" :disabled="disabled" @click="camera?.click()">{{captureLabel}}</button><button type="button" class="secondary-button" :disabled="disabled" @click="album?.click()">{{albumLabel}}</button><button v-if="modelValue&&removeLabel" type="button" :disabled="disabled" @click="error='';emit('update:modelValue',null)">{{removeLabel}}</button></div><input ref="camera" class="proof-file-input" type="file" :accept="accept" capture="environment" :disabled="disabled" @change="select"><input ref="album" class="proof-file-input" type="file" :accept="accept" :disabled="disabled" @change="select"><p class="selected-file">{{modelValue?.name??emptyLabel}}</p><p v-if="error" role="alert">{{error}}</p><img v-if="preview" class="preview-image" :src="preview" alt=""></div></template>
<style scoped>.upload-actions{display:flex;gap:10px;flex-wrap:wrap}.proof-file-input{display:none}.preview-image{display:block;max-width:100%;max-height:320px;object-fit:contain;margin:12px auto}.selected-file{overflow-wrap:anywhere;font-size:13px;color:#697586}</style>
