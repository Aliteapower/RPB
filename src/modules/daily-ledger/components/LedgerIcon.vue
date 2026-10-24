<script setup lang="ts">
defineProps<{ name: 'book' | 'chart' | 'settings' | 'wallet' | 'check' | 'lock' }>()
const paths = {
  book: 'M5 3h13v18H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm0 14h13M8 7h6M8 11h4',
  chart: 'M4 3v17h17M8 15v-4M13 15V6M18 15V9',
  settings: 'm9 3 1-2h4l1 2 3 2 2 1v4l-2 1-1 3-2 2h-4l-1-2-3-1-2-2V7l2-1 2-3ZM14 9a3 3 0 1 0 0 .1',
  wallet: 'M3 6h17v14H3V6Zm0 0 14-3v3M15 11h6v5h-6v-5Z',
  check: 'm5 12 4 4L19 6',
  lock: 'M6 10h12v11H6V10Zm3 0V6a3 3 0 0 1 6 0v4',
}
</script>
<template><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name]" /></svg></template>
