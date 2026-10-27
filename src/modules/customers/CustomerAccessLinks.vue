<script setup lang="ts">
import {computed} from 'vue'
import {useAuthSessionStore} from '../../stores/authSession'
import {customerAccessTarget,type CustomerAccessSource} from './accessNavigation'
import {useCustomerAccessText} from './accessText'
const props=withDefaults(defineProps<{store:string;source?:CustomerAccessSource;disabled?:boolean;compact?:boolean}>(),{source:'staff',disabled:false})
const auth=useAuthSessionStore(),{t}=useCustomerAccessText()
const allowed=computed(()=>auth.isTenantAdmin&&auth.user?.storeIds.includes(props.store)===true)
</script>
<template><nav v-if="allowed&&!disabled" class="customer-access-links" :class="{compact}" :aria-label="t('title')"><template v-for="kind in ['activation','email'] as const" :key="kind"><slot :to="customerAccessTarget(store,source,kind)" :label="t(kind)"><RouterLink :to="customerAccessTarget(store,source,kind)"><strong>{{t(kind)}}</strong><small v-if="!compact">{{t(kind+'Hint')}}</small></RouterLink></slot></template></nav></template>
<style scoped>.customer-access-links{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,180px),1fr));gap:10px;margin:12px 0;min-width:0;grid-column:1 / -1}.customer-access-links a{display:grid;gap:5px;padding:12px;border:1px solid #d9e4e6;border-radius:10px;background:#fff;color:#155f52;text-decoration:none;overflow-wrap:anywhere;min-width:0}.customer-access-links strong{font-size:14px}.customer-access-links small{font-size:12px;line-height:1.5;color:#567277}.customer-access-links a:focus-visible{outline:3px solid #82b8ac;outline-offset:2px}.customer-access-links.compact{display:contents}</style>
