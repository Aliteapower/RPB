<script setup lang="ts">
import {onBeforeUnmount, ref, watch} from 'vue'
import {useCardText} from '../text'
import {voucherStaff, type VoucherReportPage} from '../vouchers/api'

const props = defineProps<{store: string}>()
const {locale} = useCardText()
const x = (zh: string, en: string) => locale.value.startsWith('en') ? en : zh
const query = ref('')
const kind = ref('')
const page = ref(0)
const result = ref<VoucherReportPage>()
const busy = ref(false)
const error = ref('')
let applied = new URLSearchParams()
let generation = 0

async function load(index: number) {
  const current = ++generation
  busy.value = true
  error.value = ''
  const params = new URLSearchParams(applied)
  params.set('page', String(index))
  try {
    const data = await voucherStaff<VoucherReportPage>(props.store, '/voucher-campaign-report?' + params)
    if (current !== generation) return
    result.value = data
    page.value = index
  } catch (e) {
    if (current === generation) error.value = e instanceof Error ? e.message : String(e)
  } finally {
    if (current === generation) busy.value = false
  }
}

function search() {
  applied = new URLSearchParams({query:query.value.trim(),kind:kind.value})
  result.value = undefined
  void load(0)
}

function reset() {
  query.value = ''
  kind.value = ''
  search()
}

watch(() => props.store, reset, {immediate:true})
onBeforeUnmount(() => generation++)
</script>

<template>
  <form class="card-report-filters" @submit.prevent="search">
    <label>{{x('活动名称或批次号','Campaign name or batch code')}}
      <input v-model="query" maxlength="120">
    </label>
    <label>{{x('券类型','Voucher type')}}
      <select v-model="kind">
        <option value="">{{x('全部券类型','All voucher types')}}</option>
        <option value="cash">{{x('代金券','Cash voucher')}}</option>
        <option value="product">{{x('产品券','Product voucher')}}</option>
      </select>
    </label>
    <div class="cards-actions">
      <button class="primary" :disabled="busy">{{x('查询','Search')}}</button>
      <button type="button" :disabled="busy" @click="reset">{{x('重置','Reset')}}</button>
    </div>
  </form>
  <p class="cards-caption">{{x('按活动归属门店统计。已过期指已领取、未核销且超过该券使用日期。','By campaign owner store. Expired counts claimed, unredeemed vouchers past their own validity date.')}}</p>
  <p v-if="busy" role="status">{{x('加载中…','Loading…')}}</p>
  <p v-if="error" role="alert" class="cards-error">{{error}}</p>
  <p v-else-if="result && !result.items.length" class="cards-empty">{{x('暂无符合条件的活动券记录','No matching voucher campaigns')}}</p>
  <template v-if="result?.items.length">
    <div class="cards-table-scroll">
      <table class="cards-data-table">
        <thead><tr>
          <th scope="col">{{x('活动 / 批次号','Campaign / batch code')}}</th>
          <th scope="col">{{x('发行总量','Issue limit')}}</th>
          <th scope="col">{{x('已领取','Claimed')}}</th>
          <th scope="col">{{x('已核销','Redeemed')}}</th>
          <th scope="col">{{x('已过期','Expired')}}</th>
        </tr></thead>
        <tbody><tr v-for="row in result.items" :key="row.id">
          <td><strong>{{row.name}}</strong><small>{{row.activityCode}} · {{row.kind==='cash' ? x('代金券','Cash voucher') : x('产品券','Product voucher')}}</small></td>
          <td>{{row.total}}</td><td>{{row.issued}}</td><td>{{row.redeemed}}</td><td>{{row.expired}}</td>
        </tr></tbody>
      </table>
    </div>
    <nav class="card-report-pagination" :aria-label="x('报表分页','Report pagination')">
      <button type="button" :disabled="busy || page===0" @click="load(page-1)">{{x('上一页','Previous')}}</button>
      <span>{{x('第','Page')}} {{page+1}} {{x('页','')}}</span>
      <button type="button" :disabled="busy || !result.hasNext" @click="load(page+1)">{{x('下一页','Next')}}</button>
    </nav>
  </template>
</template>
