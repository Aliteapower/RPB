<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useRoute} from 'vue-router'
import {usePhasePage} from '../phasePage'
import {usePhaseThreeText} from '../phaseThreeText'
import {dateRangeValid,monthEnd} from '../timekeepingUi'
import {addDays,storeToday,localInput,hours} from '../time'
import {groupSeconds,type LeaveGroup,type LeaveGroupPage,type LeaveGroupDecision} from '../leaveGroups'
import {WorkforceApiError} from '../api'
import type {LeaveDecision} from '../phaseThreeTypes'
import PhaseStatus from '../components/PhaseStatus.vue'
import LeaveGroupPanel from '../components/LeaveGroupPanel.vue'
const route=useRoute(),{api,home,t,p,base,loading,busy,failure,notice,load,write}=usePhasePage(),{q}=usePhaseThreeText(),from=ref(typeof route.query.from==='string'?route.query.from:storeToday().slice(0,8)+'01'),to=ref(typeof route.query.to==='string'?route.query.to:monthEnd(from.value)),items=ref<LeaveGroup[]>([]),zone=ref('Asia/Singapore'),expanded=ref(typeof route.query.groupId==='string'?route.query.groupId:'')
function stamp(v:string){return localInput(v,zone.value).replace('T',' ')}
function summary(group:LeaveGroup){const counts=new Map<string,number>();for(const l of group.items)counts.set(l.status,(counts.get(l.status)||0)+1);return [...counts].map(([status,count])=>q(status as LeaveGroup['items'][number]['status'])+' '+count).join(' · ')}
function refresh(){if(frozenGroup.value)return;if(!dateRangeValid(from.value,to.value)){failure.value=p('invalid');return}void load(()=>api().get<LeaveGroupPage>(`/leave-groups?from=${from.value}&to=${to.value}`),v=>{items.value=v.items;zone.value=v.timezone})}
const frozenGroup=ref<{id:string;command:LeaveGroupDecision}|null>(null)
function decideAll(id:string,c:LeaveGroupDecision){
 if(busy.value)return
 if(!frozenGroup.value)frozenGroup.value={id,command:structuredClone(c)}
 const request=frozenGroup.value
 void write(async()=>{try{return await api().put('/leave-groups/'+request.id+'/decision',request.command)}catch(e){if(e instanceof WorkforceApiError&&e.status<500)frozenGroup.value=null;throw e}},()=>{frozenGroup.value=null;refresh()})
}
function decideSingle(c:{id:string;expectedVersion:number;status:LeaveDecision;note:string}){if(frozenGroup.value)return;const {id,...body}=c;void write(()=>api().put('/leaves/'+id+'/decision',body),refresh)}
onMounted(refresh)
</script>
<template><section class="wf-panel"><header class="wf-toolbar"><h2>{{q(home?.employeeManage?'leaves':'myLeaves')}}</h2><RouterLink v-if="home?.myself" class="wf-button primary" :to="base+'/leaves/new'">{{q('newLeave')}}</RouterLink></header><form class="wf-filters" @submit.prevent="refresh"><label>{{p('from')}}<input v-model="from" required type="date" :disabled="busy||!!frozenGroup"></label><label>{{p('to')}}<input v-model="to" required type="date" :min="from" :max="addDays(from,30)" :disabled="busy||!!frozenGroup"></label><button :disabled="loading||busy||!!frozenGroup">{{p('filter')}}</button></form><PhaseStatus :failure="failure" :notice="notice" :loading="loading"/><template v-if="!loading"><article v-for="group in items" :key="group.id" class="wf-leave-group"><button class="wf-leave-group-heading" :aria-expanded="expanded===group.id" :disabled="busy||!!frozenGroup" @click="expanded=expanded===group.id?'':group.id"><span><strong>{{group.items[0]?.employeeName}}</strong> · {{q(group.grouped?'groupApplication':'singleApplication')}} · {{group.items.length}} {{q('shiftCount')}} · {{hours(groupSeconds(group))}}</span><span>{{summary(group)}}</span><small>{{stamp(group.items[0]!.startAt)}} → {{stamp(group.items[group.items.length-1]!.endAt)}}</small><small>{{q('createdAtLabel')}} {{stamp(group.items[0]!.createdAt)}} · {{group.id.slice(0,8)}}</small></button><LeaveGroupPanel v-if="expanded===group.id" :group="group" :zone="zone" :base="base" :from="group.from||from" :to="group.to||to" :busy="busy||!!frozenGroup" @all="decideAll(group.id,$event)" @single="decideSingle"/></article><p v-if="!items.length" class="wf-empty">{{t('empty')}}</p></template><p v-if="frozenGroup" role="alert" class="wf-hint">{{q('groupRetryHint')}} <button :disabled="busy" @click="decideAll(frozenGroup.id,frozenGroup.command)">{{q('retryGroupReview')}}</button></p><p class="wf-hint">{{p('timezone')}}: {{zone}} · {{q('cancelHint')}}</p></section></template>
<style scoped>.wf-leave-group{border:1px solid #dbe4ee;border-radius:8px;margin:10px 0;overflow:hidden}.wf-leave-group-heading{width:100%;display:grid;grid-template-columns:1fr auto;gap:6px 14px;text-align:left;border:0;border-radius:0;padding:12px;background:#f6f9fc;white-space:normal}.wf-leave-group-heading small{color:#657c98}@media(max-width:650px){.wf-leave-group-heading{grid-template-columns:1fr}}</style>
