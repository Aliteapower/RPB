<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  kicker: string
  displayName: string
  logoMediaUrl: string
  fallbackMark: string
  logoAlt: string
}>()

const logoFailed = ref(false)

watch(() => props.logoMediaUrl, () => {
  logoFailed.value = false
})

function handleLogoError(): void {
  logoFailed.value = true
}
</script>

<template>
  <div class="brand-block">
    <span v-if="!logoMediaUrl || logoFailed" class="brand-mark" aria-hidden="true">
      {{ fallbackMark }}
    </span>
    <span v-else class="brand-mark brand-mark--logo">
      <img :src="logoMediaUrl" :alt="logoAlt" @error="handleLogoError" />
    </span>
    <div class="brand-copy">
      <p class="brand-kicker">{{ kicker }}</p>
      <h1 :title="displayName">{{ displayName }}</h1>
    </div>
  </div>
</template>

<style scoped>
.brand-block {
  align-items: center;
  display: flex;
  gap: 10px;
  min-width: 0;
}

.brand-mark {
  align-items: center;
  background: #fff7ed;
  border-radius: 999px;
  color: #f97316;
  display: inline-flex;
  flex: 0 0 auto;
  font-size: 0.92rem;
  font-weight: 900;
  height: 30px;
  justify-content: center;
  overflow: hidden;
  width: 30px;
}

.brand-mark--logo {
  background: #ffffff;
  border: 1px solid #fed7aa;
}

.brand-mark img {
  height: 100%;
  object-fit: contain;
  width: 100%;
}

.brand-copy {
  min-width: 0;
}

.brand-kicker,
h1 {
  margin: 0;
}

.brand-kicker {
  color: #64748b;
  font-size: 0.72rem;
  font-weight: 800;
}

h1 {
  color: #0f172a;
  font-size: 1.08rem;
  letter-spacing: 0;
  line-height: 1.18;
  max-width: min(42vw, 420px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 420px) {
  h1 {
    max-width: 31vw;
  }
}
</style>
