<script setup lang="ts">
import type {StorageLocation} from '../types'
import {useMerchantStorageText} from './text'
defineProps<{modelValue?:string|null;locations:StorageLocation[];disabled:boolean}>()
const emit=defineEmits<{'update:modelValue':[string]}>(),{t}=useMerchantStorageText()
</script>
<template><label>{{t('storageLocationOptional')}}<select :value="modelValue??''" :disabled="disabled" @change="emit('update:modelValue',($event.target as HTMLSelectElement).value)"><option value="">{{t('noLocation')}}</option><option v-for="place in locations" :key="place.id" :value="place.id">{{place.name}}<template v-if="place.defaultLocation"> · {{t('defaultLocation')}}</template></option></select></label></template>
