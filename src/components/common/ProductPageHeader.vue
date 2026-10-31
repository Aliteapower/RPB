<script setup lang="ts">
import type {RouteLocationRaw} from 'vue-router'
import FrontendLocaleSwitcher from './FrontendLocaleSwitcher.vue'
const props=defineProps<{title:string;caption?:string;logo?:string;backTo?:RouteLocationRaw;backLabel?:string;refreshLabel?:string;refreshDisabled?:boolean}>()
const emit=defineEmits<{refresh:[]}>()
function refresh(){if(!props.refreshDisabled)emit('refresh')}
</script>
<template>
 <header class="product-page-header">
  <div class="product-page-header-brand">
   <RouterLink v-if="backTo" class="product-page-header-back" :to="backTo" :aria-label="backLabel" :title="backLabel"><img v-if="logo" :src="logo" alt=""><svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m14 5-7 7 7 7"/></svg></RouterLink>
   <div class="product-page-header-copy"><h1>{{title}}</h1><small v-if="caption" :title="caption">{{caption}}</small></div>
  </div>
  <div class="product-page-header-navigation"><slot name="navigation"/></div>
  <div class="product-page-header-actions"><slot name="actions"/><button v-if="refreshLabel" type="button" class="product-page-header-refresh" :aria-label="refreshLabel" :title="refreshLabel" :disabled="refreshDisabled" @click="refresh"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M20 7v5h-5M20 12a8 8 0 1 0-2 5M20 12l-4-4"/></svg></button><FrontendLocaleSwitcher/></div>
 </header>
</template>
<style scoped>
.product-page-header{display:flex;align-items:center;gap:16px;margin:0 0 16px;padding:0 0 12px;border-bottom:1px solid var(--page-header-line,var(--wallet-line,#e1e7ef));color:var(--page-header-ink,var(--wallet-ink,#16243b));font-size:13px;line-height:1.5}
.product-page-header-brand{display:flex;align-items:center;gap:10px;flex:1;min-width:0}
.product-page-header .product-page-header-copy{min-width:0;overflow-wrap:anywhere}
.product-page-header .product-page-header-copy h1{font-size:19px;line-height:1.4;font-weight:650;letter-spacing:0;margin:0 0 2px}
.product-page-header .product-page-header-copy small{display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden;margin:0;font-size:12px;line-height:1.55;color:var(--page-header-muted,var(--wallet-muted,#758197))}
.product-page-header-navigation,.product-page-header-actions{display:flex;align-items:center;gap:8px;flex:none;min-width:0}
.product-page-header-navigation:empty{display:none}
.product-page-header-navigation :deep(a){white-space:nowrap}
.product-page-header :deep(button),.product-page-header :deep(select){font:inherit;min-height:36px;font-size:12px;line-height:1.5;border-radius:9px}
.product-page-header .product-page-header-back,.product-page-header .product-page-header-refresh{display:flex;align-items:center;justify-content:center;flex:none;width:36px;height:36px;min-height:36px;padding:0;border:0;border-radius:50%;background:#fff;color:inherit;text-decoration:none;cursor:pointer}
.product-page-header-back img{width:36px;height:36px;object-fit:contain;border-radius:10px}
.product-page-header-refresh:disabled{opacity:.45;cursor:not-allowed}
.product-page-header :deep(.frontend-locale-switcher){position:static;z-index:auto;box-shadow:none;backdrop-filter:none;background:#fff;border:1px solid #dfe6f0;padding:2px;display:inline-flex;gap:0;flex:none}
.product-page-header :deep(.frontend-locale-switcher__button){height:auto;min-height:26px;min-width:0;padding:3px 8px;border:0;font-size:11px;line-height:1.5}
.product-page-header :deep(.frontend-locale-switcher__button--active){background:#edf5ff;color:var(--page-header-accent,var(--wallet-blue,#1677ff))}
.product-page-header :is(button,a,select):focus-visible{outline:3px solid #82b5ff;outline-offset:3px}
@media(max-width:600px){
 .product-page-header{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;padding-bottom:10px;margin-bottom:12px}
 .product-page-header-navigation{gap:6px;font-size:11px}
 .product-page-header-navigation :deep(select){max-width:100px}
 .product-page-header-actions{grid-column:1/-1;justify-content:flex-end;gap:6px;flex-wrap:wrap}
}
@media(max-width:360px){.product-page-header-brand{gap:8px}.product-page-header .product-page-header-copy h1{font-size:17px}.product-page-header-navigation :deep(select){max-width:88px}}
</style>
<style>
#app:has(.product-page-header)>.frontend-locale-switcher{display:none}
</style>
