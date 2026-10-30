<script setup lang="ts">
import {ref} from 'vue'
import {captureLocation,useWorkModeText,type WorkPoint} from '../workMode'
const props=defineProps<{point:WorkPoint}>(),{w}=useWorkModeText(),locating=ref(false),failure=ref('')
const emit=defineEmits<{locating:[value:boolean]}>()
async function locate(){if(locating.value)return;locating.value=true;emit('locating',true);failure.value='';try{const v=await captureLocation();props.point.latitude=v.latitude;props.point.longitude=v.longitude}catch{failure.value=w('locationError')}finally{locating.value=false;emit('locating',false)}}
</script>
<template><div class="wf-filters"><label>{{w('label')}}<input v-model="point.label" required maxlength="200"></label><label>{{w('latitude')}}<input v-model.number="point.latitude" type="number" min="-90" max="90" step="any" required></label><label>{{w('longitude')}}<input v-model.number="point.longitude" type="number" min="-180" max="180" step="any" required></label><label>{{w('radius')}}<input v-model.number="point.radiusMeters" type="number" min="10" max="1000" step="1" required></label><button type="button" :disabled="locating" @click="locate">{{w(locating?'locating':'locate')}}</button><p v-if="failure" role="alert" class="wf-error">{{failure}}</p></div></template>
