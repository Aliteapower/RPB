<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import TenantAdminNav from '../../components/tenant-admin/TenantAdminNav.vue'
import { useTenantProducts } from './useTenantProducts'
const route = useRoute(), { t, te } = useI18n()
const store = computed(() => String(route.params.storeId || ''))
const { products, loading, saving, error, saved, changeCount, refresh, toggle } = useTenantProducts(store)
function reasonText(reason: string | null) {
  const key = `tenantProducts.reasons.${reason}`
  return te(key) ? t(key) : t('tenantProducts.unavailable')
}
</script>

<template>
  <div class="products-layout">
    <TenantAdminNav :key="changeCount" />
    <main class="products-main">
      <header><h1>{{ t('tenantProducts.title') }}</h1><button type="button" :disabled="loading || !!saving" @click="refresh">{{ t('common.actions.refresh') }}</button></header>
      <p>{{ t('tenantProducts.hint') }}</p>
      <p class="retention">{{ t('tenantProducts.retention') }}</p>
      <p v-if="loading" role="status">{{ t('common.actions.loading') }}</p>
      <p v-if="error" class="error" role="alert">{{ t(error) }}</p>
      <p v-if="saved" class="saved" role="status">{{ t('tenantProducts.saved') }}</p>
      <section v-for="product in products" :key="product.appKey" class="product-card">
        <div class="product-details">
          <h2 :id="`product-${product.appKey}`">{{ t(`tenantProducts.names.${product.appKey}`) }}</h2>
          <span :class="['state', { on: product.enabled }]">{{ t(product.enabled ? 'tenantProducts.enabled' : 'tenantProducts.disabled') }}</span>
          <p v-if="!product.canEnable" class="unavailable">{{ reasonText(product.unavailableReason) }}</p>
        </div>
        <button type="button" role="switch" :aria-checked="product.enabled" :aria-labelledby="`product-${product.appKey}`"
          :disabled="!!saving || (!product.enabled && !product.canEnable)" @click="toggle(product)">
          {{ t(saving === product.appKey ? 'tenantProducts.saving' : product.enabled ? 'tenantProducts.disable' : 'tenantProducts.enable') }}
        </button>
      </section>
    </main>
  </div>
</template>

<style scoped>
.products-layout{display:grid;grid-template-columns:250px minmax(0,1fr);min-height:100dvh;background:#f5f7fa;color:#0f172a}
.products-main{padding:28px;max-width:1040px;min-width:0;width:100%;box-sizing:border-box}
header{display:flex;align-items:center;justify-content:space-between;gap:12px}h1{font-size:24px;margin:0}h2{font-size:19px;margin:0 0 10px}
p{line-height:1.65;color:#475569}.retention{padding:16px;background:#eef6f5;border-radius:10px}
.product-card{display:flex;justify-content:space-between;align-items:center;gap:20px;padding:22px;background:white;border:1px solid #dbe3ea;border-radius:12px;margin-top:18px}
.product-details{min-width:0}.state{font-weight:700;color:#64748b}.state.on,.saved{color:#0f766e}.unavailable{margin-bottom:0}.error{color:#b91c1c}
button{flex-shrink:0;min-height:42px;padding:8px 18px;border:1px solid #0f766e;border-radius:8px;background:white;color:#0f766e;font:inherit;font-weight:700;cursor:pointer}
button:disabled{opacity:.5;cursor:default}button:focus-visible{outline:3px solid #14b8a6;outline-offset:3px}
@media(max-width:980px){.products-layout{grid-template-columns:minmax(0,1fr)}.products-main{padding:20px}}
@media(max-width:480px){.products-main{padding:16px}.product-card{padding:16px;gap:12px}h1{font-size:22px}h2{font-size:18px}button{padding:8px 12px}}
</style>
