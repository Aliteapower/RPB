<script setup lang="ts">
defineSlots<{default(): unknown; navigation?(): unknown; actions(): unknown}>()
</script>

<template>
  <header class="wallet-page-header">
    <div class="wallet-page-header-brand"><slot /></div>
    <div class="wallet-page-header-navigation"><slot name="navigation" /></div>
    <div class="wallet-page-header-actions"><slot name="actions" /></div>
  </header>
</template>

<style scoped>
.wallet-page-header{display:flex;align-items:center;gap:16px;padding-bottom:12px;margin-bottom:16px;border-bottom:1px solid var(--wallet-line)}
.wallet-page-header-brand{display:flex;align-items:center;gap:10px;flex:1;min-width:0}
.wallet-page-header-navigation,.wallet-page-header-actions{display:flex;align-items:center;gap:8px;flex-shrink:0}
.wallet-page-header-navigation{font-size:12px;color:var(--wallet-muted)}
.wallet-page-header-navigation:empty{display:none}
.wallet-page-header-navigation :deep(a){flex:none;white-space:nowrap}
.wallet-page-header :deep(.wallet-logo){flex:none;width:36px;height:36px;border-radius:10px}
.wallet-page-header :deep(.wallet-header-title){overflow-wrap:anywhere}
.wallet-page-header :deep(.wallet-header-title>small),.wallet-page-header :deep(.wallet-account-caption>small){display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden}
.wallet-page-header :deep(.wallet-account-trigger){gap:6px}
.wallet-page-header :deep(.wallet-icon-button){width:36px;height:36px;min-height:36px}
.wallet-page-header :deep(.staff-scan-entry){min-height:36px;padding:6px 10px;gap:6px;border-radius:9px;color:var(--wallet-blue)}
.wallet-page-header :deep(.staff-scan-entry svg){width:20px;height:20px}
@media(max-width:600px){
  .wallet-page-header{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;padding-bottom:10px;margin-bottom:12px}
  .wallet-page-header-navigation{gap:6px;font-size:11px}
  .wallet-page-header-navigation :deep(select){max-width:100px}
  .wallet-page-header-actions{grid-column:1/-1;justify-content:flex-end;gap:6px;flex-wrap:wrap}
}
@media(max-width:360px){
  .wallet-page-header-brand{gap:8px}
  .wallet-page-header :deep(.wallet-logo){width:32px;height:32px}
  .wallet-page-header :deep(.wallet-account-title),.wallet-page-header :deep(h1){font-size:17px}
  .wallet-page-header-navigation :deep(select){max-width:88px}
}
</style>
