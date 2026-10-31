<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {permittedModes,useWorkModeText} from '../workMode'
import {workModeDisplayText,type AssignmentGroup} from '../workModeDisplay'
defineProps<{groups:AssignmentGroup[]}>()
const {w}=useWorkModeText(),{locale}=useI18n(),ui=computed(()=>workModeDisplayText(locale.value.startsWith('en')))
</script>
<template>
 <div class="wm-policy-list">
  <article v-for="g in groups" :key="g.assignment.id" class="wm-policy-card" :data-policy-group="g.status">
   <header><strong>{{permittedModes(g.assignment.mode,g.assignment.allowedModes).map(m=>w(m)).join(' / ')}}</strong><span class="wf-badge" :class="{'wm-current':g.status==='current','wf-badge-warning':g.status==='upcoming'}">{{ui[g.status]}}</span></header>
   <p>{{g.assignment.effectiveFrom}} {{ui.from}}<span v-if="g.until"> · {{ui.until}} {{g.until}}</span><span v-if="permittedModes(g.assignment.mode,g.assignment.allowedModes).length>1"> · {{w('defaultMode')}}: {{w(g.assignment.mode)}}</span></p>
   <p class="wf-hint">{{w(g.assignment.origin==='position'?'position':'personal')}}<span v-if="g.assignment.positionId"> · {{ui.positionVersion}} {{g.assignment.positionVersion}}</span></p>
   <details v-if="g.entries.length>1"><summary>{{ui.details}} · {{g.entries.length}} {{ui.records}}</summary><ul><li v-for="a in g.entries" :key="a.id">{{a.effectiveFrom}} · {{w('version')}} {{a.version}}</li></ul></details>
  </article>
 </div>
</template>
