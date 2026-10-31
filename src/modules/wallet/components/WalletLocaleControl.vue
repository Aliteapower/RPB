<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { setFrontendLocale } from '../../../i18n'
const {locale,t}=useI18n({useScope:'global'})
</script>
<template><div class="wallet-locale-control" role="group" :aria-label="t('common.localeSwitcher.aria')"><button :aria-pressed="locale==='zh-CN'" @click="setFrontendLocale('zh-CN')">中文</button><button :aria-pressed="locale==='en-SG'" @click="setFrontendLocale('en-SG')">EN</button></div></template>
