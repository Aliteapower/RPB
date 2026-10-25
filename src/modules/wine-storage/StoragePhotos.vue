<script setup lang="ts">
import {ref} from 'vue'
import type {StorageApi} from './api'
import {useStorageText} from './text'
const props=defineProps<{api:StorageApi;modelValue:string[];customerId?:string;phase?:string;disabled?:boolean}>()
const emit=defineEmits<{'update:modelValue':[string[]];error:[string];busy:[boolean]}>(),{t}=useStorageText(),busy=ref(false)
async function upload(event:Event){const input=event.target as HTMLInputElement;const files=Array.from(input.files??[]);if(!files.length)return;busy.value=true;emit('busy',true);try{if(props.modelValue.length+files.length>10)throw new Error(t('photoLimit'));if(files.some(file=>file.size>5*1024*1024||!['image/jpeg','image/png'].includes(file.type)))throw new Error('PNG / JPEG ≤ 5 MiB');const added:string[]=[];for(const file of files)added.push(await props.api.upload(file,props.customerId,props.phase));emit('update:modelValue',[...props.modelValue,...added])}catch(e){emit('error',String(e))}finally{busy.value=false;emit('busy',false);input.value=''}}
</script>
<template><div><label>{{t('photos')}} <input type="file" accept="image/jpeg,image/png" capture="environment" multiple :disabled="busy||disabled||modelValue.length>=10" @change="upload"></label><small>PNG / JPEG · ≤ 5 MiB</small><div class="storage-photos"><div v-for="id in modelValue" :key="id"><a :href="`${api.base}/photos/${id}`" target="_blank" rel="noopener"><img :src="`${api.base}/photos/${id}`" :alt="t('photos')"></a><button type="button" :disabled="busy||disabled" @click="emit('update:modelValue',modelValue.filter(photo=>photo!==id))">{{t('remove')}}</button></div></div></div></template>
