<script setup lang="ts">
defineProps<{failure?:string;notice?:string;loading?:boolean}>()
import {useWorkforceText} from '../text'
const {t}=useWorkforceText()
</script>
<template><p v-if="failure" role="alert" class="wf-error">{{failure}}</p><p v-if="notice" role="status" class="wf-success">{{notice}}</p><p v-if="loading" role="status">{{t('loading')}}</p></template>
