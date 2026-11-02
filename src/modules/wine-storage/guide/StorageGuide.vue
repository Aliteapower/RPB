<script setup lang="ts">
import {computed,ref} from 'vue'
import StorageGuideAccess from './StorageGuideAccess.vue'
import content from './content.json'
import guidePdf from './guide-zh.pdf'
import {useMerchantStorageText} from '../merchant/text'
import './guide.css'
defineProps<{store:string;enabled:boolean}>()
const {locale}=useMerchantStorageText(),language=computed(()=>locale.value.startsWith('en')?'en':'zh'),selected=ref('deposit')
const current=computed(()=>content.sections.find(s=>s.id===selected.value)??content.sections[0])
const x=(zh:string,en:string)=>language.value==='en'?en:zh
</script>
<template><section class="storage-guide" :aria-label="content.title[language]">
 <header class="storage-guide-toolbar"><p class="cards-muted">{{content.intro[language]}}</p><a class="storage-guide-download" :href="guidePdf" download="酒水寄存操作指南.pdf">{{x('下载通用文字版（不含入口）','Generic PDF (without store entries)')}}</a></header>
 <StorageGuideAccess :key="store" :store="store" :enabled="enabled"/>
 <p class="storage-guide-key">{{content.keyRule[language]}}</p>
 <nav class="storage-guide-tasks" :aria-label="x('选择要办理的任务','Choose a task')"><button v-for="section in content.sections" :key="section.id" type="button" :class="{primary:selected===section.id}" :aria-pressed="selected===section.id" @click="selected=section.id">{{section.title[language]}}</button></nav>
 <article class="storage-panel storage-guide-current" aria-live="polite"><h3>{{current.title[language]}}</h3><p class="cards-muted">{{current.hint[language]}}</p><ol class="storage-guide-steps"><li v-for="(step,index) in current.steps" :key="index"><strong>{{step.title[language]}}</strong><p>{{step.text[language]}}</p></li></ol></article>
 <details class="storage-panel storage-guide-troubleshooting"><summary>{{x('遇到问题，先这样处理','If something goes wrong')}}</summary><dl><template v-for="tip in content.tips" :key="tip.title.en"><dt>{{tip.title[language]}}</dt><dd>{{tip.text[language]}}</dd></template></dl></details>
 <p class="cards-muted storage-guide-version">{{x('指南版本','Guide version')}} {{content.version}} · {{x('不同岗位的可用入口以实际权限为准。','Available controls depend on your permissions.')}}</p>
</section></template>
