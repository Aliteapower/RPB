<script setup lang="ts">
import type {ScheduledAdoption} from '../payTemplatePublication'
import {usePhaseText} from '../phaseText'
defineProps<{items?:ScheduledAdoption[]}>()
const {english}=usePhaseText(),x=(z:string,e:string)=>english()?e:z
</script>
<template><p v-for="s in items||[]" :key="s.adoption.id" class="wf-hint">{{x('批准待生效','Approved, future effective date')}} · {{s.effectiveDate}} · {{x('发布／本店版本','Release / store version')}} {{s.adoption.releaseVersion}} / {{s.adoption.storeVersion}} · {{x('当前有效采用保持原状','Current effective adoption remains unchanged')}}</p></template>
