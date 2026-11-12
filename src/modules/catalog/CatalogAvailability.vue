<script setup lang="ts">
import type {Product} from './types'
import {useCatalogText} from './text'
import {displayName} from '../shared-commerce/format'
const props=defineProps<{products:Product[];globalPublish:boolean;canManage:boolean;disabled:boolean}>(),emit=defineEmits<{change:[Product,string]}>(),{t,locale}=useCatalogText()
</script>
<template><section class="cards-panel"><h2>{{t('availabilityManage')}}</h2><p class="cards-muted">{{t('availabilityHint')}}</p><div class="cards-grid"><article v-for="p in products" :key="p.id" class="cards-panel"><h3>{{displayName(p,locale)}}</h3><p>{{p.code}} · {{t(p.status)}}</p><div class="cards-actions"><button v-if="canManage&&p.status!=='archived'" :disabled="disabled" @click="emit('change',p,'listing')">{{t(p.listingEnabled?'storeOff':'storeOn')}}</button><template v-if="globalPublish&&p.status!=='archived'"><button v-for="state in ['active','inactive','archived'].filter(v=>v!==p.status)" :key="state" :disabled="disabled" @click="emit('change',p,state)">{{t(state)}}</button></template></div></article></div></section></template>
