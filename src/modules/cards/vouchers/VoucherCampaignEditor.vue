<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import DateInput from '../../../components/common/DateInput.vue'
import {moduleRequest as catalogRequest} from '../../catalog/api'
import type {Product} from '../../catalog/types'
import {campaignInstant, campaignLocalTime} from '../../wallet/marketingTime'
import {voucherStaff, type Campaign, type VoucherKind, type VoucherStore} from './api'
import {cropVoucherImage} from './images'

const props = defineProps<{store: string; stores: VoucherStore[]; timezone: string; campaign: Campaign | null}>()
const emit = defineEmits<{saved: []; cancel: []}>()
const path = '/voucher-campaigns'
const current = props.campaign
const face = current?.face
const local = (time?: string) => time ? campaignLocalTime(time, props.timezone) : ''
const form = ref({
  name: face?.name ?? '',
  kind: face?.kind ?? 'cash' as VoucherKind,
  total: current?.total ?? 1,
  productId: face?.productId ?? '',
  quantity: face?.quantity ?? 1,
  value: face ? (face.valueCents / 100).toFixed(2) : '',
  stores: face?.stores.map(s => s.id) ?? [props.store],
  startsAt: local(current?.startsAt),
  endsAt: local(current?.endsAt),
  validFrom: face?.validFrom ?? '',
  validThrough: face?.validThrough ?? '',
  rules: face?.rules ?? '',
  image: face?.image ?? null as string | null
})
const busy = ref(false)
const imageBusy = ref(false)
const productsLoading = ref(false)
const error = ref('')
const productError = ref('')
const products = ref<Product[]>([])
const manualValue = ref(!!face)
const pricingStore = computed(() => form.value.stores.includes(props.store) ? props.store : form.value.stores[0])
const selected = computed(() => products.value.find(p => p.id === form.value.productId))
const choices = computed(() => {
  const stores = new Map(props.stores.map(s => [s.id, s]))
  for (const s of face?.stores ?? []) if (!stores.has(s.id)) stores.set(s.id, s)
  return [...stores.values()]
})

watch([() => form.value.kind, pricingStore], async ([kind, id], _, cleanup) => {
  let active = true
  cleanup(() => { active = false })
  products.value = []
  productError.value = ''
  productsLoading.value = !!id && kind === 'product'
  if (!productsLoading.value || !id) return
  try {
    const list = await catalogRequest<Product[]>('/api/v1/stores/' + encodeURIComponent(id) + '/catalog','/products')
    if (active) products.value = list.filter(p => p.listingEnabled && p.status === 'active' && p.channels.includes('cards'))
  } catch (e) {
    if (active) productError.value = e instanceof Error ? e.message : String(e)
  } finally {
    if (active) productsLoading.value = false
  }
}, {immediate:true})

watch([selected, () => form.value.quantity], ([product, quantity]) => {
  if (!manualValue.value && product?.priceCents != null) form.value.value = (product.priceCents * Number(quantity) / 100).toFixed(2)
})

function changeKind() {
  manualValue.value = false
  form.value.value = ''
  form.value.productId = ''
}

async function imageChanged(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  imageBusy.value = true
  error.value = ''
  try {
    form.value.image = await cropVoucherImage(file)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    imageBusy.value = false
    input.value = ''
  }
}

async function save() {
  if (busy.value || imageBusy.value) return
  busy.value = true
  error.value = ''
  try {
    const f = form.value
    const product = f.kind === 'product'
    if (!f.stores.length) throw new Error('请选择适用门店')
    if (product && !selected.value) throw new Error('请选择已上架的商品')
    await voucherStaff(props.store, current ? path + '/' + current.id : path, current ? 'PUT' : 'POST', {
      name:f.name, kind:f.kind, total:f.total, stores:f.stores,
      productId:product ? f.productId : null, quantity:product ? f.quantity : null,
      valueCents:f.value === '' ? null : Math.round(Number(f.value) * 100),
      startsAt:campaignInstant(f.startsAt, props.timezone), endsAt:campaignInstant(f.endsAt, props.timezone),
      validFrom:f.validFrom, validThrough:f.validThrough, rules:f.rules, image:f.image, version:current?.version ?? null
    })
    emit('saved')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <form class="cards-voucher-form" @submit.prevent="save">
    <p v-if="error" class="cards-error" role="alert">{{ error }}</p>
    <fieldset :disabled="busy">
      <label>活动名称 <input v-model.trim="form.name" maxlength="120" required></label>
      <label>券类型
        <select v-model="form.kind" :disabled="!!current?.issued" @change="changeKind">
          <option value="cash">代金券</option><option value="product">产品券</option>
        </select>
      </label>
      <label>发行数量 <input v-model.number="form.total" type="number" :min="Math.max(1, current?.issued ?? 0)" max="1000000" required></label>
      <fieldset class="voucher-stores">
        <legend>适用门店</legend>
        <label v-for="s in choices" :key="s.id" class="check">
          <input v-model="form.stores" type="checkbox" :value="s.id"> {{ s.name }}
        </label>
      </fieldset>
      <template v-if="form.kind === 'product'">
        <p v-if="productError" class="cards-error" role="alert">{{ productError }}</p>
        <label>兑换商品
          <select v-model="form.productId" :disabled="productsLoading" required>
            <option value="">{{ productsLoading ? '正在加载产品目录…' : '选择产品目录商品' }}</option>
            <option v-if="form.productId && !selected" :value="form.productId" disabled>{{ face?.productName ?? '当前商品' }}（不可用）</option>
            <option v-for="p in products" :key="p.id" :value="p.id">{{ p.nameZh || p.nameEn }} · {{ p.unit }} · {{ p.code }}</option>
          </select>
        </label>
        <label>兑换数量 <input v-model.number="form.quantity" type="number" min="1" max="10000" required></label>
      </template>
      <label>{{form.kind === 'cash' ? '抵扣面额' : '整张券展示价值'}}
        <input v-model="form.value" type="number" step="0.01" min="0.01" max="1000000" required @input="manualValue = true">
      </label>
      <div class="voucher-dates">
        <label>领取开始时间 <DateInput v-model="form.startsAt" type="datetime-local" required /></label>
        <label>领取结束时间 <DateInput v-model="form.endsAt" type="datetime-local" required /></label>
        <label>用券开始日期 <DateInput v-model="form.validFrom" required /></label>
        <label>用券结束日期（含） <DateInput v-model="form.validThrough" required /></label>
      </div>
      <p class="cards-caption">活动时区 {{ timezone }} · 免费发放</p>
      <label>活动规则 <textarea v-model="form.rules" rows="5" maxlength="4000"></textarea></label>
      <label>券图（居中裁切 4:3，压缩至 1 MB 以内）
        <input type="file" accept="image/*" :disabled="imageBusy" @change="imageChanged">
      </label>
      <img v-if="form.image" class="voucher-preview" :src="form.image" alt="4:3 券图预览">
      <div class="voucher-buttons">
        <button v-if="form.image" type="button" @click="form.image=null">移除券图</button>
        <button class="primary" :disabled="imageBusy || productsLoading">保存</button>
        <button type="button" @click="emit('cancel')">取消</button>
      </div>
    </fieldset>
  </form>
</template>

<style scoped>
.cards-voucher-form > fieldset {border:0; padding:0; display:grid; gap:12px; min-width:0; margin:0}
.cards-voucher-form label:not(.check) {display:grid; gap:6px}
.voucher-dates {display:grid; grid-template-columns:repeat(2, minmax(0,1fr)); gap:12px}
.voucher-stores {display:flex; flex-wrap:wrap; gap:10px}
.voucher-buttons {display:flex; flex-wrap:wrap; gap:8px}
.voucher-preview {max-width:min(280px,100%); aspect-ratio:4/3; object-fit:cover}
@media(max-width:550px) {.voucher-dates {grid-template-columns:1fr}}
</style>
