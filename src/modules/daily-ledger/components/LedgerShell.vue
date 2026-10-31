<script setup lang="ts">
import { useLedgerI18n } from '../i18n'
import { useAuthSessionStore } from '../../../stores/authSession'
import LedgerPageHeader from './LedgerPageHeader.vue'
import type { Branding } from '../types'
import LedgerIcon from './LedgerIcon.vue'
const { text } = useLedgerI18n()
defineProps<{ branding?: Branding; storeId: string; tab: string; admin: boolean; currency?: string; historyOnly?:boolean }>()
defineEmits<{ navigate: [tab: 'record' | 'history' | 'settings'] }>()
const auth = useAuthSessionStore()
</script>
<template>
  <div class="app-shell">
    <aside class="sidebar">

      <span class="nav-label">{{ text('工作台') }}</span>
      <nav :aria-label="text('账本工作台')">
        <button v-if="!historyOnly" class="nav-item" :class="{ active: tab === 'record' }" :aria-current="tab === 'record' ? 'page' : undefined" @click="$emit('navigate', 'record')"><LedgerIcon name="book" /><span>{{ text('营业记录') }}</span><i v-if="tab === 'record'"></i></button>
        <button class="nav-item" :class="{ active: tab === 'history' }" :aria-current="tab === 'history' ? 'page' : undefined" @click="$emit('navigate', 'history')"><LedgerIcon name="chart" /><span>{{ text('历史账目') }}</span></button>
        <template v-if="admin"><div class="nav-divider"></div><span class="nav-label">{{ text('管理') }}</span><button class="nav-item" :class="{ active: tab === 'settings' }" @click="$emit('navigate', 'settings')"><LedgerIcon name="settings" /><span>{{ text('记账设置') }}</span></button></template>
        <RouterLink v-if="auth.isTenantAdmin" class="nav-item" :to="`/stores/${storeId}/admin/staff`"><span class="people-icon">♧</span><span>{{ text('员工管理') }}</span></RouterLink>
      </nav>
      <div class="sidebar-bottom"><div class="ledger-mode"><span class="status-dot saved"></span>{{ text('当日累计盘点模式') }}</div></div>
    </aside>
    <div class="workspace">
      <LedgerPageHeader :branding="branding" :store-id="storeId" :tab="tab" :currency="currency"/>
      <main><slot /></main><footer class="page-footer"><span class="footer-brand">{{ branding?.store_name_en }}</span><span>{{ text('清楚记录每一天') }}</span></footer>
    </div>
  </div>
</template>
