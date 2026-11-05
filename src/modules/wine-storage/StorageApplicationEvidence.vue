<script setup lang="ts">
import StoragePhotoViewer from './StoragePhotoViewer.vue'
import type {Application} from './types'
import type {StorageApi} from './api'
import {displayName} from '../shared-commerce/format'
import {useStorageText} from './text'
defineProps<{application:Application;api:StorageApi}>()
const {t,locale}=useStorageText()
</script>
<template><details><summary>{{t('original')}}</summary><article v-for="line in application.lines" :key="line.id"><p><strong>{{displayName(line,locale)}}</strong> × {{line.quantity}} · {{t(line.opened?'opened':'whole')}}</p><p v-if="line.note">{{line.note}}</p><StoragePhotoViewer :photos="line.photos" :api="api"/></article></details></template>
