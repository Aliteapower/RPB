<script setup lang="ts">
import {onMounted,ref,watch} from 'vue'
import {usePhasePage} from '../phasePage'
import {reviewBoardText,type Readiness} from '../approvalWorkbench'
import {hours} from '../time'
const props=defineProps<{companyId:string;month:string}>(),{api,home,store,loading,failure,load,english}=usePhasePage(),data=ref<Readiness|null>(null),b=(key:Parameters<typeof reviewBoardText>[0])=>reviewBoardText(key,english())
function refresh(){data.value=null;if(!props.companyId||!/^\d{4}-\d{2}$/.test(props.month))return;void load(()=>api().get<Readiness>(`/approval-readiness?companyId=${encodeURIComponent(props.companyId)}&month=${props.month}`),v=>data.value=v)}
watch(()=>[props.companyId,props.month],refresh);onMounted(refresh)
</script>
<template><section class="wf-section wf-readiness"><header class="wf-toolbar"><h3>{{b('readiness')}}</h3><button :disabled="loading" @click="refresh">{{b('refresh')}}</button></header><p v-if="loading" role="status">{{b('refresh')}}…</p><p v-if="failure" role="alert" class="wf-error">{{failure}}</p><template v-if="data"><p :class="{'wf-issue':!data.ready}">{{data.total?b(data.ready?'ready':'notReady'):b('noData')}} · {{b('current')}} {{data.current}} / {{data.total}} · {{b('pending')}} {{data.pending}}</p><div class="wf-table-wrap"><table><thead><tr><th>{{b('store')}}</th><th>{{b('total')}}</th><th>{{b('current')}}</th><th>{{b('pending')}}</th><th>{{b('work')}} / {{b('paid')}} / {{b('unpaid')}}</th><th></th></tr></thead><tbody><tr v-for="row in data.stores" :key="row.storeId"><td>{{row.storeName}}</td><td>{{row.total}}</td><td>{{row.current}}</td><td>{{row.pending}}</td><td>{{hours(row.workSeconds)}} / {{hours(row.paidLeaveSeconds)}} / {{hours(row.unpaidLeaveSeconds)}}</td><td><RouterLink v-if="home?.headOffice||(home?.employeeManage&&store===row.storeId)" :to="{path:'/stores/'+row.storeId+'/workforce/attendance',query:{month}}">{{b('openReview')}}</RouterLink></td></tr></tbody></table></div></template><p class="wf-hint">{{b('readinessHint')}}</p></section></template>
<style scoped>.wf-readiness{padding:12px;border:1px solid #dbe4ee;border-radius:8px}</style>
