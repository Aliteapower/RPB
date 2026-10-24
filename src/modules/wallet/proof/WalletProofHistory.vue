<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useWalletRecord} from '../useWalletRecord'
import type {WalletProof} from './types'
const props=defineProps<{store:string;sourceType:string;sourceId:string}>(),{t}=useI18n()
const path=computed(()=>`/collection-proofs/${props.sourceType}/${encodeURIComponent(props.sourceId)}`)
const {record,error}=useWalletRecord<WalletProof[]>(computed(()=>props.store),path)
</script>
<template><section v-if="record?.length||error" class="wallet-card"><h3>{{t('wallet.proof.history')}}</h3><p v-if="error" class="wallet-error" role="alert">{{error}}</p><article v-for="item in record??[]" :key="item.id"><dl class="wallet-detail-fields"><dt>{{t('wallet.proof.systemReference')}}</dt><dd>{{item.context.systemReference}}</dd><dt>{{t('wallet.proof.bankReference')}}</dt><dd>{{item.confirmation.bankReference}}</dd><dt>{{t('wallet.proof.actualAccount')}}</dt><dd>{{item.confirmation.actualAccount}}</dd><dt>{{t('wallet.note')}}</dt><dd>{{item.confirmation.note||'—'}}</dd><template v-for="(result,field) in item.checks" :key="field"><dt>{{t('wallet.proof.'+field)}}</dt><dd>{{t('wallet.proof.'+result)}}</dd></template></dl><details><summary>{{t('wallet.proof.scan')}}</summary><pre>{{item.extracted.rawText}}</pre><p>{{t('wallet.proof.bankReference')}}: {{item.bankReference||'—'}}</p></details><a :href="item.imageUrl" target="_blank" rel="noopener noreferrer">{{t('wallet.proof.view')}}</a></article></section></template>
<style scoped>pre{white-space:pre-wrap;overflow-wrap:anywhere}dd{overflow-wrap:anywhere}</style>
