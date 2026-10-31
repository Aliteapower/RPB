<script setup lang="ts">
import type {Application} from './types'
import {displayName} from '../shared-commerce/format'
import {useStorageText} from './text'
defineProps<{application:Application}>()
const {t,locale}=useStorageText()
</script>
<template><section v-if="application.confirmation" class="storage-receipt-summary"><h3>{{t('receiptResult')}}</h3><p><span>{{t('originalQuantity')}} {{application.lines.reduce((n,l)=>n+l.quantity,0)}}</span> · <strong>{{t('actualQuantity')}} {{application.confirmation.lines.reduce((n,l)=>n+l.quantity,0)}}</strong></p><p v-for="(line,index) in application.confirmation.lines" :key="index"><strong>{{displayName(line,locale)||displayName(application.lines.find(original=>original.id===line.lineId)??{nameZh:'',nameEn:''},locale)}}</strong> × {{line.quantity}} · {{t(line.opened?'opened':'whole')}}</p></section></template>
