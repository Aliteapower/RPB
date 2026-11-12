<script setup lang="ts">
import {computed,ref} from 'vue'
import {usePhaseThreeText} from '../phaseThreeText'
import {usePhaseText} from '../phaseText'
import {useWorkforce} from '../context'
import {leaveActions} from '../timekeepingUi'
import {localInput,hours} from '../time'
import type {LeaveGroup,LeaveGroupDecision} from '../leaveGroups'
import type {Leave,LeaveDecision} from '../phaseThreeTypes'
const props=defineProps<{group:LeaveGroup;zone:string;base:string;from:string;to:string;busy:boolean}>()
const emit=defineEmits<{all:[LeaveGroupDecision];single:[{id:string;expectedVersion:number;status:LeaveDecision;note:string}]}>()
const {home}=useWorkforce(),{q}=usePhaseThreeText(),{p}=usePhaseText(),note=ref('')
const actions=computed(()=>[...new Set(props.group.items.flatMap(l=>leaveActions(l,home.value)))])
const personal=computed(()=>actions.value.some(s=>s==='withdrawn'||s==='cancel_requested'))
function eligible(status:LeaveDecision){return props.group.items.filter(l=>leaveActions(l,home.value).includes(status))}
function needsNote(status:LeaveDecision){return status!=='withdrawn'}
function stamp(v:string){return localInput(v,props.zone).replace('T',' ')}
function label(status:LeaveDecision,group=false){
 if(group)return q(({approved:'groupApprove',rejected:'groupReject',withdrawn:'groupWithdraw',cancel_requested:'groupRequestCancellation',cancelled:'groupApproveCancellation',cancel_rejected:'groupRejectCancellation'} as const)[status])
 return status==='approved'?p('approve'):status==='rejected'?p('reject'):status==='withdrawn'?p('withdraw'):status==='cancel_requested'?q('requestCancellation'):status==='cancelled'?q('approveCancellation'):q('rejectCancellation')
}
function single(item:Leave,status:LeaveDecision){if(props.busy||(needsNote(status)&&!note.value.trim())||!leaveActions(item,home.value).includes(status))return;emit('single',{id:item.id,expectedVersion:item.version,status,note:note.value.trim()})}
function all(status:LeaveDecision){const items=eligible(status);if(props.busy||(needsNote(status)&&!note.value.trim())||!items.length)return;emit('all',{status,note:note.value.trim(),items:items.map(l=>({id:l.id,expectedVersion:l.version}))})}
</script>
<template><section class="wf-leave-group-detail"><p class="wf-hint">{{q('groupFullHint')}}</p><label v-if="actions.length">{{personal?p('reason'):p('note')}}<textarea v-model="note" :disabled="busy" maxlength="500" rows="2" :aria-label="q(personal?'groupRequestNote':'groupReviewNote')"></textarea></label><div v-if="group.grouped&&actions.length" class="wf-form-actions"><button v-for="(action,index) in actions" :key="action" :class="{primary:index===0}" :disabled="busy||(needsNote(action)&&!note.trim())" @click="all(action)">{{label(action,true)}} · {{eligible(action).length}}</button></div><div class="wf-table-wrap"><table><thead><tr><th>{{q('leaveTypes')}}</th><th>{{q('period24')}}</th><th>{{q('durationLabel')}}</th><th>{{p('status')}}</th><th>{{p('note')}}</th><th>{{q('groupActions')}}</th></tr></thead><tbody><tr v-for="item in group.items" :key="item.id"><td>{{item.typeName}}<br><small>{{q(item.paid?'paid':'unpaid')}}</small></td><td>{{stamp(item.startAt)}} → {{stamp(item.endAt)}}</td><td>{{hours((Date.parse(item.endAt)-Date.parse(item.startAt))/1000)}}</td><td>{{q(item.status)}}</td><td>{{item.reviewNote||'—'}}</td><td><div class="wf-leave-day-actions"><button v-for="action in leaveActions(item,home)" :key="action" :disabled="busy||(needsNote(action)&&!note.trim())" @click="single(item,action)">{{label(action)}}</button><RouterLink :to="{path:base+'/leaves/'+item.id,query:{from,to}}">{{q('detail')}}</RouterLink></div></td></tr></tbody></table></div><p class="wf-hint wf-preserve">{{p('reason')}}：{{group.items[0]?.reason}}</p></section></template>
<style scoped>.wf-leave-group-detail{padding:12px;border-top:1px solid #dbe4ee}.wf-leave-group-detail label{margin:0}.wf-leave-day-actions{display:flex;flex-wrap:wrap;gap:6px;align-items:center}.wf-leave-day-actions button{padding:5px 9px}.wf-leave-group-detail td{vertical-align:top}.wf-leave-group-detail textarea{max-width:700px}</style>
