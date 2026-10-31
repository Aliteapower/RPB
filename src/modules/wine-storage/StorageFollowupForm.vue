<script setup lang="ts">
import {ref} from 'vue'
import type {StorageApi} from './api'
import type {Item} from './types'
import {useStorageText} from './text'
import StoragePhotos from './StoragePhotos.vue'
const props=defineProps<{api:StorageApi;item:Item;disabled:boolean}>(),emit=defineEmits<{saved:[];error:[unknown]}>(),{t}=useStorageText()
const photos=ref<string[]>([]),note=ref(''),uploading=ref(false),saving=ref(false)
async function save(){if(props.disabled||uploading.value||saving.value||!photos.value.length)return;saving.value=true;try{await props.api.command(`/items/${props.item.id}/photos`,{photos:photos.value,note:note.value});photos.value=[];note.value='';emit('saved')}catch(e){emit('error',e)}finally{saving.value=false}}
</script>
<template><details><summary>{{t('followup')}}</summary><form @submit.prevent="save"><fieldset :disabled="disabled||saving"><StoragePhotos v-model="photos" :api="api" :customer-id="item.customerId" phase="followup" :disabled="disabled||saving" @busy="uploading=$event" @error="emit('error',$event)"/><label>{{t('note')}}<textarea v-model="note" maxlength="2000"/></label><button :disabled="disabled||saving||uploading||!photos.length">{{t('followup')}}</button></fieldset></form></details></template>
