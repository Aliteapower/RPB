<script setup lang="ts">
import {onMounted, onUnmounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useVisiblePageRefresh} from '../composables/useVisiblePageRefresh'
import {createVersionProbe} from './versionProbe'

const {locale} = useI18n()
const newer = ref(''), confirming = ref(false)
const probe = createVersionProbe(import.meta.env.VITE_FRONTEND_VERSION ?? 'development', version => { newer.value = version; if (!version) confirming.value = false })
onMounted(() => { if (document.visibilityState !== 'hidden') void probe.check() })
onUnmounted(() => probe.stop())
useVisiblePageRefresh(probe.check, () => true, 60_000)
function tr(zh: string, en: string) { return locale.value.startsWith('en') ? en : zh }
function reload() { if (newer.value && confirming.value) window.location.reload() }
</script>

<template>
  <aside v-if="newer" class="frontend-update-notice" role="status" :aria-label="tr('版本更新提醒','Version update notice')">
    <div>
      <strong>{{tr('有新版本可用','An update is available')}}</strong>
      <p>{{tr('当前页面不会自动刷新。请先保存或记下未保存内容，再更新页面。','This page will not refresh automatically. Save or note any unsaved work before updating.')}}</p>
      <p v-if="confirming">{{tr('重新载入将清除本页未保存的内容。确认已处理后再继续。','Reloading clears unsaved work on this page. Continue only after handling it.')}}</p>
    </div>
    <div class="frontend-update-actions">
      <button v-if="!confirming" type="button" @click="confirming=true">{{tr('更新页面','Update page')}}</button>
      <template v-else>
        <button type="button" @click="confirming=false">{{tr('暂不更新','Not now')}}</button>
        <button type="button" @click="reload">{{tr('确认更新并重新载入','Confirm update and reload')}}</button>
      </template>
    </div>
  </aside>
</template>

<style scoped>
.frontend-update-notice{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;margin:12px 16px;padding:14px 16px;border:1px solid #b8d6ca;border-radius:12px;background:#eef8f2;color:#173d32;font-size:14px;line-height:1.5;overflow-wrap:anywhere}
.frontend-update-notice>div{min-width:0}.frontend-update-notice p{margin:6px 0 0}.frontend-update-actions{display:flex;flex-wrap:wrap;gap:8px}
.frontend-update-actions button{min-height:44px;max-width:100%;padding:8px 12px;border:1px solid #9cbdb0;border-radius:8px;background:white;color:#155f52;font:inherit;cursor:pointer}
.frontend-update-actions button:focus-visible{outline:3px solid #82b8ac;outline-offset:2px}
@media(max-width:640px){.frontend-update-notice{margin:10px 12px;padding:12px}.frontend-update-actions{width:100%}.frontend-update-actions button{flex:1 1 auto}}
</style>
