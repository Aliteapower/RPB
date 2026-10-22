<script setup lang="ts">
import { useAuthSessionStore } from '../../../stores/authSession'
import StoreSwitcher from '../../../components/store/StoreSwitcher.vue'
import FrontendLocaleSwitcher from '../../../components/common/FrontendLocaleSwitcher.vue'
import type { Branding } from '../types'
import LedgerIcon from './LedgerIcon.vue'
defineProps<{ branding?: Branding; storeId: string; tab: string; admin: boolean; currency?: string }>()
defineEmits<{ navigate: [tab: 'record' | 'history' | 'settings'] }>()
const auth = useAuthSessionStore()
</script>
<template>
  <div class="app-shell">
    <aside class="sidebar">
      <RouterLink class="brand" :to="`/stores/${storeId}/staff`"><span class="brand-symbol" :class="{ 'custom-logo': branding?.logo_data }"><img v-if="branding?.logo_data" :src="branding.logo_data" alt="门店 Logo" /><LedgerIcon v-else name="book" /></span><span class="brand-copy">{{ branding?.store_name || '每日营业记录' }}<small>{{ branding?.store_name_en || 'DAILY LEDGER' }}</small></span></RouterLink>
      <span class="nav-label">工作台</span>
      <nav aria-label="账本工作台">
        <button class="nav-item" :class="{ active: tab === 'record' }" :aria-current="tab === 'record' ? 'page' : undefined" @click="$emit('navigate', 'record')"><LedgerIcon name="book" /><span>营业记录</span><i v-if="tab === 'record'"></i></button>
        <button class="nav-item" :class="{ active: tab === 'history' }" :aria-current="tab === 'history' ? 'page' : undefined" @click="$emit('navigate', 'history')"><LedgerIcon name="chart" /><span>历史账目</span></button>
        <template v-if="admin"><div class="nav-divider"></div><span class="nav-label">管理</span><button class="nav-item" :class="{ active: tab === 'settings' }" @click="$emit('navigate', 'settings')"><LedgerIcon name="settings" /><span>门店与记账设置</span></button></template>
        <RouterLink v-if="auth.isTenantAdmin" class="nav-item" :to="`/stores/${storeId}/admin/staff`"><span class="people-icon">♧</span><span>员工管理</span></RouterLink>
      </nav>
      <div class="sidebar-bottom"><div class="ledger-mode"><span class="status-dot saved"></span>当日累计盘点模式</div><div class="account"><span class="avatar">{{ (auth.user?.displayName || '员').slice(0, 1) }}</span><div><strong>{{ auth.user?.displayName }}</strong><small>{{ admin ? '管理员' : '员工' }}</small></div><RouterLink class="icon-button" :to="`/stores/${storeId}/staff`" aria-label="员工首页" title="员工首页">↪</RouterLink></div></div>
    </aside>
    <div class="workspace">
      <header class="topbar"><span>门店营业管理 <span class="breadcrumb">/ {{ tab === 'history' ? '历史账目' : '每日记录' }}</span></span><div class="platform-tools"><StoreSwitcher surface="staff" /><span class="live-dot"></span><span>{{ currency || 'SGD' }}</span><span class="top-user">· {{ auth.user?.displayName }}</span><FrontendLocaleSwitcher /></div></header>
      <main><slot /></main><footer class="page-footer"><span class="footer-brand">{{ branding?.store_name_en }}</span><span>清楚记录每一天</span></footer>
    </div>
  </div>
</template>
