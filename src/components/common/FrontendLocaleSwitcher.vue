<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { setFrontendLocale, SUPPORTED_FRONTEND_LOCALES, type FrontendLocale } from '../../i18n'

const { locale, t } = useI18n({ useScope: 'global' })

const localeOptions: Array<{ value: FrontendLocale, labelKey: string }> = [
  { value: 'zh-CN', labelKey: 'common.localeSwitcher.zhCN' },
  { value: 'en-SG', labelKey: 'common.localeSwitcher.enSG' }
]

const activeLocale = computed(() => locale.value as FrontendLocale)

function switchLocale(nextLocale: FrontendLocale): void {
  if (SUPPORTED_FRONTEND_LOCALES.includes(nextLocale) && nextLocale !== activeLocale.value) {
    setFrontendLocale(nextLocale)
  }
}
</script>

<template>
  <div class="frontend-locale-switcher" :aria-label="t('common.localeSwitcher.aria')" role="group">
    <button
      v-for="option in localeOptions"
      :key="option.value"
      type="button"
      class="frontend-locale-switcher__button"
      :class="{ 'frontend-locale-switcher__button--active': option.value === activeLocale }"
      :aria-pressed="option.value === activeLocale"
      @click="switchLocale(option.value)"
    >
      {{ t(option.labelKey) }}
    </button>
  </div>
</template>

<style scoped>
.frontend-locale-switcher {
  position: fixed;
  top: max(10px, env(safe-area-inset-top));
  right: max(10px, env(safe-area-inset-right));
  z-index: 2000;
  display: grid;
  grid-template-columns: repeat(2, minmax(42px, 1fr));
  gap: 3px;
  padding: 3px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(148, 163, 184, 0.42);
  border-radius: 8px;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(12px);
}

.frontend-locale-switcher__button {
  min-width: 42px;
  height: 30px;
  padding: 0 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.frontend-locale-switcher__button--active {
  background: #0f766e;
  color: #ffffff;
  box-shadow: 0 4px 10px rgba(15, 118, 110, 0.22);
}

.frontend-locale-switcher__button:focus-visible {
  outline: 3px solid rgba(14, 165, 233, 0.34);
  outline-offset: 2px;
}

@media (max-width: 520px) {
  .frontend-locale-switcher {
    top: auto;
    right: max(8px, env(safe-area-inset-right));
    bottom: max(86px, calc(78px + env(safe-area-inset-bottom)));
    grid-template-columns: repeat(2, minmax(40px, 1fr));
  }

  .frontend-locale-switcher__button {
    min-width: 40px;
    padding: 0 8px;
  }
}

@media print {
  .frontend-locale-switcher {
    display: none;
  }
}
</style>
