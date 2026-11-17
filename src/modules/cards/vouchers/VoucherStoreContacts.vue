<script setup lang="ts">
import type {VoucherPresentation} from './api'
defineProps<{contacts:VoucherPresentation['contacts']}>()
</script>
<template>
  <div v-if="contacts.length" class="voucher-contacts">
    <section v-for="store in contacts" :key="store.id">
      <h3>{{store.name}}</h3>
      <dl>
        <div v-if="store.address"><dt>地址</dt><dd>{{store.address}}</dd></div>
        <div v-if="store.phone"><dt>电话</dt><dd><a :href="'tel:'+store.phone.replace(/[^+0-9]/g,'')">{{store.phone}}</a></dd></div>
      </dl>
    </section>
  </div>
</template>
<style scoped>
.voucher-contacts{display:grid;gap:12px}
.voucher-contacts h3{font-size:14px;margin:0 0 6px;color:#263e4e}
.voucher-contacts dl{display:grid;gap:4px;margin:0}
.voucher-contacts dl>div{display:grid;grid-template-columns:36px minmax(0,1fr);gap:8px}
.voucher-contacts dt{font-size:13px;color:#506575}
.voucher-contacts dd{margin:0;font-size:14px;line-height:1.6;overflow-wrap:anywhere;white-space:pre-wrap}
.voucher-contacts a{color:inherit}
</style>
