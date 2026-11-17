<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useAuthSessionStore} from '../../../stores/authSession'
import {walletApi} from '../../wallet/api'
import type {Context} from '../../wallet/types'
import {campaignLocalTime} from '../../wallet/marketingTime'
import {voucherStaff, type Campaign, type Summary} from './api'
import VoucherCampaignEditor from './VoucherCampaignEditor.vue'
import VoucherShareDialog from './VoucherShareDialog.vue'

const props = defineProps<{store: string; enabled: boolean}>()
const auth = useAuthSessionStore()
const path = '/voucher-campaigns'
const list = ref<Summary[]>([])
const context = ref<Context>()
const busy = ref(false)
const error = ref('')
const sharing = ref<Summary>()
const editing = ref<Campaign | null>()
const confirmDelete = ref<string>()
const timezone = computed(() => context.value?.store.timezone ?? '')
const stores = computed(() => auth.authorizedStores.filter(s => s.status === 'active')
  .map(s => ({id:s.storeId, name:s.storeName})))
const statusLabels = {draft:'草稿',active:'已启用',disabled:'已停用',archived:'已归档'}

async function run(work: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''

  try {
    await work()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function refresh() {
  list.value = await voucherStaff<Summary[]>(props.store, path)
}

onMounted(() => run(async () => {
  await Promise.all([
    refresh(),
    walletApi(props.store).get<Context>('/context').then(value => { context.value = value }),
    auth.ensureAuthorizedStores()
  ])
}))

function activeLabel(item: Summary) {
  if (item.state !== 'active') return statusLabels[item.state]
  if (Date.now() >= Date.parse(item.endsAt)) return '已结束'
  if (Date.now() < Date.parse(item.startsAt)) return '未开始'
  return item.issued >= item.total ? '已领完' : '领取中'
}

function dates(item: Summary) {
  if (!timezone.value) return ''
  return [item.startsAt, item.endsAt].map(d => campaignLocalTime(d, timezone.value).replace('T',' ')).join(' — ')
}

function edit(item: Summary) {
  return run(async () => { editing.value = await voucherStaff<Campaign>(props.store, path + '/' + item.id) })
}

function saved() {
  editing.value = undefined
  void run(refresh)
}

function state(item: Summary, status: 'active' | 'disabled' | 'archived') {
  return run(async () => {
    await voucherStaff(props.store, path + '/' + item.id + '/status', 'POST', {status, version:item.version})
    await refresh()
  })
}

function remove(item: Summary) {
  return run(async () => {
    await voucherStaff(props.store, path + '/' + item.id + '?version=' + item.version, 'DELETE')
    confirmDelete.value = undefined
    await refresh()
  })
}


</script>

<template>
  <section class="cards-panel cards-plans">
    <header class="cards-section-heading">
      <h2>抢券活动</h2>
      <div class="voucher-actions">
        <button :disabled="busy" @click="run(refresh)">刷新</button>
        <button class="primary" :disabled="busy || !enabled || !timezone || editing !== undefined" @click="editing=null">新增抢券活动</button>
      </div>
    </header>
    <p v-if="!enabled" class="cards-notice">请先在页面顶部<a href="#cards-feature-settings">启用卡功能</a>。跨店活动须在各适用门店分别启用。</p>
    <p v-if="error" class="cards-error" role="alert">{{ error }}</p>
    <VoucherShareDialog v-if="sharing" :id="sharing.id" :name="sharing.name" :batch-code="sharing.activityCode" @close="sharing=undefined" />
    <p v-if="busy" role="status">处理中…</p>
    <VoucherCampaignEditor v-if="editing !== undefined && timezone" :key="editing?.id ?? 'new'"
      :store="store" :stores="stores" :timezone="timezone" :campaign="editing" @saved="saved" @cancel="editing=undefined" />
    <template v-else>
      <p v-if="!busy && !list.length && !error" class="cards-empty">暂无抢券活动</p>
      <article v-for="item in list" :key="item.id" class="cards-list-item cards-plan-item">
        <div class="cards-item-heading">
          <div class="cards-item-title">
            <h3>{{ item.name }}</h3>
            <span class="cards-badge">{{ item.kind === 'product' ? '产品券' : '代金券' }}</span>
            <span class="cards-badge">{{ activeLabel(item) }}</span>
          </div>
          <span>{{ item.activityCode }}</span>
        </div>
        <p>{{ dates(item) }}</p>
        <p>发行 {{ item.total }} · 已领 {{ item.issued }} · 剩余 {{ item.total - item.issued }} · 已核销 {{ item.redeemed }}</p>
        <div class="voucher-actions">
          <button :disabled="busy" @click="sharing=item">分享抢券二维码</button>
          <template v-if="item.state !== 'archived'">
            <button :disabled="busy || !enabled" @click="edit(item)">编辑</button>
            <button v-if="item.state === 'active'" :disabled="busy" @click="state(item,'disabled')">停用</button>
            <button v-else :disabled="busy || !enabled" @click="state(item,'active')">启用</button>
            <button v-if="item.issued" :disabled="busy" @click="state(item,'archived')">归档</button>
          </template>
          <button v-if="!item.issued" :disabled="busy" @click="confirmDelete=item.id">删除</button>
        </div>
        <div v-if="confirmDelete===item.id" class="voucher-actions">
          <span>删除此活动后，公开链接将失效。</span>
          <button :disabled="busy" @click="remove(item)">确认删除</button>
          <button :disabled="busy" @click="confirmDelete=undefined">取消</button>
        </div>
      </article>
      <RouterLink :to="'/stores/' + store + '/scan'">打开门店扫码台</RouterLink>
    </template>
  </section>
</template>

<style scoped>
.voucher-actions {display:flex; flex-wrap:wrap; align-items:center; gap:8px}
</style>
