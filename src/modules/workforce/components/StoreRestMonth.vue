<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import {usePhasePage} from '../phasePage'
import {useHolidayText,type RestMonth} from '../holidayYears'
import {storeToday,addDays} from '../time'
import PhaseStatus from './PhaseStatus.vue'
const emit=defineEmits<{changed:[month:string]}>(),{api,home,current,load,write,loading,busy,failure,notice}=usePhasePage(),{h,weekday}=useHolidayText(),month=ref(storeToday().slice(0,7)),dates=ref<string[]>([]),fingerprint=ref('')
const days=computed(()=>{if(!/^\d{4}-\d{2}$/.test(month.value))return [];const result:string[]=[];for(let day=month.value+'-01';day.startsWith(month.value)&&result.length<31;day=addDays(day,1))result.push(day);return result})
function refresh(){fingerprint.value='';dates.value=[];if(home.value?.employeeManage)void load(()=>api().get<RestMonth>('/store-rest-months/'+month.value),result=>{dates.value=result.dates;fingerprint.value=result.fingerprint})}
function toggle(date:string){dates.value=dates.value.includes(date)?dates.value.filter(d=>d!==date):[...dates.value,date].sort()}
function save(){if(!fingerprint.value||!home.value?.employeeManage)return;void write(async()=>{const result=await api().put<RestMonth>('/store-rest-months/'+month.value,{fingerprint:fingerprint.value,dates:dates.value});if(current()){dates.value=result.dates;fingerprint.value=result.fingerprint;emit('changed',month.value)}})}
onMounted(refresh)
</script>
<template><section v-if="home?.employeeManage" class="wf-section"><h3>{{h('rest')}}</h3><p class="wf-hint">{{h('restHint')}}</p><label>{{h('month')}}<input v-model="month" type="month" :disabled="busy||loading" @change="refresh"></label><PhaseStatus :loading="loading" :failure="failure" :notice="notice"/><div class="wf-rest-days" v-if="fingerprint"><button v-for="date in days" :key="date" type="button" :disabled="busy||loading" :aria-pressed="dates.includes(date)" :aria-label="date+' '+weekday(date)" :class="dates.includes(date)?'primary':''" @click="toggle(date)">{{date.slice(-2)}}<small>{{weekday(date)}}</small></button></div><footer class="wf-form-actions"><span>{{h('selected')}} {{dates.length}}</span><button type="button" :disabled="busy||loading" @click="dates=[]">{{h('clear')}}</button><button type="button" :disabled="busy||loading" @click="refresh">{{h('reload')}}</button><button type="button" class="primary" :disabled="busy||loading||!fingerprint" @click="save">{{h('restSave')}}</button></footer></section></template>
<style scoped>.wf-rest-days{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:6px;margin-top:12px;max-width:660px}.wf-rest-days small{display:block;font-size:10px}</style>

