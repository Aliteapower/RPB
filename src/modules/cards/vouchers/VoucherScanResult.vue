<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {voucherBenefit, voucherRedemptionTime, voucherValue, type ScanResult} from './api'
import VoucherDialog from './VoucherDialog.vue'

defineProps<{voucher: ScanResult | null; error: string; closeLabel: string}>()
const emit = defineEmits<{close: []}>()
const {locale} = useI18n()
const english = computed(() => locale.value.startsWith('en'))
const labels = computed(() => english.value ? {
  redeemed:'Voucher redeemed', used:'Already redeemed', expired:'Expired', too_early:'Not yet valid',
  cash:'Discount', product:'Product', reference:'Registration number', batch:'Batch number', first:'First redeemed at',
} : {
  redeemed:'核销成功', used:'已使用', expired:'已过期', too_early:'未到使用日期',
  cash:'抵扣金额', product:'兑换商品', reference:'登记号', batch:'批次号', first:'首次核销时间',
})
</script>

<template>
  <VoucherDialog v-if="voucher" :title="labels[voucher.status]" :tone="voucher.status==='redeemed'?'success':'notice'" :close-label="closeLabel" @close="emit('close')">
    <div class="voucher-scan-benefit">
      <span>{{labels[voucher.face.kind]}}</span>
      <strong>{{english && voucher.face.kind==='cash' ? voucherValue(voucher.face) : voucherBenefit(voucher.face)}}</strong>
      <p>{{voucher.face.name}}</p>
      <p v-if="voucher.face.specification">{{voucher.face.specification}}</p>
    </div>
    <dl v-if="voucher.redeemedAt" class="voucher-scan-record">
      <div v-if="voucher.issueTotal != null && voucher.redemptionNumber != null">
        <dt>{{labels.reference}}</dt>
        <dd><strong>{{voucher.batchCode + voucher.issueTotal + '.' + voucher.redemptionNumber}}</strong></dd>
      </div>
      <div>
        <dt>{{labels.batch}}</dt>
        <dd>
          <strong>{{voucher.batchCode}}</strong>
          <span v-if="voucher.issueTotal != null && voucher.redemptionNumber != null">
            {{english
              ? `(${voucher.issueTotal} issued · Redemption #${voucher.redemptionNumber})`
              : `（本次发行共 ${voucher.issueTotal} 张 | 第 ${voucher.redemptionNumber} 张核销）`}}
          </span>
        </dd>
      </div>
      <div v-if="voucher.status==='used'">
        <dt>{{labels.first}}</dt>
        <dd>{{voucherRedemptionTime(voucher.redeemedAt, voucher.face.timezone)}} <small>（{{voucher.face.timezone}}）</small></dd>
      </div>
    </dl>
  </VoucherDialog>
  <VoucherDialog v-else :title="error" tone="error" :close-label="closeLabel" @close="emit('close')" />
</template>

<style scoped>
.voucher-scan-benefit{display:grid;gap:5px;padding:14px 16px;border-radius:10px;background:#eef5fa;overflow-wrap:anywhere}
.voucher-scan-benefit>span,.voucher-scan-record dt{color:#4b6173;font-size:13px;font-weight:600}
.voucher-scan-benefit>strong{color:#173d58;font-size:26px;line-height:1.25;font-weight:750}
.voucher-scan-benefit p{margin:0;color:#43586a;font-size:14px}
.voucher-scan-record{display:grid;gap:10px;margin:14px 0 0}
.voucher-scan-record>div{display:grid;gap:4px;padding-bottom:8px;border-bottom:1px solid #e5ecf1}
.voucher-scan-record dd{margin:0;color:#253e50;font-size:14px;font-variant-numeric:tabular-nums}
.voucher-scan-record dd strong{font-size:18px}
.voucher-scan-record dd span,.voucher-scan-record small{display:block;color:#4b6173;font-size:13px}
</style>
