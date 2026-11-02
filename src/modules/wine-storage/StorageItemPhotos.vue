<script setup lang="ts">
import {computed} from 'vue'
import type {Item,Event} from './types'
import type {StorageApi} from './api'
import {itemPhotos} from './itemPhotos'
import StoragePhotoViewer from './StoragePhotoViewer.vue'
const props=defineProps<{item:Item;api:StorageApi;events?:Event[]}>()
const photos=computed(()=>itemPhotos(props.item,props.events))
</script>
<template><StoragePhotoViewer v-if="photos.length" :key="item.id" :photos="photos" :api="api"/></template>
