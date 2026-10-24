<script setup lang="ts">
import FrontendLocaleSwitcher from './components/common/FrontendLocaleSwitcher.vue'
import { routeViewKey } from './router/viewKey'
</script>

<template>
  <FrontendLocaleSwitcher />
  <RouterView :key="routeViewKey($route)" />
</template>

<style>
:root {
  color: #1d2736;
  background: #f4f7fb;
  font-family:
    Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI",
    sans-serif;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

* {
  box-sizing: border-box;
}

html,
body,
#app {
  min-height: 100%;
  margin: 0;
}

body {
  min-width: 320px;
}

button,
input,
textarea {
  font: inherit;
}

button {
  cursor: pointer;
}
</style>
