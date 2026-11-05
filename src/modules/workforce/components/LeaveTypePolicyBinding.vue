<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {usePhasePage} from '../phasePage'
import {leaveKind,type LeaveBinding,type LeaveKind} from '../complianceTypes'
import type {LeaveType} from '../phaseThreeTypes'
const props=defineProps<{types:LeaveType[]}>(),{api,english,busy,failure,write}=usePhasePage(),bindings=ref<LeaveBinding[]>([]),choices=ref<Record<string,string>>({})
async function refresh(){try{bindings.value=await api().get<LeaveBinding[]>('/leave-type-policies');choices.value=Object.fromEntries(bindings.value.map(b=>[b.typeId,b.kind]))}catch{failure.value=english()?'Could not load policy bindings. Retry.':'假期规则绑定加载失败，请重试。'}}
function save(type:LeaveType){const kind=choices.value[type.id];if(!kind)return;void write(()=>api().put('/leave-type-policies/'+type.id,{expectedVersion:bindings.value.find(b=>b.typeId===type.id)?.version||0,kind}),refresh)}
onMounted(refresh)
</script>
<template><section class="wf-section"><h3>{{english()?'Next: connect leave types to balances':'下一步：绑定请假类型与额度'}}</h3><p class="wf-hint">{{english()?'Paid is not a leave category. Annual, outpatient and hospital leave use different balances. Changes affect new applications only. Other statutory leave requires its own reviewed policy.':'带薪标志不等于假期类别。年假、门诊及住院假对应不同额度；绑定仅用于新申请，其他法定假不能冒充年假。'}}</p><p v-if="failure" role="alert">{{failure}}</p><article v-for="type in types" :key="type.id" class="wf-history"><form class="wf-inline" @submit.prevent="save(type)"><label>{{type.name}}<select v-model="choices[type.id]" required :disabled="busy"><option value="" disabled>{{english()?'Choose a reviewed policy':'选择已核对的假期规则'}}</option><option v-for="kind in (type.paid?['annual','sick','hospital','company']:['unpaid'])" :key="kind" :value="kind">{{leaveKind(kind,english())}}</option></select></label><button :disabled="busy||!choices[type.id]">{{english()?'Save this binding':'保存本项绑定'}}</button></form></article></section></template>
