<script setup lang="ts">
import {ref,computed,watch} from 'vue'
import type {Product,Category} from './types'
import {useCatalogText} from './text'
import {displayName,money} from '../shared-commerce/format'
const props=defineProps<{products:Product[];categories:Category[];canEdit:boolean;disabled:boolean}>(),emit=defineEmits<{edit:[Product]}>(),{t,locale}=useCatalogText(),search=ref(''),category=ref(''),page=ref(0)
const matches=computed(()=>props.products.filter(p=>(!category.value||p.categoryId===category.value)&&[p.code,p.nameZh,p.nameEn].some(v=>v.toLowerCase().includes(search.value.trim().toLowerCase())))),items=computed(()=>matches.value.slice(page.value*12,page.value*12+12))
watch([search,category,()=>props.products],()=>page.value=0)
</script>
<template><section :aria-label="t('productList')"><div class="cards-grid"><label>{{t('search')}}<input v-model="search" maxlength="120"></label><label>{{t('category')}}<select v-model="category"><option value="">{{t('allCategories')}}</option><option v-for="c in categories" :key="c.id" :value="c.id">{{displayName(c,locale)}}</option></select></label></div><p v-if="!items.length">{{t('empty')}}</p><div class="cards-grid"><article v-for="p in items" :key="p.id" class="cards-panel"><h3>{{displayName(p,locale)}}</h3><small>{{p.code}} · {{t(p.status)}}</small><p>{{p.priceCents===null?t('unpriced'):money(p.priceCents)}} / {{p.unit}}</p><p>{{p.channels.map(c=>t(c)).join(' · ')}}</p><button v-if="canEdit&&p.status!=='archived'" type="button" :disabled="disabled" @click="emit('edit',p)">{{t('edit')}}</button></article></div><div v-if="matches.length>12" class="cards-actions"><button type="button" :disabled="!page" @click="page--">←</button><span>{{page+1}} / {{Math.ceil(matches.length/12)}}</span><button type="button" :disabled="(page+1)*12>=matches.length" @click="page++">→</button></div></section></template>
