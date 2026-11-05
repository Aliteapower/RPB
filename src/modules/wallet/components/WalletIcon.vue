<script setup lang="ts">
withDefaults(defineProps<{ name: string; size?: number }>(), { size: 24 })
const paths: Record<string,string> = {
  scan: 'M8 3H3v5M16 3h5v5M3 16v5h5M21 16v5h-5M3 12h18M8 8h8v8H8z',
  qr: 'M3 3h6v6H3zM15 3h6v6h-6zM3 15h6v6H3zM15 15h2v2h-2zM21 15v6h-6M12 3v6M3 12h6M12 12v9',
  wallet: 'M3 6h17v15H3zM3 6V4h14M15 11h6v5h-6z',
  bill: 'M5 3h14v18l-3-2-4 2-4-2-3 2zM8 7h8M8 11h8M8 15h5',
  plus: 'M12 4v16M4 12h16', arrow: 'M9 5l7 7-7 7', back: 'M15 5l-7 7 7 7',
  history: 'M3 10a9 9 0 1 1 2 9M3 3v7h7M12 7v6l4 2',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
  chart: 'M4 20V10M12 20V4M20 20v-8',
  settings: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2',
  check: 'M5 12l4 4L19 6', refresh: 'M20 8a8 8 0 1 0 0 8M20 3v5h-5',
  user: 'M8 7a4 4 0 1 0 8 0 4 4 0 0 0-8 0M4 22v-4a8 8 0 0 1 16 0v4'
}
</script>
<template><svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name] || paths.wallet" /></svg></template>
