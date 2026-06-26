<script setup lang="ts">
import type { ProductLinePriceStatus } from '../../../types/platformProductLineBilling'

defineProps<{
  disabled: boolean
  form: {
    monthlyAmount: number
    yearlyAmount: number
    currency: string
    monthlyStatus: ProductLinePriceStatus
    yearlyStatus: ProductLinePriceStatus
  }
  saving: boolean
}>()

const emit = defineEmits<{
  save: []
}>()
</script>

<template>
  <form class="price-form-module" @submit.prevent="emit('save')">
    <h3>定价</h3>
    <label>
      <span>月付价格</span>
      <input v-model.number="form.monthlyAmount" type="number" min="0" step="0.01" :disabled="disabled">
    </label>
    <label>
      <span>年付价格</span>
      <input v-model.number="form.yearlyAmount" type="number" min="0" step="0.01" :disabled="disabled">
    </label>
    <label>
      <span>币种</span>
      <input v-model.trim="form.currency" maxlength="3" :disabled="disabled">
    </label>
    <div class="price-status-grid">
      <label>
        <span>月付状态</span>
        <select v-model="form.monthlyStatus" :disabled="disabled">
          <option value="active">启用</option>
          <option value="disabled">停用</option>
        </select>
      </label>
      <label>
        <span>年付状态</span>
        <select v-model="form.yearlyStatus" :disabled="disabled">
          <option value="active">启用</option>
          <option value="disabled">停用</option>
        </select>
      </label>
    </div>
    <button class="primary-button" type="submit" :disabled="saving || disabled">
      {{ saving ? '保存中' : '保存定价' }}
    </button>
  </form>
</template>

<style scoped>
.price-form-module {
  display: grid;
  gap: 14px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
  padding: 16px;
}

.price-form-module h3 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.price-form-module label {
  display: grid;
  gap: 6px;
}

.price-form-module label span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.price-form-module input,
.price-form-module select {
  min-height: 38px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 8px 10px;
  font: inherit;
}

.price-status-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.primary-button {
  min-height: 36px;
  border: 0;
  border-radius: 6px;
  color: #ffffff;
  background: #0f766e;
  padding: 0 14px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.primary-button:disabled {
  opacity: 0.55;
  cursor: default;
}
</style>
