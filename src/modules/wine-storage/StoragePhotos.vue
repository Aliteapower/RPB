<script setup lang="ts">
import {IMAGE_SOURCE_LIMIT} from '../../utils/imageUpload'
import {ref,watch,onUnmounted} from 'vue'
import type {StorageApi} from './api'
import {useStorageText} from './text'
const props=defineProps<{api:StorageApi;modelValue:string[];customerId?:string;phase?:string;disabled?:boolean;optional?:boolean}>()
const emit=defineEmits<{'update:modelValue':[string[]];error:[string];busy:[boolean]}>(),{t}=useStorageText(),busy=ref(false)
let generation=0,alive=true
watch(()=>[props.api,props.customerId,props.phase],()=>{generation++;busy.value=false;emit('busy',false)})
onUnmounted(()=>{alive=false;generation++;emit('busy',false)})
async function upload(event:Event){
 const input=event.target as HTMLInputElement,files=Array.from(input.files??[]);if(!files.length||busy.value||props.disabled)return
 const request=++generation,api=props.api,customer=props.customerId,phase=props.phase,original=[...props.modelValue],current=()=>alive&&request===generation
 busy.value=true;emit('busy',true)
 try{
  if(original.length+files.length>10)throw new Error(t('photoLimit'))
  if(files.some(file=>file.size>IMAGE_SOURCE_LIMIT||!['image/jpeg','image/png'].includes(file.type)))throw new Error('请选择有效PNG / JPEG图片 / Choose a valid PNG or JPEG image')
  const added:string[]=[];for(const file of files){if(!current())return;added.push(await api.upload(file,customer,phase))}
  if(current())emit('update:modelValue',[...original,...added])
 }catch(e){if(current())emit('error',String(e))}
 finally{if(current()){busy.value=false;emit('busy',false)}input.value=''}
}

</script>
<template><div class="storage-photo-upload"><label>{{t(optional?'photosOptional':'photos')}} <input type="file" :required="false" accept="image/jpeg,image/png" capture="environment" multiple :disabled="busy||disabled||modelValue.length>=10" @change="upload"></label><small v-if="optional">{{t('optionalPhotoHint')}}</small><small>{{t('photoFormats')}}</small><div class="storage-photos"><div v-for="id in modelValue" :key="id"><a :href="`${api.base}/photos/${id}`" target="_blank" rel="noopener"><img :src="`${api.base}/photos/${id}`" :alt="t('photos')"></a><button type="button" :disabled="busy||disabled" @click="emit('update:modelValue',modelValue.filter(photo=>photo!==id))">{{t('remove')}}</button></div></div></div></template>
