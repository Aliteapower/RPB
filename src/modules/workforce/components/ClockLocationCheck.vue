<script setup lang="ts">
import {onBeforeUnmount,ref,watch} from 'vue'
import {captureRawLocation,workModeError,useWorkModeText,type WorkPoint,type ClockLocation} from '../workMode'
import {useWorkforceText} from '../text'
import {useClockSiteText} from '../clockSiteText'
import {distanceMeters,type ClockSiteCheck} from '../clockSite'
import type {WorkforceApi} from '../api'
const props=defineProps<{storeId:string;point:WorkPoint;version?:number;api?:WorkforceApi;disabled?:boolean}>()
const {error}=useWorkforceText()
const {s,english}=useClockSiteText(),{w}=useWorkModeText(),busy=ref(false),failure=ref(''),result=ref<ClockSiteCheck|null>(null)
let revision=0
function invalidate(){revision++;result.value=null;failure.value='';busy.value=false}
watch(()=>[props.storeId,props.version,props.point.label,props.point.latitude,props.point.longitude,props.point.radiusMeters],invalidate,{flush:'sync'})
onBeforeUnmount(invalidate)
async function check(){if(busy.value||props.disabled)return;const rev=++revision,target={...props.point},api=props.api;busy.value=true;result.value=null;failure.value='';try{const location:ClockLocation=await captureRawLocation();if(rev!==revision)return;const value=api?await api.put<ClockSiteCheck>('/clock-site/check',{expectedVersion:props.version,point:target,location}):{distanceMeters:distanceMeters(location,target),radiusMeters:target.radiusMeters,accuracyMeters:location.accuracyMeters,allowed:false,code:null,siteVersion:0,checkedAt:location.capturedAt};if(rev===revision)result.value=value}catch(e){if(rev===revision)failure.value=workModeError(e,english())||(e&&typeof e==='object'&&'code'in e?error(e):w('locationError'))}finally{if(rev===revision)busy.value=false}}
</script>
<template><section class="wf-section"><p>{{api?s('hint'):s('pinned')}}</p><p v-if="!api">{{point.label}} · {{s('approx')}}</p><button type="button" :disabled="disabled||busy" @click="check">{{busy?s('checking'):s(api?'check':'preview')}}</button><p v-if="failure" role="alert">{{failure}}</p><div v-if="result" role="status"><p v-if="api">{{s(result.allowed?'pass':'fail')}} <span v-if="result.code">{{workModeError({code:result.code},english())||result.code}}</span></p><p>{{s('distance')}}: {{!api?s('about'):''}} {{result.distanceMeters===null?'—':Math.round(result.distanceMeters)}} {{s('meters')}} · {{s('accuracy')}}: {{result.accuracyMeters===null?'—':Math.round(result.accuracyMeters)}} {{s('meters')}} · {{s('radius')}}: {{result.radiusMeters}} {{s('meters')}}</p><p>{{s('time')}}: {{result.checkedAt}}</p></div></section></template>
